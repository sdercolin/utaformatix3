package ui.configuration

import csstype.Length
import csstype.Margin
import csstype.VerticalAlign
import csstype.em
import csstype.px
import kotlinx.js.jso
import mui.icons.material.HelpOutline
import mui.material.BaseTextFieldProps
import mui.material.Box
import mui.material.FormControl
import mui.material.FormControlMargin
import mui.material.FormControlVariant
import mui.material.FormGroup
import mui.material.FormLabel
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
import react.css.css
import react.dom.html.ReactHTML.div
import ui.ProjectSplitState
import ui.common.SubProps
import ui.common.configurationSwitch
import ui.common.subFC
import ui.strings.Strings
import ui.strings.string

external interface ProjectSplitProps : SubProps<ProjectSplitState>

val ProjectSplitBlock = subFC<ProjectSplitProps, ProjectSplitState> { _, state, editState ->
    FormGroup {
        div {
            configurationSwitch(
                isOn = state.isOn,
                onSwitched = { editState { copy(isOn = it) } },
                labelStrings = Strings.ProjectSplit,
            )
            Tooltip {
                title = ReactNode(string(Strings.ProjectSplitDescription))
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
    if (state.isOn) buildProjectSplitDetail(state, editState)
}

private fun ChildrenBuilder.buildProjectSplitDetail(
    state: ProjectSplitState,
    editState: (ProjectSplitState.() -> ProjectSplitState) -> Unit,
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
                    FormLabel {
                        focused = false
                        Typography {
                            variant = TypographyVariant.caption
                            +string(Strings.ProjectSplitMaxTrackCountLabel)
                        }
                    }
                    margin = FormControlMargin.normal
                    variant = FormControlVariant.standard
                    focused = false
                    TextField {
                        sx { width = 5.em }
                        value = state.maxTrackCountInput
                        (this.unsafeCast<BaseTextFieldProps>()).variant = FormControlVariant.standard
                        (this.unsafeCast<StandardTextFieldProps>()).onChange = { event ->
                            val value = event.target.asDynamic().value as String
                            editState { copy(maxTrackCountInput = value) }
                        }
                        error = state.isReady.not()
                    }
                }
            }
        }
    }
}
