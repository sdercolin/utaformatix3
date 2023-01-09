package ui.configuration

import csstype.Length
import csstype.Margin
import csstype.VerticalAlign
import csstype.em
import csstype.px
import kotlinx.js.jso
import model.Project
import mui.icons.material.ErrorOutline
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
import process.needWarningZoom
import process.projectZoomFactorOptions
import react.ChildrenBuilder
import react.ReactNode
import react.css.css
import react.dom.html.ReactHTML.div
import ui.ProjectZoomState
import ui.common.SubProps
import ui.common.configurationSwitch
import ui.common.subFC
import ui.strings.Strings
import ui.strings.string

external interface ProjectZoomProps : SubProps<ProjectZoomState> {
    var projects: List<Project>
}

val ProjectZoomBlock = subFC<ProjectZoomProps, ProjectZoomState> { props, state, editState ->
    FormGroup {
        div {
            configurationSwitch(
                isOn = state.isOn,
                onSwitched = { editState { copy(isOn = it) } },
                labelStrings = Strings.ProjectZoom,
            )
            Tooltip {
                title = ReactNode(string(Strings.ProjectZoomDescription))
                placement = TooltipPlacement.right
                disableInteractive = false
                HelpOutline {
                    style = jso {
                        verticalAlign = VerticalAlign.middle
                    }
                }
            }
            if (props.projects.any { it.needWarningZoom(state.factorValue) }) {
                Tooltip {
                    title = ReactNode(string(Strings.ProjectZoomWarning))
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
    if (state.isOn) buildProjectZoomDetail(state, editState)
}

private fun ChildrenBuilder.buildProjectZoomDetail(
    state: ProjectZoomState,
    editState: (ProjectZoomState.() -> ProjectZoomState) -> Unit,
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
                            +string(Strings.ProjectZooLabel)
                        }
                    }
                    margin = FormControlMargin.normal
                    variant = FormControlVariant.standard
                    focused = false
                    TextField {
                        style = jso { minWidth = 5.em }
                        select = true
                        value = state.factor.unsafeCast<Nothing?>()
                        (this.unsafeCast<BaseTextFieldProps>()).variant = FormControlVariant.standard
                        (this.unsafeCast<StandardTextFieldProps>()).onChange = { event ->
                            val value = event.target.asDynamic().value as String
                            editState { copy(factor = value) }
                        }
                        projectZoomFactorOptions.forEach { factor ->
                            MenuItem {
                                value = factor
                                +(factor)
                            }
                        }
                    }
                }
            }
        }
    }
}
