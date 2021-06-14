package ui

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.css.LinearDimension
import kotlinx.css.marginTop
import kotlinx.html.js.onClickFunction
import model.Format
import model.Project
import org.w3c.files.File
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.setState
import styled.css
import styled.styledDiv
import ui.external.materialui.Severity
import ui.external.materialui.TypographyVariant
import ui.external.materialui.typography
import ui.external.react.fileDrop
import ui.strings.Strings
import ui.strings.string
import util.extensionName
import util.toList
import util.waitFileSelection

class Importer : RComponent<ImporterProps, ImporterState>() {

    override fun ImporterState.init() {
        isLoading = false
        snackbarError = SnackbarErrorState()
        dialogError = DialogErrorState()
    }

    override fun RBuilder.render() {
        title(Strings.ImportProjectCaption)

        styledDiv {
            css {
                marginTop = LinearDimension("40px")
            }
            attrs.onClickFunction = {
                GlobalScope.launch {
                    val accept = props.formats.joinToString(",") { it.extension }
                    val files = waitFileSelection(accept = accept, multiple = true)
                    checkFilesToImport(files)
                }
            }
            buildFileDrop()
        }

        messageBar(
            isShowing = state.snackbarError.isShowing,
            message = state.snackbarError.message,
            close = { closeMessageBar() },
            severityString = Severity.error
        )

        errorDialog(
            isShowing = state.dialogError.isShowing,
            title = state.dialogError.title,
            errorMessage = state.dialogError.message,
            close = { closeErrorDialog() }
        )

        progress(isShowing = state.isLoading)
    }

    private fun RBuilder.buildFileDrop() {
        fileDrop {
            attrs.onDrop = { files, _ ->
                checkFilesToImport(files.toList())
            }
            typography {
                attrs.variant = TypographyVariant.h5
                +string(Strings.ImportFileDescription)
            }
            styledDiv {
                css {
                    marginTop = LinearDimension("5px")
                }
                typography {
                    attrs.variant = TypographyVariant.body2
                    +string(Strings.ImportFileSubDescription)
                }
            }
        }
    }

    private fun checkFilesToImport(files: List<File>) {
        val fileFormat = getFileFormat(files)
        when {
            fileFormat == null -> setState {
                snackbarError = SnackbarErrorState(true, string(Strings.UnsupportedFileTypeImportError))
            }
            !fileFormat.multipleFile && files.count() > 1 -> setState {
                snackbarError = SnackbarErrorState(
                    true,
                    string(Strings.MultipleFileImportError, "format" to fileFormat.name)
                )
            }
            else -> import(files, fileFormat)
        }
    }

    private fun import(files: List<File>, format: Format) {
        setState { isLoading = true }
        GlobalScope.launch {
            try {
                delay(100)
                val parseFunction = format.parser
                val project = parseFunction(files).lyricsTypeAnalysed().requireValid()
                console.log("Project was imported successfully.")
                console.log(project)
                props.onImported.invoke(project)
            } catch (t: Throwable) {
                console.log(t)
                setState {
                    isLoading = false
                    dialogError = DialogErrorState(
                        isShowing = true,
                        title = string(Strings.ImportErrorDialogTitle),
                        message = t.message ?: t.toString()
                    )
                }
            }
        }
    }

    private fun getFileFormat(files: List<File>): Format? {
        val extensions = files.map { it.extensionName }.distinct()

        return if (extensions.count() > 1) null
        else props.formats.find { it.allExtensions.contains(".${extensions.first()}") }
    }

    private fun closeMessageBar() {
        setState { snackbarError = snackbarError.copy(isShowing = false) }
    }

    private fun closeErrorDialog() {
        setState { dialogError = dialogError.copy(isShowing = false) }
    }
}

external interface ImporterProps : RProps {
    var formats: List<Format>
    var onImported: (Project) -> Unit
}

external interface ImporterState : RState {
    var isLoading: Boolean
    var snackbarError: SnackbarErrorState
    var dialogError: DialogErrorState
}

data class SnackbarErrorState(
    val isShowing: Boolean = false,
    val message: String = ""
)
