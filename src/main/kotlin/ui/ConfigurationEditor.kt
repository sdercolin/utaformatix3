package ui

import csstype.px
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.ExportResult
import model.Feature
import model.Format
import model.LyricsType
import model.LyricsType.Unknown
import model.Project
import model.TICKS_IN_FULL_NOTE
import mui.material.Button
import mui.material.ButtonColor
import mui.material.ButtonVariant
import process.RESTS_FILLING_MAX_LENGTH_DENOMINATOR_DEFAULT
import process.evalFractionOrNull
import process.fillRests
import process.lyrics.LyricsReplacementRequest
import process.lyrics.convert
import process.lyrics.replaceLyrics
import process.projectZoomFactorOptions
import process.zoom
import react.ChildrenBuilder
import react.Props
import react.css.css
import react.dom.html.ReactHTML.div
import react.useState
import ui.common.DialogErrorState
import ui.common.SubState
import ui.common.errorDialog
import ui.common.progress
import ui.common.scopedFC
import ui.common.title
import ui.configuration.LyricsConversionBlock
import ui.configuration.LyricsReplacementBlock
import ui.configuration.PitchConversionBlock
import ui.configuration.ProjectZoomBlock
import ui.configuration.SlightRestsFillingBlock
import ui.strings.Strings
import ui.strings.string
import util.runCatchingCancellable

val ConfigurationEditor = scopedFC<ConfigurationEditorProps> { props, scope ->
    var isProcessing by useState(false)
    val (lyricsConversion, setLyricsConversion) = useState {
        val analysedType = props.project.lyricsType
        val doLyricsConversion = analysedType != Unknown
        val fromLyricsType: LyricsType?
        val toLyricsType: LyricsType?

        if (doLyricsConversion) {
            fromLyricsType = analysedType
            toLyricsType = analysedType.findBestConversionTargetIn(props.outputFormat)
        } else {
            fromLyricsType = null
            toLyricsType = null
        }
        LyricsConversionState(
            doLyricsConversion,
            fromLyricsType,
            toLyricsType
        )
    }
    val (lyricsReplacement, setLyricsReplacement) = useState {
        val preset = LyricsReplacementRequest.getPreset(props.outputFormat)
        if (preset == null) {
            LyricsReplacementState(false, LyricsReplacementRequest())
        } else {
            LyricsReplacementState(true, preset)
        }
    }
    val (slightRestsFilling, setSlightRestsFilling) = useState {
        SlightRestsFillingState(
            true,
            RESTS_FILLING_MAX_LENGTH_DENOMINATOR_DEFAULT
        )
    }
    val (pitchConversion, setPitchConversion) = useState {
        val hasPitchData = Feature.ConvertPitch.isAvailable(props.project)
        val isPitchConversionAvailable = hasPitchData &&
            props.outputFormat.availableFeaturesForGeneration.contains(Feature.ConvertPitch)
        PitchConversionState(
            isAvailable = isPitchConversionAvailable,
            isOn = isPitchConversionAvailable
        )
    }
    val (projectZoom, setProjectZoom) = useState {
        ProjectZoomState(
            isOn = false,
            factor = projectZoomFactorOptions.first(),
            hasWarning = false
        )
    }
    var dialogError by useState(DialogErrorState())

    fun isReady() = lyricsConversion.isReady && lyricsReplacement.isReady

    fun closeErrorDialog() {
        dialogError = dialogError.copy(isShowing = false)
    }

    title(Strings.ConfigurationEditorCaption)
    LyricsConversionBlock {
        this.project = props.project
        this.outputFormat = props.outputFormat
        initialState = lyricsConversion
        submitState = setLyricsConversion
    }
    LyricsReplacementBlock {
        initialState = lyricsReplacement
        submitState = setLyricsReplacement
    }
    SlightRestsFillingBlock {
        initialState = slightRestsFilling
        submitState = setSlightRestsFilling
    }
    if (pitchConversion.isAvailable) PitchConversionBlock {
        initialState = pitchConversion
        submitState = setPitchConversion
    }
    ProjectZoomBlock {
        this.project = props.project
        initialState = projectZoom
        submitState = setProjectZoom
    }
    buildNextButton(
        scope,
        props,
        isEnabled = isReady(),
        lyricsConversion,
        lyricsReplacement,
        slightRestsFilling,
        pitchConversion,
        projectZoom,
        setProcessing = { isProcessing = it },
        onDialogError = { dialogError = it }
    )

    errorDialog(
        isShowing = dialogError.isShowing,
        title = dialogError.title,
        errorMessage = dialogError.message,
        close = { closeErrorDialog() }
    )

    progress(isShowing = isProcessing)
}

