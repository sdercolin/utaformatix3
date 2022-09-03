package ui

import ImportParamsJson
import csstype.VerticalAlign
import csstype.px
import exception.UnsupportedFileFormatError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.js.jso
import model.Format
import model.ImportParams
import model.Project
import mui.icons.material.HelpOutline
import mui.material.AlertColor
import mui.material.FormControlLabel
import mui.material.FormGroup
import mui.material.LabelPlacement
import mui.material.Switch
import mui.material.SwitchColor
import mui.material.Tooltip
import mui.material.TooltipPlacement
import mui.material.Typography
import mui.material.styles.TypographyVariant
import org.w3c.files.File
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.create
import react.css.css
import react.dom.html.ReactHTML.div
import react.useState
import ui.common.DialogErrorState
import ui.common.errorDialog
import ui.common.messageBar
import ui.common.progress
import ui.common.scopedFC
import ui.common.title
import ui.external.Cookies
import ui.external.react.FileDrop
import ui.strings.Strings
import ui.strings.string
import util.extensionName
import util.runCatchingCancellable
import util.toList
import util.waitFileSelection

val Importer = scopedFC<ImporterProps> { props, scope ->
    var isLoading by useState(false)
    var params by useState { loadImportParamsFromCookies() ?: ImportParams() }
    var snackbarError by useState(SnackbarErrorState())
    var dialogError by useState(DialogErrorState())

    fun checkFilesToImport(files: List<File>) {
        val fileFormat = getFileFormat(files, props)
        when {
            fileFormat == null -> {
                snackbarError = SnackbarErrorState(true, string(Strings.UnsupportedFileTypeImportError))
            }
            !fileFormat.multipleFile && files.count() > 1 -> {
                snackbarError = SnackbarErrorState(
                    true,
                    string(Strings.MultipleFileImportError, "format" to fileFormat.name),
                )
            }
            else -> import(
                scope,
                files,
                fileFormat,
                setLoading = { isLoading = it },
                onSnackBarError = { snackbarError = it },
                onDialogError = { dialogError = it },
                props,
                params,
            )
        }
    }

    fun closeMessageBar() {
        snackbarError = snackbarError.copy(isShowing = false)
    }

    fun closeErrorDialog() {
        dialogError = dialogError.copy(isShowing = false)
    }

    title(Strings.ImportProjectCaption)

    div {
        css {
            marginTop = 40.px
        }
        onClick = {
            scope.launch {
                val accept = props.formats.joinToString(",") { it.extension }
                val files = waitFileSelection(accept = accept, multiple = true)
                checkFilesToImport(files)
            }
        }
        buildFileDrop { checkFilesToImport(it) }
    }

    buildConfigurations(params) { params = it }

    messageBar(
        isShowing = snackbarError.isShowing,
        message = snackbarError.message,
        close = { closeMessageBar() },
        color = AlertColor.error,
    )

    errorDialog(
        isShowing = dialogError.isShowing,
        title = dialogError.title,
        errorMessage = dialogError.message,
        close = { closeErrorDialog() },
    )

    progress(isShowing = isLoading)
}

private fun ChildrenBuilder.buildFileDrop(onFiles: (List<File>) -> Unit) {
    FileDrop {
        onDrop = { files, _ ->
            onFiles(files.toList())
        }
        Typography {
            variant = TypographyVariant.h5
            +string(Strings.ImportFileDescription)
        }
        div {
            css {
                marginTop = 5.px
            }
            Typography {
                variant = TypographyVariant.body2
                +string(Strings.ImportFileSubDescription)
            }
        }
    }
}

private fun ChildrenBuilder.buildConfigurations(params: ImportParams, onNewParams: (ImportParams) -> Unit) {
    FormGroup {
        div {
            FormControlLabel {
                label = ReactNode(string(Strings.UseSimpleImport))
                control = Switch.create {
                    color = SwitchColor.secondary
                    checked = params.simpleImport
                    onChange = { event, _ ->
                        val checked = event.target.checked
                        onNewParams(params.copy(simpleImport = checked))
                    }
                }
                labelPlacement = LabelPlacement.end
            }
            Tooltip {
                title = ReactNode(string(Strings.UseSimpleImportDescription))
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
}

private fun import(
    scope: CoroutineScope,
    files: List<File>,
    format: Format,
    setLoading: (Boolean) -> Unit,
    onSnackBarError: (SnackbarErrorState) -> Unit,
    onDialogError: (DialogErrorState) -> Unit,
    props: ImporterProps,
    params: ImportParams,
) {
    setLoading(true)
    scope.launch {
        runCatchingCancellable {
            delay(100)
            val parseFunction = format.parser
            val project = parseFunction(files, params).lyricsTypeAnalysed().requireValid()
            console.log("Project was imported successfully.")
            console.log(project)
            saveImportParamsToCookies(params)
            props.onImported.invoke(project)
        }.onFailure { t ->
            console.log(t)
            setLoading(false)
            if (t is UnsupportedFileFormatError) {
                onSnackBarError(SnackbarErrorState(true, t.message))
            } else {
                onDialogError(
                    DialogErrorState(
                        isShowing = true,
                        title = string(Strings.ImportErrorDialogTitle),
                        message = t.stackTraceToString(),
                    ),
                )
            }
        }
    }
}

private fun getFileFormat(files: List<File>, props: ImporterProps): Format? {
    val extensions = files.map { it.extensionName }.distinct()

    return if (extensions.count() > 1) null
    else props.formats.find { it.allExtensions.contains(".${extensions.first()}") }
}

private fun loadImportParamsFromCookies() = Cookies.get(ImportParamsCookieName)
    ?.takeIf { it.isNotBlank() }
    ?.let(ImportParamsJson::parse)

private fun saveImportParamsToCookies(params: ImportParams) =
    Cookies.set(ImportParamsCookieName, ImportParamsJson.generate(params))

private const val ImportParamsCookieName = "import_params"

external interface ImporterProps : Props {
    var formats: List<Format>
    var onImported: (Project) -> Unit
}

data class SnackbarErrorState(
    val isShowing: Boolean = false,
    val message: String = "",
)
