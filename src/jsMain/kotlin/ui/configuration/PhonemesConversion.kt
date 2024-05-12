package ui.configuration

import core.model.Format
import core.process.phonemes.PhonemesMappingRequest
import csstype.AlignItems
import csstype.Display
import csstype.FlexDirection
import csstype.Length
import csstype.Margin
import csstype.VerticalAlign
import csstype.WhiteSpace
import csstype.em
import csstype.px
import emotion.react.css
import kotlinx.js.jso
import mui.icons.material.HelpOutline
import mui.material.BaseTextFieldProps
import mui.material.Button
import mui.material.ButtonColor
import mui.material.ButtonVariant
import mui.material.FormControl
import mui.material.FormControlMargin
import mui.material.FormControlVariant
import mui.material.FormGroup
import mui.material.FormLabel
import mui.material.MenuItem
import mui.material.Paper
import mui.material.StandardTextFieldProps
import mui.material.TextField
import mui.material.Tooltip
import mui.material.TooltipPlacement
import mui.material.Typography
import mui.material.styles.TypographyVariant
import react.ChildrenBuilder
import react.create
import react.dom.html.ReactHTML.div
import ui.PhonemesConversionState
import ui.common.SubProps
import ui.common.configurationSwitch
import ui.common.subFC
import ui.strings.Strings
import ui.strings.string

external interface PhonemesConversionProps : SubProps<PhonemesConversionState> {
    var sourceFormat: Format
    var targetFormat: Format
}

val PhonemesConversionBlock = subFC<PhonemesConversionProps, PhonemesConversionState> { props, state, editState ->
    FormGroup {
        div {
            configurationSwitch(
                isOn = state.isOn,
                onSwitched = { editState { copy(isOn = it) } },
                labelStrings = Strings.PhonemesConversion,
            )
        }
    }

    if (state.isOn) buildPhonemesConversionDetail(props, state, editState)
}

private fun ChildrenBuilder.buildPhonemesConversionDetail(
    props: PhonemesConversionProps,
    state: PhonemesConversionState,
    editState: (PhonemesConversionState.() -> PhonemesConversionState) -> Unit,
) {
    div {
        css {
            margin = Margin(horizontal = 40.px, vertical = 0.px)
            width = Length.maxContent
        }
        Paper {
            elevation = 0
            div {
                css {
                    margin = Margin(
                        horizontal = 24.px,
                        top = 16.px,
                        bottom = 24.px,
                    )
                }
                div {
                    style = jso {
                        paddingTop = 16.px
                        paddingBottom = 8.px
                    }
                    configurationSwitch(
                        isOn = state.useMapping,
                        onSwitched = {
                            editState {
                                copy(useMapping = it).updatePresetName()
                            }
                        },
                        labelStrings = Strings.PhonemesConversionEnableMapping,
                    )
                    Tooltip {
                        val text = string(Strings.PhonemesConversionEnableMappingDescription)
                        title = div.create {
                            css { whiteSpace = WhiteSpace.preLine }
                            +text
                        }
                        placement = TooltipPlacement.right
                        disableInteractive = false
                        HelpOutline {
                            style = jso {
                                verticalAlign = VerticalAlign.middle
                            }
                        }
                    }
                }
                FormGroup {
                    div {
                        style = jso {
                            display = Display.flex
                            flexDirection = FlexDirection.row
                            alignItems = AlignItems.flexEnd
                        }
                        FormControl {
                            margin = FormControlMargin.normal
                            variant = FormControlVariant.standard
                            disabled = state.useMapping.not()
                            focused = false
                            FormLabel {
                                focused = false
                                Typography {
                                    variant = TypographyVariant.caption
                                    +string(Strings.PhonemesMappingPreset)
                                }
                            }
                            TextField {
                                disabled = state.useMapping.not()
                                style = jso { minWidth = 16.em }
                                select = true
                                value = state.mappingPresetName.orEmpty().unsafeCast<Nothing?>()
                                (this.unsafeCast<BaseTextFieldProps>()).variant = FormControlVariant.standard
                                (this.unsafeCast<StandardTextFieldProps>()).onChange = { event ->
                                    val value = event.target.asDynamic().value as String
                                    editState {
                                        copy(
                                            mappingPresetName = value,
                                            mappingRequest = PhonemesMappingRequest.getPreset(value),
                                        )
                                    }
                                }
                                PhonemesMappingRequest.Presets
                                    .filter { preset ->
                                        props.sourceFormat in preset.sourceFormats + Format.UfData &&
                                            props.targetFormat in preset.targetFormats + Format.UfData
                                    }
                                    .forEach { preset ->
                                        MenuItem {
                                            value = preset.name
                                            +(preset.name)
                                        }
                                    }
                            }
                        }
                        Button {
                            style = jso {
                                marginLeft = 24.px
                                marginBottom = 12.px
                            }
                            disabled = state.useMapping.not()
                            variant = ButtonVariant.outlined
                            color = ButtonColor.secondary
                            onClick = {
                                editState {
                                    copy(
                                        mappingPresetName = null,
                                        mappingRequest = PhonemesMappingRequest(),
                                    )
                                }
                            }
                            div {
                                +string(Strings.PhonemesMappingPresetClear)
                            }
                        }
                    }
                    div {
                        TextField {
                            disabled = state.useMapping.not()
                            multiline = true
                            style = jso {
                                marginTop = 8.px
                                marginBottom = 16.px
                                width = 25.em
                            }
                            (this.unsafeCast<StandardTextFieldProps>()).InputProps = jso {
                                style = jso {
                                    paddingTop = 12.px
                                    paddingBottom = 12.px
                                }
                            }
                            minRows = 10
                            maxRows = 10
                            placeholder = string(Strings.PhonemesMappingMapPlaceholder)
                            value = state.mappingRequest.mapText
                            (this.unsafeCast<BaseTextFieldProps>()).variant = FormControlVariant.filled
                            (this.unsafeCast<StandardTextFieldProps>()).onChange = { event ->
                                val value = event.target.asDynamic().value as String
                                editState {
                                    copy(mappingRequest = mappingRequest.copy(mapText = value)).updatePresetName()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
