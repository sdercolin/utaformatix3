package ui.configuration

import csstype.AlignItems
import csstype.Display
import csstype.FlexDirection
import csstype.Length
import csstype.Margin
import csstype.VerticalAlign
import csstype.WhiteSpace
import csstype.em
import csstype.px
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
import process.lyrics.LyricsMappingRequest
import react.ChildrenBuilder
import react.create
import react.css.css
import react.dom.html.ReactHTML.div
import ui.LyricsMappingState
import ui.common.SubProps
import ui.common.configurationSwitch
import ui.common.subFC
import ui.strings.Strings
import ui.strings.string

external interface LyricsMappingProps : SubProps<LyricsMappingState>

val LyricsMappingBlock = subFC<LyricsMappingProps, LyricsMappingState> { _, state, editState ->
    FormGroup {
        div {
            configurationSwitch(
                isOn = state.isOn,
                onSwitched = { editState { copy(isOn = it) } },
                labelStrings = Strings.LyricsMapping,
            )
            Tooltip {
                val text = string(Strings.LyricsMappingDescription)
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
    }

    if (state.isOn) buildLyricsMappingDetail(state, editState)
}

private fun ChildrenBuilder.buildLyricsMappingDetail(
    state: LyricsMappingState,
    editState: (LyricsMappingState.() -> LyricsMappingState) -> Unit,
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
                            focused = false
                            FormLabel {
                                focused = false
                                Typography {
                                    variant = TypographyVariant.caption
                                    +string(Strings.LyricsMappingPreset)
                                }
                            }
                            TextField {
                                style = jso { minWidth = 16.em }
                                select = true
                                value = state.presetName.orEmpty().unsafeCast<Nothing?>()
                                (this.unsafeCast<BaseTextFieldProps>()).variant = FormControlVariant.standard
                                (this.unsafeCast<StandardTextFieldProps>()).onChange = { event ->
                                    val value = event.target.asDynamic().value as String
                                    editState {
                                        copy(
                                            presetName = value,
                                            request = LyricsMappingRequest.getPreset(value),
                                        )
                                    }
                                }
                                LyricsMappingRequest.Presets.forEach { preset ->
                                    MenuItem {
                                        value = preset.first
                                        +(preset.first)
                                    }
                                }
                            }
                        }
                        Button {
                            style = jso {
                                marginLeft = 24.px
                                marginBottom = 12.px
                            }
                            variant = ButtonVariant.outlined
                            color = ButtonColor.secondary
                            onClick = {
                                editState {
                                    copy(
                                        presetName = null,
                                        request = LyricsMappingRequest(),
                                    )
                                }
                            }
                            div {
                                +string(Strings.LyricsMappingPresetClear)
                            }
                        }
                    }
                    div {
                        TextField {
                            multiline = true
                            style = jso {
                                marginTop = 8.px
                                marginBottom = 8.px
                                width = 25.em
                            }
                            (this.unsafeCast<StandardTextFieldProps>()).InputProps = jso {
                                style = jso {
                                    paddingTop = 12.px
                                    paddingBottom = 12.px
                                }
                            }
                            minRows = 5
                            maxRows = 10
                            placeholder = string(Strings.LyricsMappingMapPlaceholder)
                            value = state.request.mapText
                            (this.unsafeCast<BaseTextFieldProps>()).variant = FormControlVariant.filled
                            (this.unsafeCast<StandardTextFieldProps>()).onChange = { event ->
                                val value = event.target.asDynamic().value as String
                                editState {
                                    copy(request = request.copy(mapText = value)).updatePresetName()
                                }
                            }
                        }
                    }
                    div {
                        style = jso {
                            paddingTop = 12.px
                            paddingBottom = 16.px
                        }
                        configurationSwitch(
                            isOn = state.request.mapToPhonemes,
                            onSwitched = {
                                editState {
                                    copy(request = request.copy(mapToPhonemes = it)).updatePresetName()
                                }
                            },
                            labelStrings = Strings.LyricsMappingToPhonemes,
                        )
                    }
                }
            }
        }
    }
}
