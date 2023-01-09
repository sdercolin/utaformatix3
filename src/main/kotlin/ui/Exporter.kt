package ui

import csstype.Color
import csstype.px
import external.JsZip
import external.JsZipOption
import external.saveAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.js.jso
import model.ExportNotification
import model.ExportResult
import model.Format
import mui.icons.material.Refresh
import mui.icons.material.SaveAlt
import mui.material.Alert
import mui.material.AlertColor
import mui.material.Button
import mui.material.ButtonColor
import mui.material.ButtonVariant
import mui.system.sx
import org.w3c.files.Blob
import react.ChildrenBuilder
import react.Props
import react.css.css
import react.dom.html.ReactHTML.div
import react.useState
import ui.common.DialogErrorState
import ui.common.errorDialog
import ui.common.progress
import ui.common.scopedFC
import ui.common.title
import ui.strings.Strings
import ui.strings.string
import util.runCatchingCancellable

val Exporter = scopedFC<ExporterProps> { props, scope ->
    var isProcessing by useState(false)
    var dialogError by useState(DialogErrorState())
    title(Strings.ExporterTitleSuccess)
    buildExportInfo(props)
    buildButtons(props, scope, setProcessing = { isProcessing = it }, onDialogError = { dialogError = it })
    progress(isShowing = isProcessing)
    errorDialog(
        isShowing = dialogError.isShowing,
        title = dialogError.title,
        errorMessage = dialogError.message,
        close = { dialogError = dialogError.copy(isShowing = false) },
    )
}

private fun ChildrenBuilder.buildExportInfo(props: ExporterProps) {
    val notifications = props.results.flatMap { result ->
        result.notifications.map { result.fileName to it }
    }
    if (notifications.isEmpty()) return

    Alert {
        severity = AlertColor.warning
        notifications.take(10).map {
            if (props.results.size > 1) {
                val (fileName, notification) = it
                "($fileName) ${notification.text}"
            } else {
                it.second.text
            }
        }
            .forEach {
                div { +it }
            }
        // TODO: Show more button
    }
}

private fun ChildrenBuilder.buildButtons(
    props: ExporterProps,
    scope: CoroutineScope,
    setProcessing: (Boolean) -> Unit,
    onDialogError: (DialogErrorState) -> Unit,
) {
    div {
        css {
            marginTop = 32.px
        }
        Button {
            variant = ButtonVariant.contained
            color = ButtonColor.secondary
            sx { backgroundColor = Color("#e0e0e0") }
            onClick = { download(props, scope, setProcessing, onDialogError) }
            SaveAlt()
            div {
                css { padding = 8.px }
                +string(Strings.ExportButton)
            }
        }
        Button {
            style = jso { marginLeft = 16.px }
            variant = ButtonVariant.contained
            color = ButtonColor.primary
            onClick = { props.onRestart() }
            Refresh()
            div {
                css { padding = 8.px }
                +string(Strings.RestartButton)
            }
        }
    }
}

private fun download(
    props: ExporterProps,
    scope: CoroutineScope,
    setProcessing: (Boolean) -> Unit,
    onDialogError: (DialogErrorState) -> Unit,
) {
    if (props.results.size == 1) {
        val result = props.results.first()
        saveAs(result.blob, result.fileName)
    } else {
        setProcessing(true)
        scope.launch {
            runCatchingCancellable {
                val zip = JsZip()
                props.results.forEach { result ->
                    zip.file(result.fileName, result.blob)
                }
                val option = JsZipOption().also { it.type = "blob" }
                val blob = zip.generateAsync(option).await() as Blob
                saveAs(blob, props.results.first().fileName + "_all.zip")
                setProcessing(false)
            }.onFailure { t ->
                console.log(t)
                setProcessing(false)
                onDialogError(
                    DialogErrorState(
                        isShowing = true,
                        title = string(Strings.ProcessErrorDialogTitle),
                        message = t.stackTraceToString(),
                    ),
                )
            }
        }
    }
}

private val ExportNotification.text: String
    get() = string(
        when (this) {
            ExportNotification.PhonemeResetRequiredVSQ -> Strings.ExportNotificationPhonemeResetRequiredVSQ
            ExportNotification.PhonemeResetRequiredV4 -> Strings.ExportNotificationPhonemeResetRequiredV4
            ExportNotification.PhonemeResetRequiredV5 -> Strings.ExportNotificationPhonemeResetRequiredV5
            ExportNotification.TimeSignatureIgnored -> Strings.ExportNotificationTimeSignatureIgnored
            ExportNotification.PitchDataExported -> Strings.ExportNotificationPitchDataExported
            ExportNotification.DataOverLengthLimitIgnored -> Strings.ExportNotificationDataOverLengthLimitIgnored
        },
    )

external interface ExporterProps : Props {
    var format: Format
    var results: List<ExportResult>
    var onRestart: () -> Unit
}
