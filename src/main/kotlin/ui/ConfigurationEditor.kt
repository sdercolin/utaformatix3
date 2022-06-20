package ui

import csstype.Length
import csstype.Margin
import csstype.VerticalAlign
import csstype.em
import csstype.px
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.js.jso
import model.ExportResult
import model.Feature
import model.Format
import model.LyricsType
import model.LyricsType.KanaCv
import model.LyricsType.KanaVcv
import model.LyricsType.RomajiCv
import model.LyricsType.RomajiVcv
import model.LyricsType.Unknown
import model.Project
import model.TICKS_IN_FULL_NOTE
import mui.icons.material.ErrorOutline
import mui.icons.material.HelpOutline
import mui.material.Button
import mui.material.ButtonColor
import mui.material.ButtonVariant
import mui.material.FormControl
import mui.material.FormControlLabel
import mui.material.FormControlMargin
import mui.material.FormGroup
import mui.material.FormLabel
import mui.material.InputLabel
import mui.material.LabelPlacement
import mui.material.MenuItem
import mui.material.Paper
import mui.material.Radio
import mui.material.RadioGroup
import mui.material.Select
import mui.material.Switch
import mui.material.SwitchColor
import mui.material.Tooltip
import mui.material.TooltipPlacement
import mui.material.Typography
import mui.material.styles.TypographyVariant
import process.RESTS_FILLING_MAX_LENGTH_DENOMINATOR_DEFAULT
import process.fillRests
import process.lyrics.convert
import process.restsFillingMaxLengthDenominatorOptions
import react.ChildrenBuilder
import react.FC
import react.Props
import react.ReactNode
import react.create
import react.css.css
import react.dom.html.ReactHTML.div
import react.useState
import ui.strings.Strings
import ui.strings.string