private fun ChildrenBuilder.buildNextButton(
    scope: CoroutineScope,
    props: ConfigurationEditorProps,
    isEnabled: Boolean,
    lyricsConversion: LyricsConversionState,
    lyricsReplacement: LyricsReplacementState,
    slightRestsFilling: SlightRestsFillingState,
    pitchConversion: PitchConversionState,
    projectZoom: ProjectZoomState,
    setProcessing: (Boolean) -> Unit,
    onDialogError: (DialogErrorState) -> Unit
) {
    div {
        css {
            marginTop = 48.px
        }
        Button {
            color = ButtonColor.primary
            variant = ButtonVariant.contained
            disabled = !isEnabled
            onClick = {
                process(
                    scope,
                    props,
                    lyricsConversion,
                    lyricsReplacement,
                    slightRestsFilling,
                    pitchConversion,
                    projectZoom,
                    setProcessing,
                    onDialogError
                )
            }
            +string(Strings.NextButton)
        }
    }
}

private fun process(
    scope: CoroutineScope,
    props: ConfigurationEditorProps,
    lyricsConversion: LyricsConversionState,
    lyricsReplacement: LyricsReplacementState,
    slightRestsFilling: SlightRestsFillingState,
    pitchConversion: PitchConversionState,
    projectZoom: ProjectZoomState,
    setProcessing: (Boolean) -> Unit,
    onDialogError: (DialogErrorState) -> Unit
) {
    setProcessing(true)
    scope.launch {
        runCatchingCancellable {
            val format = props.outputFormat
            val fromType = lyricsConversion.fromType
            val toType = lyricsConversion.toType

            val project = props.project
                .let {
                    if (lyricsConversion.isOn && fromType != null && toType != null) {
                        convert(it.copy(lyricsType = fromType), toType, format)
                    } else it
                }
                .let {
                    if (lyricsReplacement.isOn) {
                        it.replaceLyrics(lyricsReplacement.request)
                    } else it
                }
                .let {
                    if (slightRestsFilling.isOn) {
                        it.copy(
                            tracks = it.tracks.map { track ->
                                track.fillRests(slightRestsFilling.excludedMaxLength)
                            }
                        )
                    } else it
                }
                .let {
                    if (projectZoom.isOn) {
                        it.zoom(projectZoom.factorValue)
                    } else it
                }

            val availableFeatures = Feature.values()
                .filter { it.isAvailable.invoke(project) && format.availableFeaturesForGeneration.contains(it) }
                .filter {
                    when (it) {
                        Feature.ConvertPitch -> pitchConversion.isOn
                    }
                }

            delay(100)
            val result = format.generator.invoke(project, availableFeatures)
            console.log(result.blob)
            props.onFinished.invoke(result, format)
        }.onFailure { t ->
            console.log(t)
            setProcessing(false)
            onDialogError(
                DialogErrorState(
                    isShowing = true,
                    title = string(Strings.ProcessErrorDialogTitle),
                    message = t.stackTraceToString()
                )
            )
        }
    }
}

external interface ConfigurationEditorProps : Props {
    var project: Project
    var outputFormat: Format
    var onFinished: (ExportResult, Format) -> Unit
}

data class LyricsConversionState(
    val isOn: Boolean,
    val fromType: LyricsType?,
    val toType: LyricsType?
) : SubState {
    val isReady: Boolean = if (isOn) fromType != null && toType != null else true
}

data class LyricsReplacementState(
    val isOn: Boolean,
    val request: LyricsReplacementRequest
) : SubState {
    val isReady: Boolean = if (isOn) request.isValid else true
}

data class SlightRestsFillingState(
    val isOn: Boolean,
    val excludedMaxLengthDenominator: Int
) : SubState {

    val excludedMaxLength: Long
        get() = (TICKS_IN_FULL_NOTE / excludedMaxLengthDenominator).toLong()
}

data class PitchConversionState(
    val isAvailable: Boolean,
    val isOn: Boolean
) : SubState

data class ProjectZoomState(
    val isOn: Boolean,
    val factor: String,
    val hasWarning: Boolean
) : SubState {
    val factorValue: Double
        get() = factor.evalFractionOrNull()!!
}
