package ui

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.css.LinearDimension
import kotlinx.css.margin
import kotlinx.css.marginTop
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
import org.w3c.dom.HTMLInputElement
import process.RESTS_FILLING_MAX_LENGTH_DEFAULT
import process.lyrics.convert
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.setState
import styled.css
import styled.styledDiv
import ui.external.materialui.ButtonVariant
import ui.external.materialui.Color
import ui.external.materialui.FormControlMargin
import ui.external.materialui.LabelPlacement
import ui.external.materialui.Style
import ui.external.materialui.TextFieldInputProps
import ui.external.materialui.TypographyVariant
import ui.external.materialui.button
import ui.external.materialui.formControl
import ui.external.materialui.formControlLabel
import ui.external.materialui.formGroup
import ui.external.materialui.formLabel
import ui.external.materialui.inputAdornment
import ui.external.materialui.paper
import ui.external.materialui.radio
import ui.external.materialui.radioGroup
import ui.external.materialui.switch
import ui.external.materialui.textField
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
            RESTS_FILLING_MAX_LENGTH_DEFAULT.toString()
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
                            vertical = LinearDimension("16px")
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
            formControl {
                attrs {
                    margin = FormControlMargin.normal
                }
                textField {
                    attrs {
                        focused = false
                        label = string(Strings.SlightRestsFillingThresholdLabel)
                        variant = "filled"
                        InputProps = TextFieldInputProps(
                            value = state.slightRestsFilling.excludedMaxLengthInput,
                            endAdornment = inputAdornment {
                                attrs {
                                    style = Style(marginTop = "1.25em")
                                    position = "end"
                                }
                                +(string(Strings.SlightRestsFillingThresholdLabelSuffix))
                            }
                        )
                        onChange = { event ->
                            val value = (event.target as HTMLInputElement).value
                            val maxLength = if (value.isEmpty()) 0
                            else value.toLongOrNull()
                            maxLength?.takeIf { it >= 0 }?.let {
                                setState {
                                    slightRestsFilling = slightRestsFilling.copy(
                                        excludedMaxLengthInput = it.toString()
                                    )
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
                val project =
                    if (lyricsConversionState.isOn && fromType != null && toType != null)
                        convert(props.project.copy(lyricsType = fromType), toType)
                    else props.project
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
    val excludedMaxLengthInput: String
) {
    val excludedMaxLength
        get() = excludedMaxLengthInput.toLongOrNull()
}
