package ui

import APP_NAME
import APP_VERSION
import csstype.Auto
import csstype.Length
import csstype.Margin
import csstype.NamedColor
import csstype.Position
import csstype.number
import csstype.px
import csstype.rem
import csstype.vh
import kotlinx.browser.window
import kotlinx.js.jso
import model.Format
import mui.icons.material.ArrowBack
import mui.icons.material.Feedback
import mui.icons.material.LiveHelp
import mui.material.AppBar
import mui.material.AppBarPosition
import mui.material.Button
import mui.material.ButtonColor
import mui.material.Container
import mui.material.CssBaseline
import mui.material.Fab
import mui.material.FabColor
import mui.material.Size
import mui.material.Step
import mui.material.StepLabel
import mui.material.Stepper
import mui.material.Toolbar
import mui.material.Tooltip
import mui.material.Typography
import mui.material.styles.ThemeProvider
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.ChildrenBuilder
import react.FC
import react.Props
import react.ReactNode
import react.css.css
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.useState
import ui.model.Stage
import ui.model.StageInfo
import ui.strings.Language
import ui.strings.Strings
import ui.strings.string

val App = FC<Props> {
    var stageInfoStack: List<StageInfo> by useState(listOf(StageInfo.Import))

    @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
    var language: Language? by useState(null)

    fun pushStage(stageInfo: StageInfo) {
        val stack = stageInfoStack.toMutableList()
        if (stageInfoStack.last().stage == stageInfo.stage) {
            stack.removeAt(stack.lastIndex)
        }
        stack.add(stageInfo)
        stageInfoStack = stack
    }

    fun popStage() {
        stageInfoStack = stageInfoStack.dropLast(1)
    }

    fun popAllStages() {
        stageInfoStack = stageInfoStack.take(1)
    }

    fun getStageInfo() = stageInfoStack.last()

    ThemeProvider {
        theme = appTheme
        CssBaseline {}
        div {
            css {
                height = 100.vh
            }
            div {
                css {
                    height = Length.fitContent
                    minHeight = 95.vh
                }

                Container {
                    maxWidth = "xl"
                    buildAppBar(pushStage = { pushStage(it) }, onChangeLanguage = { language = it })
                    buildStepper(getStageInfo().stage.index)
                    buildBody(getStageInfo(), pushStage = { pushStage(it) }, popAllStages = { popAllStages() })
                }
            }
            CustomFooter {
                onOpenEmbeddedPage = { urlKey -> pushStage(StageInfo.ExtraPage(urlKey)) }
            }
            buildBackButton(stageInfoStack, onClickBackButton = { popStage() })
        }
    }
}

private fun ChildrenBuilder.buildAppBar(pushStage: (StageInfo) -> Unit, onChangeLanguage: (Language) -> Unit) {
    AppBar {
        position = AppBarPosition.fixed
        style = jso {
            background = appTheme.palette.primary.main
        }
        Toolbar {
            Typography {
                css {
                    color = appTheme.palette.primary.contrastText
                }
                variant = TypographyVariant.h6
                +APP_NAME
                span {
                    css {
                        fontSize = 0.8.rem
                        marginLeft = 5.px
                        color = NamedColor.lightgrey
                    }
                    +"v$APP_VERSION"
                }
                sx { flexGrow = number(1.0) }
            }
            Tooltip {
                title = ReactNode(string(Strings.FrequentlyAskedQuestionTooltip))
                disableInteractive = true
                Button {
                    color = ButtonColor.inherit
                    onClick = { pushStage(StageInfo.ExtraPage(Strings.FaqUrl)) }
                    LiveHelp()
                }
            }
            Tooltip {
                title = ReactNode(string(Strings.ReportFeedbackTooltip))
                disableInteractive = true
                Button {
                    color = ButtonColor.inherit
                    onClick = { window.open(string(Strings.ReportUrl), target = "_blank") }
                    Feedback()
                }
            }
            LanguageSelector {
                this.onChangeLanguage = onChangeLanguage
            }
        }
    }
    // Append toolbar for fixing style problems
    Toolbar {}
}

private fun ChildrenBuilder.buildStepper(stageIndex: Int) {
    if (stageIndex < 0) return
    Stepper {
        css {
            margin = Margin(horizontal = 0.px, vertical = 24.px)
        }
        style = jso {
            background = NamedColor.transparent
        }
        activeStep = stageIndex
        Stage.forStepper.forEachIndexed { index, stage ->
            Step {
                completed = stageIndex > index
                StepLabel {
                    stage.displayName?.let { +it }
                }
            }
        }
    }
}

private fun ChildrenBuilder.buildBody(stageInfo: StageInfo, pushStage: (StageInfo) -> Unit, popAllStages: () -> Unit) {
    div {
        css {
            margin = Margin(horizontal = 24.px, vertical = 0.px)
        }
        when (stageInfo) {
            is StageInfo.Import -> Importer {
                formats = Format.importable
                onImported = { pushStage(StageInfo.SelectOutputFormat(it)) }
            }
            is StageInfo.SelectOutputFormat -> OutputFormatSelector {
                formats = Format.exportable
                projects = stageInfo.projects
                onSelected = { pushStage(StageInfo.Configure(stageInfo.projects, it)) }
            }
            is StageInfo.Configure -> ConfigurationEditor {
                projects = stageInfo.projects
                outputFormat = stageInfo.outputFormat
                onFinished = { result, format ->
                    pushStage(StageInfo.Export(result, format))
                }
            }
            is StageInfo.Export -> Exporter {
                format = stageInfo.outputFormat
                results = stageInfo.results
                onRestart = { popAllStages() }
            }
            is StageInfo.ExtraPage -> EmbeddedPage {
                url = string(stageInfo.urlKey)
            }
        }
    }
}

private fun ChildrenBuilder.buildBackButton(stageInfoStack: List<StageInfo>, onClickBackButton: () -> Unit) {
    if (stageInfoStack.count() <= 1) return
    Fab {
        size = Size.large
        color = FabColor.primary
        onClick = { onClickBackButton() }
        style = jso {
            position = Position.fixed
            top = Auto.auto
            left = Auto.auto
            bottom = 32.px
            right = 32.px
        }
        ArrowBack()
    }
}
