package ui

import react.RBuilder
import ui.external.materialui.SnackbarAnchorOrigin
import ui.external.materialui.alert
import ui.external.materialui.snackbar

fun RBuilder.messageBar(
    isShowing: Boolean,
    message: String,
    close: () -> Unit,
    severityString: String
) = snackbar {
    attrs {
        anchorOrigin = SnackbarAnchorOrigin(
            vertical = "bottom",
            horizontal = "center"
        )
        autoHideDuration = 5000 // ms
        open = isShowing
        onClose = close
    }
    alert {
        attrs {
            severity = severityString
            variant = "filled"
        }
        +message
    }
}
