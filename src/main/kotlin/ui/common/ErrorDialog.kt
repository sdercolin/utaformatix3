package ui.common

import csstype.px
import kotlinx.browser.window
import kotlinx.js.jso
import mui.material.Alert
import mui.material.AlertColor
import mui.material.Button
import mui.material.ButtonColor
import mui.material.Dialog
import mui.material.DialogActions
import mui.material.DialogContent
import mui.material.DialogContentText
import mui.material.DialogTitle
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import ui.strings.Strings
import ui.strings.string

fun ChildrenBuilder.errorDialog(
    isShowing: Boolean,
    close: () -> Unit,
    title: String,
    errorMessage: String,
) {
    Dialog {
        open = isShowing
        onClose = { _, _ -> close() }
        DialogTitle {
            +title
        }
        Alert {
            severity = AlertColor.error
            style = jso { borderRadius = 0.px }
            +errorMessage
        }
        div {
            DialogContent {
                DialogContentText {
                    +string(Strings.ErrorDialogDescription)
                }
            }
        }
        DialogActions {
            Button {
                onClick = { close() }
                color = ButtonColor.inherit
                +string(Strings.CancelButton)
            }
            Button {
                onClick = {
                    close()
                    window.open(string(Strings.ReportUrl), target = "_blank")
                }
                color = ButtonColor.inherit
                +string(Strings.ReportButton)
            }
        }
    }
}

data class DialogErrorState(
    val isShowing: Boolean = false,
    val title: String = "",
    val message: String = "",
)
