package ui

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
import ui.strings.Strings.CancelButton
import ui.strings.Strings.ErrorDialogDescription
import ui.strings.Strings.ReportButton
import ui.strings.Strings.ReportUrl
import ui.strings.string
import kotlin.browser.window

fun RBuilder.errorDialog(
    open: Boolean,
    onClose: () -> Unit,
    title: String,
    errorMessage: String
) {
    dialog {
        attrs {
            this.open = open
            this.onClose = onClose
        }
        dialogTitle {
            +(title)
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
                    +(string(ErrorDialogDescription))
                }
            }
        }
        dialogActions {
            button {
                attrs {
                    onClick = {
                        onClose()
                    }
                }
                +(string(CancelButton))
            }
            button {
                attrs {
                    onClick = {
                        onClose()
                        window.open(string(ReportUrl), target = "_blank")
                    }
                }
                +(string(ReportButton))
            }
        }
    }
}

data class DialogErrorState(
    val open: Boolean = false,
    val title: String = "",
    val message: String = ""
)
