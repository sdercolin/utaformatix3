package ui

import react.RBuilder
import ui.external.materialui.SnackbarAnchorOrigin
import ui.external.materialui.alert
import ui.external.materialui.snackbar

fun RBuilder.messageBar(
    open: Boolean,
    message: String,
    onClose: () -> Unit,
    severityString: String
) = snackbar {
    attrs {
        anchorOrigin = SnackbarAnchorOrigin(
            vertical = "bottom",
            horizontal = "center"
        )
        autoHideDuration = 5000 // ms
        this.open = open
        this.onClose = onClose
    }
    alert {
        attrs {
            severity = severityString
            variant = "filled"
        }
        +message
    }
}
