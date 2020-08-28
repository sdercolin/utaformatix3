package ui

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.css.LinearDimension
import kotlinx.css.VerticalAlign
import kotlinx.css.margin
import kotlinx.css.marginTop
import kotlinx.css.minWidth
import kotlinx.css.paddingBottom
import kotlinx.css.width
import model.ExportResult
import model.Format
import model.LyricsType
import model.LyricsType.KANA_CV
import model.LyricsType.KANA_VCV
import model.LyricsType.ROMAJI_CV
import model.LyricsType.ROMAJI_VCV
import model.LyricsType.UNKNOWN
import model.Project
import model.TICKS_IN_FULL_NOTE
import org.w3c.dom.HTMLInputElement
import process.RESTS_FILLING_MAX_LENGTH_DENOMINATOR_DEFAULT
import process.fillRests
import process.lyrics.convert
import process.restsFillingMaxLengthDenominatorOptions
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.dom.div
import react.setState
import styled.css
import styled.styledDiv
import ui.external.materialui.ButtonVariant
import ui.external.materialui.Color
import ui.external.materialui.FontSize
import ui.external.materialui.FormControlMargin
import ui.external.materialui.Icons
import ui.external.materialui.LabelPlacement
import ui.external.materialui.Style
import ui.external.materialui.TypographyVariant
import ui.external.materialui.button
import ui.external.materialui.formControl
import ui.external.materialui.formControlLabel
import ui.external.materialui.formGroup
import ui.external.materialui.formLabel
import ui.external.materialui.inputLabel
import ui.external.materialui.menuItem
import ui.external.materialui.paper
import ui.external.materialui.radio
import ui.external.materialui.radioGroup
import ui.external.materialui.select
import ui.external.materialui.switch
import ui.external.materialui.tooltip
import ui.external.materialui.typography
import ui.strings.Strings
import ui.strings.Strings.NextButton
import ui.strings.Strings.ProcessErrorDialogTitle
import ui.strings.string

