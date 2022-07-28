package ui

import csstype.px
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
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
import org.w3c.dom.get
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
import util.runIf
import util.runIfAllNotNull

val ConfigurationEditor = scopedFC<ConfigurationEditorProps> { props, scope ->
    var isProcessing by useState(false)
    val (lyricsConversion, setLyricsConversion) = useState {
        getStateFromLocalStorage<LyricsConversionState>("lyricsConversion")?.let {
            return@useState it
        }

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
        getStateFromLocalStorage<LyricsReplacementState>("lyricsReplacement")?.let {
            return@useState it
        }

        val preset = LyricsReplacementRequest.getPreset(props.project.format, props.outputFormat)
        if (preset == null) {
            LyricsReplacementState(false, LyricsReplacementRequest())
        } else {
            LyricsReplacementState(true, preset)
        }
    }
    val (slightRestsFilling, setSlightRestsFilling) = useState {
        getStateFromLocalStorage<SlightRestsFillingState>("slightRestsFilling")?.let {
            return@useState it
        }
        SlightRestsFillingState(
            true,
            RESTS_FILLING_MAX_LENGTH_DENOMINATOR_DEFAULT
        )
    }
    val (pitchConversion, setPitchConversion) = useState {
        getStateFromLocalStorage<PitchConversionState>("pitchConversion")?.let {
            return@useState it
        }
        val hasPitchData = Feature.ConvertPitch.isAvailable(props.project)
        val isPitchConversionAvailable = hasPitchData &&
            props.outputFormat.availableFeaturesForGeneration.contains(Feature.ConvertPitch)
        PitchConversionState(
            isAvailable = isPitchConversionAvailable,
            isOn = isPitchConversionAvailable
        )
    }
    val (projectZoom, setProjectZoom) = useState {
        getStateFromLocalStorage<ProjectZoomState>("projectZoom")?.let {
            return@useState it
        }
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

private inline fun <reified T> ChildrenBuilder.getStateFromLocalStorage(name: String): T? {
    runCatching {
        window.localStorage[name]?.let {
            return json.decodeFromString(it)
        }
    }.onFailure {
        console.log(it)
    }
    return null
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
                .runIf(lyricsConversion.isOn) {
                    runIfAllNotNull(fromType, toType) { fromType, toType ->
                        convert(copy(lyricsType = fromType), toType, format)
                    }
                }
                .runIf(lyricsReplacement.isOn) {
                    replaceLyrics(lyricsReplacement.request)
                }
                .runIf(slightRestsFilling.isOn) {
                    fillRests(slightRestsFilling.excludedMaxLength)
                }
                .runIf(projectZoom.isOn) {
                    zoom(projectZoom.factorValue)
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
            listOf(
                "lyricsConversion" to lyricsConversion,
                "lyricsReplacement" to lyricsReplacement,
                "slightRestsFilling" to slightRestsFilling,
                "pitchConversion" to pitchConversion,
                "projectZoom" to projectZoom
            ).forEach {
                window.localStorage.setItem(it.first, json.encodeToString(it.second))
            }
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

private val json = Json {
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
        polymorphic(SubState::class, LyricsConversionState::class, LyricsConversionState.serializer())
        polymorphic(SubState::class, LyricsReplacementState::class, LyricsReplacementState.serializer())
        polymorphic(SubState::class, SlightRestsFillingState::class, SlightRestsFillingState.serializer())
        polymorphic(SubState::class, PitchConversionState::class, PitchConversionState.serializer())
        polymorphic(SubState::class, ProjectZoomState::class, ProjectZoomState.serializer())
    }
}

external interface ConfigurationEditorProps : Props {
    var project: Project
    var outputFormat: Format
    var onFinished: (ExportResult, Format) -> Unit
}

@Serializable
data class LyricsConversionState(
    val isOn: Boolean,
    val fromType: LyricsType?,
    val toType: LyricsType?
) : SubState() {
    val isReady: Boolean get() = if (isOn) fromType != null && toType != null else true
}

@Serializable
data class LyricsReplacementState(
    val isOn: Boolean,
    val request: LyricsReplacementRequest
) : SubState() {
    val isReady: Boolean get() = if (isOn) request.isValid else true
}

@Serializable
data class SlightRestsFillingState(
    val isOn: Boolean,
    val excludedMaxLengthDenominator: Int
) : SubState() {

    val excludedMaxLength: Long
        get() = (TICKS_IN_FULL_NOTE / excludedMaxLengthDenominator).toLong()
}

@Serializable
data class PitchConversionState(
    val isAvailable: Boolean,
    val isOn: Boolean
) : SubState()

@Serializable
data class ProjectZoomState(
    val isOn: Boolean,
    val factor: String,
    val hasWarning: Boolean
) : SubState() {
    val factorValue: Double
        get() = factor.evalFractionOrNull()!!
}
