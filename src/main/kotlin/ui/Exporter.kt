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
import ui.common.ProgressProps
import ui.common.errorDialog
import ui.common.progress
import ui.common.scopedFC
import ui.common.title
import ui.strings.Strings
import ui.strings.string
import util.runCatchingCancellable

val Exporter = scopedFC<ExporterProps> { props, scope ->
    var progress by useState(ProgressProps.Initial)
    var dialogError by useState(DialogErrorState())
    title(Strings.ExporterTitleSuccess)
    buildExportInfo(props)
    buildButtons(props, scope, setProgress = { progress = it }, onDialogError = { dialogError = it })
    progress(progress)
    errorDialog(
        state = dialogError,
        close = { dialogError = dialogError.copy(isShowing = false) },
    )
}

private const val MAX_NOTIFICATIONS = 20

private fun ChildrenBuilder.buildExportInfo(props: ExporterProps) {
    val notifications = props.results.flatMap { result ->
        result.notifications.map { result.fileName to it }
    }
    if (notifications.isEmpty()) return

    Alert {
        severity = AlertColor.warning
        notifications.take(MAX_NOTIFICATIONS).map {
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
        if (notifications.size > MAX_NOTIFICATIONS) {
            div { +"..." }
        }
    }
}

private fun ChildrenBuilder.buildButtons(
    props: ExporterProps,
    scope: CoroutineScope,
    setProgress: (ProgressProps) -> Unit,
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
            onClick = { download(props, scope, setProgress, onDialogError) }
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
    setProgress: (ProgressProps) -> Unit,
    onDialogError: (DialogErrorState) -> Unit,
) {
    if (props.results.size == 1) {
        val result = props.results.first()
        saveAs(result.blob, result.fileName)
    } else {
        setProgress(ProgressProps(isShowing = true))
        scope.launch {
            runCatchingCancellable {
                val zip = JsZip()
                props.results.forEach { result ->
                    zip.file(result.fileName, result.blob)
                }
                val option = JsZipOption().also { it.type = "blob" }
                val blob = zip.generateAsync(option).await() as Blob
                saveAs(blob, props.results.first().fileName + "_all.zip")
                setProgress(ProgressProps.Initial)
            }.onFailure { t ->
                console.log(t)
                setProgress(ProgressProps.Initial)
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
