package ui.configuration

import core.process.restsFillingMaxLengthDenominatorOptions
import csstype.Length
import csstype.Margin
import csstype.VerticalAlign
import csstype.em
import csstype.px
import emotion.react.css
import kotlinx.js.jso
import mui.icons.material.HelpOutline
import mui.material.BaseTextFieldProps
import mui.material.Box
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
import mui.system.sx
import react.ChildrenBuilder
import react.ReactNode
import react.dom.html.ReactHTML.div
import ui.SlightRestsFillingState
import ui.common.SubProps
import ui.common.configurationSwitch
import ui.common.subFC
import ui.strings.Strings
import ui.strings.string

external interface SlightRestsFillingProps : SubProps<SlightRestsFillingState>

val SlightRestsFillingBlock = subFC<SlightRestsFillingProps, SlightRestsFillingState> { _, state, editState ->
    FormGroup {
        div {
            configurationSwitch(
                isOn = state.isOn,
                onSwitched = { editState { copy(isOn = it) } },
                labelStrings = Strings.SlightRestsFilling,
            )
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

    if (state.isOn) buildRestsFillingDetail(state, editState)
}

private fun ChildrenBuilder.buildRestsFillingDetail(
    state: SlightRestsFillingState,
    editState: (SlightRestsFillingState.() -> SlightRestsFillingState) -> Unit,
) {
    div {
        css {
            margin = Margin(horizontal = 40.px, vertical = 0.px)
            width = Length.maxContent
        }
        Paper {
            elevation = 0
            Box {
                style = jso {
                    margin = Margin(
                        left = 24.px,
                        right = 48.px,
                        top = 16.px,
                        bottom = 16.px,
                    )
                    paddingBottom = 8.px
                }
                sx { minWidth = 15.em }
                FormControl {
                    margin = FormControlMargin.normal
                    variant = FormControlVariant.standard
                    focused = false
                    FormLabel {
                        focused = false
                        Typography {
                            variant = TypographyVariant.caption
                            +string(Strings.SlightRestsFillingThresholdLabel)
                        }
                    }
                    TextField {
                        style = jso { minWidth = 5.em }
                        select = true
                        value = state.excludedMaxLengthDenominator.toString().unsafeCast<Nothing?>()
                        (this.unsafeCast<BaseTextFieldProps>()).variant = FormControlVariant.standard
                        (this.unsafeCast<StandardTextFieldProps>()).onChange = { event ->
                            val value = event.target.asDynamic().value as String
                            editState { copy(excludedMaxLengthDenominator = value.toInt()) }
                        }
                        restsFillingMaxLengthDenominatorOptions.forEach { denominator ->
                            MenuItem {
                                value = denominator.toString()
                                +string(
                                    Strings.SlightRestsFillingThresholdItem,
                                    "denominator" to denominator.toString(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
