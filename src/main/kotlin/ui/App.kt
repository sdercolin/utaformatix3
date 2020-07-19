package ui

import APP_NAME
import APP_VERSION
import kotlinx.css.FontWeight
import kotlinx.css.LinearDimension
import kotlinx.css.color
import kotlinx.css.fontSize
import kotlinx.css.fontWeight
import kotlinx.css.height
import kotlinx.css.margin
import kotlinx.css.marginLeft
import kotlinx.css.minHeight
import kotlinx.css.width
import model.Format
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.setState
import styled.css
import styled.styledDiv
import styled.styledSpan
import ui.external.materialui.Breakpoint
import ui.external.materialui.Color
import ui.external.materialui.Icons
import ui.external.materialui.Position
import ui.external.materialui.StepIconProps
import ui.external.materialui.StepIconPropsClasses
import ui.external.materialui.Style
import ui.external.materialui.TypographyVariant
import ui.external.materialui.appBar
import ui.external.materialui.container
import ui.external.materialui.cssBaseline
import ui.external.materialui.fab
import ui.external.materialui.step
import ui.external.materialui.stepLabel
import ui.external.materialui.stepper
import ui.external.materialui.toolbar
import ui.external.materialui.typography
import ui.model.Stage
import ui.model.StageInfo
import ui.strings.Strings.ReportUrl
import ui.strings.string
import kotlin.browser.window

class App : RComponent<RProps, AppState>() {

    override fun AppState.init() {
        stageInfoStack = listOf(StageInfo.Import)
    }

    override fun RBuilder.render() {
        cssBaseline {}
        styledDiv {
            css {
                height = LinearDimension("100vh")
            }
            styledDiv {
                css {
                    height = LinearDimension.fitContent
                    minHeight = LinearDimension("95vh")
                }
                container {
                    attrs {
                        maxWidth = Breakpoint.lg
                    }
                    buildAppBar()
                    buildStepper()
                    buildBody()
                }
            }
            child(CustomFooter::class) {}
            buildBackButton()
        }
    }

    private fun RBuilder.buildAppBar() {
        appBar {
            attrs {
                position = Position.fixed
            }
            toolbar {
                styledDiv {
                    css {
                        width = LinearDimension.fillAvailable
                    }
                    typography {
                        attrs {
                            variant = TypographyVariant.h6
                            color = Color.inherit
                        }
                        +APP_NAME
                        styledSpan {
                            css {
                                fontSize = LinearDimension("0.8rem")
                                marginLeft = LinearDimension("5px")
                                fontWeight = FontWeight("400")
                                color = kotlinx.css.Color.lightGrey
                            }
                            +"v$APP_VERSION"
                        }
                    }
                }
                ui.external.materialui.button {
                    attrs {
                        color = Color.inherit
                        onClick = { window.open(string(ReportUrl), target = "_blank") }
                    }
                    Icons.feedback {}
                }
                child(LanguageSelector::class) {
                    attrs {
                        onChangeLanguage = {
                            setState { }
                        }
                    }
                }
            }
        }
        // Append toolbar for fixing style problems
        toolbar {}
    }

    private fun RBuilder.buildStepper() {
        stepper {
            attrs {
                style = Style(backgroundColor = Color.transparent)
                activeStep = state.stageInfo.stage.index
            }
            Stage.values().forEach { stage ->
                step {
                    attrs {
                        key = stage.name
                    }
                    stepLabel {
                        attrs {
                            StepIconProps = StepIconProps(
                                StepIconPropsClasses(
                                    root = "main-stepper-icon",
                                    active = "main-stepper-icon-active",
                                    completed = "main-stepper-icon-completed"
                                )
                            )
                        }
                        +stage.displayName
                    }
                }
            }
        }
    }

    private fun RBuilder.buildBody() {
        styledDiv {
            css {
                margin(horizontal = LinearDimension("24px"))
            }
            when (val info = state.stageInfo) {
                is StageInfo.Import -> {
                    child(Importer::class) {
                        attrs {
                            formats = Format.importable
                            onImported = { goNextStage(StageInfo.SelectOutputFormat(it)) }
                        }
                    }
                }
                is StageInfo.SelectOutputFormat -> {
                    child(OutputFormatSelector::class) {
                        attrs {
                            formats = Format.exportable
                            project = info.project
                            onSelected = {
                                goNextStage(StageInfo.ConvertLyrics(info.project, it))
                            }
                        }
                    }
                }
                is StageInfo.ConvertLyrics -> {
                    child(ConfigurationEditor::class) {
                        attrs {
                            project = info.project
                            outputFormat = info.outputFormat
                            onFinished = { result, format ->
                                goNextStage(StageInfo.Export(info.project, result, format))
                            }
                        }
                    }
                }
                is StageInfo.Export -> {
                    child(Exporter::class) {
                        attrs {
                            project = info.project
                            format = info.outputFormat
                            result = info.result
                            onRestart = {
                                popAllStages()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun RBuilder.buildBackButton() {
        if (state.stageInfoStack.count() <= 1) return
        fab {
            attrs {
                size = "large"
                color = Color.primary
                onClick = {
                    popStage()
                }
                style = Style(
                    position = "fixed",
                    top = "auto",
                    left = "auto",
                    bottom = "32px",
                    right = "32px"
                )
            }
            Icons.arrowBack {}
        }
    }

    private fun goNextStage(stageInfo: StageInfo) = setState {
        stageInfoStack += stageInfo
    }

    private fun popStage() = setState {
        stageInfoStack = stageInfoStack.dropLast(1)
    }

    private fun popAllStages() = setState {
        stageInfoStack = stageInfoStack.take(1)
    }

    private val AppState.stageInfo get() = stageInfoStack.last()
}

external interface AppState : RState {
    var stageInfoStack: List<StageInfo>
}
