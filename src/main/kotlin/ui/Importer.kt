package ui

import csstype.VerticalAlign
import csstype.px
import exception.UnsupportedFileFormatError
import io.ImportParamsJson
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
import ui.common.ProgressProps
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
    var loadingProgress by useState(ProgressProps.Initial)
    var params by useState { loadImportParamsFromCookies() ?: ImportParams() }
    var snackbarError by useState(SnackbarErrorState())
    var dialogError by useState(DialogErrorState())

    suspend fun checkFilesToImport(files: List<File>, multipleMode: Boolean) {
        val onError = { t: Throwable ->
            console.log(t)
            if (t is UnsupportedFileFormatError) {
                snackbarError = SnackbarErrorState(true, t.message)
            } else {
                dialogError = DialogErrorState(
                    isShowing = true,
                    title = string(Strings.ImportErrorDialogTitle),
                    message = t.stackTraceToString(),
                )
            }
        }

        val fileFormat = runCatchingCancellable { getFileFormat(files, props) }
            .onFailure(onError)
            .getOrElse { return }

        when {
            fileFormat == null -> {
                snackbarError = SnackbarErrorState(true, string(Strings.UnsupportedFileTypeImportError))
            }
            !fileFormat.multipleFile && !multipleMode && files.count() > 1 -> {
                snackbarError = SnackbarErrorState(
                    true,
                    string(Strings.MultipleFileImportError, "format" to fileFormat.name),
                )
            }
            else -> import(
                scope,
                files,
                fileFormat,
                setProgress = { loadingProgress = it },
                onError,
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
                checkFilesToImport(files, params.multipleMode)
            }
        }
        buildFileDrop(scope) { checkFilesToImport(it, params.multipleMode) }
    }

    buildConfigurations(params) { params = it }

    messageBar(
        isShowing = snackbarError.isShowing,
        message = snackbarError.message,
        close = { closeMessageBar() },
        color = AlertColor.error,
    )

    errorDialog(
        state = dialogError,
        close = { closeErrorDialog() },
    )

    progress(loadingProgress)
}

private fun ChildrenBuilder.buildFileDrop(scope: CoroutineScope, onFiles: suspend (List<File>) -> Unit) {
    FileDrop {
        onDrop = { files, _ ->
            scope.launch { onFiles(files.toList()) }
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
        row = true
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
        div {
            css { marginLeft = 48.px }
            FormControlLabel {
                label = ReactNode(string(Strings.UseMultipleMode))
                control = Switch.create {
                    color = SwitchColor.secondary
                    checked = params.multipleMode
                    onChange = { event, _ ->
                        val checked = event.target.checked
                        onNewParams(params.copy(multipleMode = checked))
                    }
                }
                labelPlacement = LabelPlacement.end
            }
            Tooltip {
                title = ReactNode(string(Strings.UseMultipleModeDescription))
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
    setProgress: (ProgressProps) -> Unit,
    onError: (Throwable) -> Unit,
    props: ImporterProps,
    params: ImportParams,
) {
    scope.launch {
        runCatchingCancellable {
            delay(100)
            val parseFunction = format.parser
            val projects = if (params.multipleMode) {
                val total = files.count()
                console.log("Importing $total files...")
                val projects = files.mapIndexed { index, file ->
                    setProgress(ProgressProps(isShowing = true, total = total, current = index + 1))
                    val project = parseFunction(listOf(file), params).lyricsTypeAnalysed().requireValid()
                    console.log("Imported ${index + 1} of $total files")
                    project
                }
                console.log("A batch of $total projects are imported successfully.")
                projects
            } else {
                setProgress(ProgressProps(isShowing = true))
                val project = parseFunction(files, params).lyricsTypeAnalysed().requireValid()
                console.log("Project is imported successfully.")
                console.log(project)
                listOf(project)
            }
            saveImportParamsToCookies(params)
            props.onImported.invoke(projects)
        }.onFailure { t ->
            onError(t)
            setProgress(ProgressProps.Initial)
        }
    }
}

private suspend fun getFileFormat(files: List<File>, props: ImporterProps): Format? {
    val extensions = files.map { it.extensionName }.distinct()
    if (extensions.count() > 1) return null

    return props.formats.find { it.match(files) }
}

private fun loadImportParamsFromCookies() = Cookies.get(ImportParamsCookieName)
    ?.takeIf { it.isNotBlank() }
    ?.let(ImportParamsJson::parse)

private fun saveImportParamsToCookies(params: ImportParams) =
    Cookies.set(ImportParamsCookieName, ImportParamsJson.generate(params))

private const val ImportParamsCookieName = "import_params"

external interface ImporterProps : Props {
    var formats: List<Format>
    var onImported: (List<Project>) -> Unit
}

data class SnackbarErrorState(
    val isShowing: Boolean = false,
    val message: String = "",
)