val ConfigurationEditor = FC<ConfigurationEditorProps> { props ->
    var isProcessing by useState(false)
    var lyricsConversion: LyricsConversionState by useState {
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
    var slightRestsFilling: SlightRestsFillingState by useState {
        SlightRestsFillingState(
            true,
            RESTS_FILLING_MAX_LENGTH_DENOMINATOR_DEFAULT
        )
    }
    var pitchConversion: PitchConversionState by useState {
        val hasPitchData = Feature.ConvertPitch.isAvailable(props.project)
        val isPitchConversionAvailable = hasPitchData &&
                props.outputFormat.availableFeaturesForGeneration.contains(Feature.ConvertPitch)
        PitchConversionState(
            isAvailable = isPitchConversionAvailable,
            isOn = isPitchConversionAvailable
        )
    }
    var dialogError by useState(DialogErrorState())

    fun closeErrorDialog() {
        dialogError = dialogError.copy(isShowing = false)
    }

    title(Strings.ConfigurationEditorCaption)
    buildLyricsBlock(props, lyricsConversion) { lyricsConversion = it }
    buildRestsFillingBlock(slightRestsFilling) { slightRestsFilling = it }
    if (pitchConversion.isAvailable) buildPitchConversion(pitchConversion) { pitchConversion = it }
    buildNextButton(
        props,
        isEnabled = lyricsConversion.isReady,
        lyricsConversion,
        slightRestsFilling,
        pitchConversion,
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

private fun ChildrenBuilder.buildLyricsBlock(
    props: ConfigurationEditorProps,
    lyricsConversion: LyricsConversionState,
    onChangeLyricsConversion: (LyricsConversionState) -> Unit
) {
    FormGroup {
        FormControlLabel {
            label = ReactNode(string(Strings.JapaneseLyricsConversionSwitchLabel))
            control = Switch.create {
                color = SwitchColor.secondary
                checked = lyricsConversion.isOn
                onChange = { event, _ ->
                    val checked = event.target.checked
                    onChangeLyricsConversion(lyricsConversion.copy(isOn = checked))
                }
            }
            labelPlacement = LabelPlacement.end
        }
    }

    if (lyricsConversion.isOn) buildLyricsDetail(props, lyricsConversion, onChangeLyricsConversion)
}

private fun ChildrenBuilder.buildLyricsDetail(
    props: ConfigurationEditorProps,
    lyricsConversion: LyricsConversionState,
    onChangeLyricsConversion: (LyricsConversionState) -> Unit
) {
    div {
        css {
            Margin(horizontal = 40.px, vertical = 0.px)
            width = Length.maxContent
        }
        Paper {
            elevation = 0
            div {
                css {
                    Margin(
                        horizontal = 24.px,
                        top = 16.px,
                        bottom = 24.px
                    )
                }
                FormGroup {
                    buildLyricsTypeControl(
                        lyricsConversion,
                        onChangeLyricsConversion,
                        labelText = string(
                            Strings.FromLyricsTypeLabel,
                            "type" to props.project.lyricsType.displayName
                        ),
                        displayedTypeSelector = { it.fromType },
                        lyricsConversionUpdater = { current, value ->
                            current.copy(fromType = LyricsType.valueOf(value))
                        },
                        lyricTypeOptions = listOf(RomajiCv, RomajiVcv, KanaCv, KanaVcv)
                    )

                    buildLyricsTypeControl(
                        lyricsConversion,
                        onChangeLyricsConversion,
                        labelText = string(Strings.ToLyricsTypeLabel),
                        displayedTypeSelector = { it.toType },
                        lyricsConversionUpdater = { current, value ->
                            current.copy(toType = LyricsType.valueOf(value))
                        },
                        lyricTypeOptions = props.outputFormat.possibleLyricsTypes
                    )
                }
            }
        }
    }
}

private fun ChildrenBuilder.buildLyricsTypeControl(
    lyricsConversion: LyricsConversionState,
    onChangeLyricsConversion: (LyricsConversionState) -> Unit,
    labelText: String,
    displayedTypeSelector: (LyricsConversionState) -> LyricsType?,
    lyricsConversionUpdater: (LyricsConversionState, String) -> LyricsConversionState,
    lyricTypeOptions: List<LyricsType>
) {
    FormControl {
        margin = FormControlMargin.normal
        FormLabel {
            focused = false
            +labelText
        }
        RadioGroup {
            row = true
            value = displayedTypeSelector(lyricsConversion)?.name.orEmpty()
            onChange = { event, _ ->
                val value = event.target.value
                onChangeLyricsConversion(lyricsConversionUpdater(lyricsConversion, value))
            }
            lyricTypeOptions.forEach { lyricsType ->
                FormControlLabel {
                    value = lyricsType.name
                    control = Radio.create()
                    label = Typography.create {
                        variant = TypographyVariant.subtitle2
                        +lyricsType.displayName
                    }
                }
            }
        }
    }
}

fun ChildrenBuilder.buildRestsFillingBlock(
    slightRestsFilling: SlightRestsFillingState,
    onChangeSlightRestsFilling: (SlightRestsFillingState) -> Unit
) {
    FormGroup {
        div {
            FormControlLabel {
                label = ReactNode(string(Strings.SlightRestsFillingSwitchLabel))
                control = Switch.create {
                    color = SwitchColor.secondary
                    checked = slightRestsFilling.isOn
                    onChange = { event, _ ->
                        val checked = event.target.checked
                        onChangeSlightRestsFilling(slightRestsFilling.copy(isOn = checked))
                    }
                }
                labelPlacement = LabelPlacement.end
            }
            Tooltip {
                title = ReactNode(string(Strings.SlightRestsFillingDescription))
                placement = TooltipPlacement.right
                disableInteractive = false
                HelpOutline {
                    style = jso {
                        verticalAlign = VerticalAlign.middle
                    }
                }
            }
        }
    }

    if (slightRestsFilling.isOn) buildRestsFillingDetail(slightRestsFilling, onChangeSlightRestsFilling)
}

private fun ChildrenBuilder.buildRestsFillingDetail(
    slightRestsFilling: SlightRestsFillingState,
    onChangeSlightRestsFilling: (SlightRestsFillingState) -> Unit
) {
    div {
        css {
            Margin(horizontal = 40.px, vertical = 0.px)
            width = Length.maxContent
        }
        Paper {
            elevation = 0
            div {
                css {
                    Margin(
                        horizontal = 24.px,
                        vertical = 16.px
                    )
                    paddingBottom = 8.px
                    minWidth = 20.em
                }
                FormControl {
                    margin = FormControlMargin.normal
                    focused = false
                    InputLabel {
                        style = jso { width = Length.maxContent }
                        id = SlightRestsFillingLabelId
                        focused = false
                        +string(Strings.SlightRestsFillingThresholdLabel)
                    }
                    Select {
                        labelId = SlightRestsFillingLabelId
                        value = slightRestsFilling.excludedMaxLengthDenominator.toString().unsafeCast<Nothing?>()
                        onChange = { event, _ ->
                            val value = event.target.value
                            onChangeSlightRestsFilling(
                                slightRestsFilling.copy(
                                    excludedMaxLengthDenominator = value.toInt()
                                )
                            )
                        }
                        restsFillingMaxLengthDenominatorOptions.forEach { denominator ->
                            MenuItem {
                                value = denominator.toString()
                                +string(
                                    Strings.SlightRestsFillingThresholdItem,
                                    "denominator" to denominator.toString()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ChildrenBuilder.buildPitchConversion(
    pitchConversion: PitchConversionState,
    onChangePitchConversion: (PitchConversionState) -> Unit
) {
    FormGroup {
        div {
            FormControlLabel {
                label = ReactNode(string(Strings.ConvertPitchData))
                control = Switch.create {
                    color = SwitchColor.secondary
                    checked = pitchConversion.isOn
                    onChange = { event, _ ->
                        val checked = event.target.checked
                        onChangePitchConversion(pitchConversion.copy(isOn = checked))
                    }
                }
                labelPlacement = LabelPlacement.end
            }
            Tooltip {
                title = ReactNode(string(Strings.ConvertPitchDataDescription))
                placement = TooltipPlacement.right
                disableInteractive = false
                ErrorOutline {
                    style = jso {
                        verticalAlign = VerticalAlign.middle
                    }
                }
            }
        }
    }
}

private fun ChildrenBuilder.buildNextButton(
    props: ConfigurationEditorProps,
    isEnabled: Boolean,
    lyricsConversion: LyricsConversionState,
    slightRestsFilling: SlightRestsFillingState,
    pitchConversion: PitchConversionState,
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
                process(props, lyricsConversion, slightRestsFilling, pitchConversion, setProcessing, onDialogError)
            }
            +string(Strings.NextButton)
        }
    }
}

private fun process(
    props: ConfigurationEditorProps,
    lyricsConversion: LyricsConversionState,
    slightRestsFilling: SlightRestsFillingState,
    pitchConversion: PitchConversionState,
    setProcessing: (Boolean) -> Unit,
    onDialogError: (DialogErrorState) -> Unit
) {
    setProcessing(true)
    GlobalScope.launch {
        try {
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
                    if (slightRestsFilling.isOn) {
                        it.copy(
                            tracks = it.tracks.map { track ->
                                track.fillRests(slightRestsFilling.excludedMaxLength)
                            }
                        )
                    } else it
                }

            delay(100)
            val availableFeatures = Feature.values()
                .filter {
                    it.isAvailable.invoke(project) &&
                            format.availableFeaturesForGeneration.contains(it)
                }
                .filter {
                    when (it) {
                        Feature.ConvertPitch -> pitchConversion.isOn
                    }
                }

            val result = format.generator.invoke(project, availableFeatures)
            console.log(result.blob)
            props.onFinished.invoke(result, format)
        } catch (t: Throwable) {
            console.log(t)
            setProcessing(false)
            onDialogError(
                DialogErrorState(
                    isShowing = true,
                    title = string(Strings.ProcessErrorDialogTitle),
                    message = t.message ?: t.toString()
                )
            )
        }
    }
}

private const val SlightRestsFillingLabelId = "slight-rests-filling"

external interface ConfigurationEditorProps : Props {
    var project: Project
    var outputFormat: Format
    var onFinished: (ExportResult, Format) -> Unit
}

data class LyricsConversionState(
    val isOn: Boolean,
    val fromType: LyricsType?,
    val toType: LyricsType?
) {
    val isReady: Boolean =
        if (isOn) fromType != null && toType != null
        else true
}

data class SlightRestsFillingState(
    val isOn: Boolean,
    val excludedMaxLengthDenominator: Int
) {

    val excludedMaxLength: Long
        get() = (TICKS_IN_FULL_NOTE / excludedMaxLengthDenominator).toLong()
}

data class PitchConversionState(
    val isAvailable: Boolean,
    val isOn: Boolean
)