class ConfigurationEditor(props: ConfigurationEditorProps) :
    RComponent<ConfigurationEditorProps, ConfigurationEditorState>(props) {

    override fun ConfigurationEditorState.init(props: ConfigurationEditorProps) {
        isProcessing = false
        val analysedType = props.project.lyricsType
        val doLyricsConversion = analysedType != UNKNOWN
        val fromLyricsType: LyricsType?
        val toLyricsType: LyricsType?
        if (doLyricsConversion) {
            fromLyricsType = analysedType
            toLyricsType = analysedType.findBestConversionTargetIn(props.outputFormat)
        } else {
            fromLyricsType = null
            toLyricsType = null
        }
        lyricsConversion = LyricsConversionState(
            doLyricsConversion,
            fromLyricsType,
            toLyricsType
        )
        slightRestsFilling = SlightRestsFillingState(
            true,
            RESTS_FILLING_MAX_LENGTH_DENOMINATOR_DEFAULT
        )
        dialogError = DialogErrorState()
    }

    override fun RBuilder.render() {
        title(Strings.ConfigurationEditorCaption)
        buildLyricsBlock()
        buildRestsFillingBlock()
        buildNextButton()

        errorDialog(
            open = state.dialogError.open,
            title = state.dialogError.title,
            errorMessage = state.dialogError.message,
            onClose = { closeErrorDialog() }
        )

        if (state.isProcessing) {
            progress()
        }
    }

    private fun RBuilder.buildLyricsBlock() {
        formGroup {
            formControlLabel {
                attrs {
                    label = string(Strings.JapaneseLyricsConversionSwitchLabel)
                    control = switch {
                        attrs {
                            checked = state.lyricsConversion.isOn
                            onChange = {
                                val checked = (it.target as HTMLInputElement).checked
                                setState {
                                    lyricsConversion = lyricsConversion.copy(
                                        isOn = checked
                                    )
                                }
                            }
                        }
                    }
                    labelPlacement = LabelPlacement.end
                }
            }
        }

        if (state.lyricsConversion.isOn) {
            buildLyricsDetail()
        }
    }

    private fun RBuilder.buildLyricsDetail() {
        styledDiv {
            css {
                margin(horizontal = LinearDimension("40px"))
                width = LinearDimension.maxContent
            }
            paper {
                attrs {
                    elevation = 0
                }
                styledDiv {
                    css {
                        margin(
                            horizontal = LinearDimension("24px"),
                            top = LinearDimension("16px"),
                            bottom = LinearDimension("24px")
                        )
                    }
                    formGroup {
                        buildFromLyricsTypeControl()
                        buildToLyricsTypeControl()
                    }
                }
            }
        }
    }

    private fun RBuilder.buildFromLyricsTypeControl() {
        formControl {
            attrs {
                margin = FormControlMargin.normal
            }
            formLabel {
                attrs.focused = false
                +(string(Strings.FromLyricsTypeLabel, "type" to props.project.lyricsType.displayName))
            }
            radioGroup {
                attrs {
                    row = true
                    value = state.lyricsConversion.fromType?.name.orEmpty()
                    onChange = {
                        val value = (it.target as HTMLInputElement).value
                        setState {
                            lyricsConversion = lyricsConversion.copy(
                                fromType = LyricsType.valueOf(value)
                            )
                        }
                    }
                }
                listOf(ROMAJI_CV, ROMAJI_VCV, KANA_CV, KANA_VCV).forEach { lyricsType ->
                    formControlLabel {
                        attrs {
                            value = lyricsType.name
                            control = radio {}
                            label = typography {
                                attrs {
                                    variant = TypographyVariant.subtitle2
                                }
                                +lyricsType.displayName
                            }
                        }
                    }
                }

            }
        }
    }

    private fun RBuilder.buildToLyricsTypeControl() {
        formControl {
            attrs {
                margin = FormControlMargin.normal
            }
            formLabel {
                attrs.focused = false
                +(string(Strings.ToLyricsTypeLabel))
            }
            radioGroup {
                attrs {
                    row = true
                    value = state.lyricsConversion.toType?.name.orEmpty()
                    onChange = {
                        val value = (it.target as HTMLInputElement).value
                        setState {
                            lyricsConversion = lyricsConversion.copy(
                                toType = LyricsType.valueOf(value)
                            )
                        }
                    }
                }
                props.outputFormat.possibleLyricsTypes.forEach { lyricsType ->
                    formControlLabel {
                        attrs {
                            value = lyricsType.name
                            control = radio {}
                            label = typography {
                                attrs {
                                    variant = TypographyVariant.subtitle2
                                }
                                +lyricsType.displayName
                            }
                        }
                    }
                }
            }
        }
    }

    private fun RBuilder.buildRestsFillingBlock() {
        formGroup {
            div {
                formControlLabel {
                    attrs {
                        label = string(Strings.SlightRestsFillingSwitchLabel)
                        control = switch {
                            attrs {
                                checked = state.slightRestsFilling.isOn
                                onChange = {
                                    val checked = (it.target as HTMLInputElement).checked
                                    setState {
                                        slightRestsFilling = slightRestsFilling.copy(
                                            isOn = checked
                                        )
                                    }
                                }
                            }
                        }
                        labelPlacement = LabelPlacement.end
                    }
                }
                tooltip {
                    attrs {
                        title = string(Strings.SlightRestsFillingDescription)
                        placement = "right"
                        interactive = true
                    }
                    Icons.help {
                        attrs {
                            style = Style(
                                fontSize = FontSize.initial,
                                verticalAlign = VerticalAlign.middle
                            )
                        }
                    }
                }
            }
        }

        if (state.slightRestsFilling.isOn) {
            buildRestsFillingDetail()
        }
    }

    private fun RBuilder.buildRestsFillingDetail() {
        styledDiv {
            css {
                margin(horizontal = LinearDimension("40px"))
                width = LinearDimension.maxContent
            }
            paper {
                attrs {
                    elevation = 0
                }
                styledDiv {
                    css {
                        margin(
                            horizontal = LinearDimension("24px"),
                            vertical = LinearDimension("16px")
                        )
                        paddingBottom = LinearDimension("8px")
                        minWidth = LinearDimension("20em")
                    }
                    formControl {
                        attrs {
                            margin = FormControlMargin.normal
                            focused = false
                        }
                        inputLabel {
                            attrs {
                                style = Style(width = "max-content")
                                id = slightRestsFillingLabelId
                                focused = false
                            }
                            +(string(Strings.SlightRestsFillingThresholdLabel))
                        }
                        select {
                            attrs {
                                labelId = slightRestsFillingLabelId
                                value = state.slightRestsFilling.excludedMaxLengthDenominator.toString()
                                onChange = { event ->
                                    val value = event.target.asDynamic().value as String
                                    setState {
                                        slightRestsFilling = slightRestsFilling.copy(
                                            excludedMaxLengthDenominator = value.toInt()
                                        )
                                    }
                                }
                            }
                            restsFillingMaxLengthDenominatorOptions.forEach { denominator ->
                                menuItem {
                                    attrs {
                                        value = denominator.toString()
                                    }
                                    +(string(
                                        Strings.SlightRestsFillingThresholdItem,
                                        "denominator" to denominator.toString()
                                    ))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun RBuilder.buildNextButton() {
        styledDiv {
            css {
                marginTop = LinearDimension("48px")
            }
            button {
                attrs {
                    color = Color.primary
                    variant = ButtonVariant.contained
                    disabled = !state.canGoNext
                    onClick = {
                        process()
                    }
                }
                +(string(NextButton))
            }
        }
    }

    private fun process() {
        setState {
            isProcessing = true
        }
        GlobalScope.launch {
            try {
                val lyricsConversionState = state.lyricsConversion
                val fromType = lyricsConversionState.fromType
                val toType = lyricsConversionState.toType

                val slightRestsFillingState = state.slightRestsFilling
                val excludedMaxLength = slightRestsFillingState.excludedMaxLength

                val project = props.project
                    .let {
                        if (lyricsConversionState.isOn && fromType != null && toType != null)
                            convert(it.copy(lyricsType = fromType), toType)
                        else it
                    }
                    .let {
                        if (slightRestsFillingState.isOn && excludedMaxLength != null)
                            it.copy(
                                tracks = it.tracks.map { track -> track.fillRests(excludedMaxLength) }
                            )
                        else it
                    }

                val format = props.outputFormat
                val result = format.generator.invoke(project)
                console.log(result.blob)
                props.onFinished(result, format)
            } catch (t: Throwable) {
                console.log(t)
                setState {
                    isProcessing = false
                    dialogError = DialogErrorState(
                        open = true,
                        title = string(ProcessErrorDialogTitle),
                        message = t.message ?: t.toString()
                    )
                }
            }
        }
    }

    private val ConfigurationEditorState.canGoNext: Boolean
        get() = lyricsConversion.isReady

    private fun closeErrorDialog() {
        setState {
            dialogError = dialogError.copy(open = false)
        }
    }

    private val slightRestsFillingLabelId = "slight-rests-filling"
}

external interface ConfigurationEditorProps : RProps {
    var project: Project
    var outputFormat: Format
    var onFinished: (ExportResult, Format) -> Unit
}

external interface ConfigurationEditorState : RState {
    var isProcessing: Boolean
    var lyricsConversion: LyricsConversionState
    var slightRestsFilling: SlightRestsFillingState
    var dialogError: DialogErrorState
}

data class LyricsConversionState(
    val isOn: Boolean,
    val fromType: LyricsType?,
    val toType: LyricsType?
) {
    val isReady =
        if (isOn) fromType != null && toType != null
        else true
}

data class SlightRestsFillingState(
    val isOn: Boolean,
    val excludedMaxLengthDenominator: Int
) {

    val excludedMaxLength
        get() = (TICKS_IN_FULL_NOTE / excludedMaxLengthDenominator).toLong()
}
