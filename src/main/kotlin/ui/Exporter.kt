package ui

import external.saveAs
import kotlinx.css.LinearDimension
import kotlinx.css.marginTop
import kotlinx.css.padding
import model.ExportNotification
import model.ExportResult
import model.Format
import model.Project
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.dom.div
import styled.css
import styled.styledDiv
import ui.external.materialui.ButtonVariant
import ui.external.materialui.Color
import ui.external.materialui.Icons
import ui.external.materialui.Severity
import ui.external.materialui.Style
import ui.external.materialui.alert
import ui.external.materialui.button
import ui.strings.Strings
import ui.strings.string

class Exporter : RComponent<ExporterProps, RState>() {

    override fun RBuilder.render() {
        title(Strings.ExporterTitleSuccess)
        buildExportInfo()
        buildButtons()
    }

    private fun RBuilder.buildExportInfo() {
        val notifications = props.result.notifications
        if (notifications.isEmpty()) return

        alert {
            attrs.severity = Severity.warning
            notifications.map { it.text }
                .forEach {
                    div { +it }
                }
        }
    }

    private fun RBuilder.buildButtons() {
        styledDiv {
            css {
                marginTop = LinearDimension("32px")
            }
            button {
                attrs {
                    variant = ButtonVariant.contained
                    onClick = { download() }
                }
                Icons.save {}
                styledDiv {
                    css {
                        padding = "8px"
                    }
                    +string(Strings.ExportButton)
                }
            }
            button {
                attrs {
                    style = Style(marginLeft = "16px")
                    variant = ButtonVariant.contained
                    color = Color.primary
                    onClick = { props.onRestart() }
                }
                Icons.refresh {}
                styledDiv {
                    css {
                        padding = "8px"
                    }
                    +string(Strings.RestartButton)
                }
            }
        }
    }

    private fun download() {
        saveAs(props.result.blob, props.result.fileName)
    }

    private val ExportNotification.text: String
        get() = string(
            when (this) {
                ExportNotification.PhonemeResetRequiredVSQ -> Strings.ExportNotificationPhonemeResetRequiredVSQ
                ExportNotification.PhonemeResetRequiredV4 -> Strings.ExportNotificationPhonemeResetRequiredV4
                ExportNotification.PhonemeResetRequiredV5 -> Strings.ExportNotificationPhonemeResetRequiredV5
                ExportNotification.TempoChangeIgnored -> Strings.ExportNotificationTimeSignatureIgnored
                ExportNotification.TimeSignatureIgnored -> Strings.ExportNotificationTempoChangeIgnored
                ExportNotification.PitchDataExported -> Strings.ExportNotificationPitchDataExported
                ExportNotification.DataOverLengthLimitIgnored -> Strings.ExportNotificationDataOverLengthLimitIgnored
            }
        )
}

external interface ExporterProps : RProps {
    var project: Project
    var format: Format
    var result: ExportResult
    var onRestart: () -> Unit
}
