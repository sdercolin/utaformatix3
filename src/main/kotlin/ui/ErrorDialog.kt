package ui

import kotlinx.browser.window
import react.RBuilder
import react.dom.div
import ui.external.materialui.Severity
import ui.external.materialui.Style
import ui.external.materialui.alert
import ui.external.materialui.button
import ui.external.materialui.dialog
import ui.external.materialui.dialogActions
import ui.external.materialui.dialogContent
import ui.external.materialui.dialogContentText
import ui.external.materialui.dialogTitle
import ui.strings.Strings
import ui.strings.string

fun RBuilder.errorDialog(
    isShowing: Boolean,
    close: () -> Unit,
    title: String,
    errorMessage: String
) {
    dialog {
        attrs {
            open = isShowing
            onClose = close
        }
        dialogTitle {
            +title
        }
        alert {
            attrs {
                severity = Severity.error
                style = Style(borderRadius = "0px")
            }
            +errorMessage
        }
        div {
            dialogContent {
                dialogContentText {
                    +string(Strings.ErrorDialogDescription)
                }
            }
        }
        dialogActions {
            button {
                attrs.onClick = { close() }
                +string(Strings.CancelButton)
            }
            button {
                attrs.onClick = {
                    close()
                    window.open(string(Strings.ReportUrl), target = "_blank")
                }
                +string(Strings.ReportButton)
            }
        }
    }
}

data class DialogErrorState(
    val isShowing: Boolean = false,
    val title: String = "",
    val message: String = ""
)
