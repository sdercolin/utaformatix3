package ui

import csstype.Color
import csstype.px
import external.saveAs
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
import react.ChildrenBuilder
import react.FC
import react.Props
import react.css.css
import react.dom.html.ReactHTML.div
import ui.common.title
import ui.strings.Strings
import ui.strings.string

val Exporter = FC<ExporterProps> { props ->
    title(Strings.ExporterTitleSuccess)
    buildExportInfo(props)
    buildButtons(props)
}

private fun ChildrenBuilder.buildExportInfo(props: ExporterProps) {
    val notifications = props.result.notifications
    if (notifications.isEmpty()) return

    Alert {
        severity = AlertColor.warning
        notifications.map { it.text }.forEach { div { +it } }
    }
}

private fun ChildrenBuilder.buildButtons(props: ExporterProps) {
    div {
        css {
            marginTop = 32.px
        }
        Button {
            variant = ButtonVariant.contained
            color = ButtonColor.secondary
            sx { backgroundColor = Color("#e0e0e0") }
            onClick = { download(props) }
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

private fun download(props: ExporterProps) {
    saveAs(props.result.blob, props.result.fileName)
}

private val ExportNotification.text: String
    get() = string(
        when (this) {
            ExportNotification.PhonemeResetRequiredVSQ -> Strings.ExportNotificationPhonemeResetRequiredVSQ
            ExportNotification.PhonemeResetRequiredV4 -> Strings.ExportNotificationPhonemeResetRequiredV4
            ExportNotification.PhonemeResetRequiredV5 -> Strings.ExportNotificationPhonemeResetRequiredV5
            ExportNotification.TempoChangeIgnored -> Strings.ExportNotificationTempoChangeIgnored
            ExportNotification.TimeSignatureIgnored -> Strings.ExportNotificationTimeSignatureIgnored
            ExportNotification.PitchDataExported -> Strings.ExportNotificationPitchDataExported
            ExportNotification.DataOverLengthLimitIgnored -> Strings.ExportNotificationDataOverLengthLimitIgnored
        },
    )

external interface ExporterProps : Props {
    var format: Format
    var result: ExportResult
    var onRestart: () -> Unit
}
