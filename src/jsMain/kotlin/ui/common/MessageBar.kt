package ui.common

import kotlinx.js.jso
import mui.material.Alert
import mui.material.AlertColor
import mui.material.AlertVariant
import mui.material.Snackbar
import mui.material.SnackbarOriginHorizontal
import mui.material.SnackbarOriginVertical
import react.ChildrenBuilder

fun ChildrenBuilder.messageBar(
    isShowing: Boolean,
    message: String,
    close: () -> Unit,
    color: AlertColor,
) = Snackbar {
    anchorOrigin =
        jso {
            vertical = SnackbarOriginVertical.bottom
            horizontal = SnackbarOriginHorizontal.center
        }
    autoHideDuration = 5000 // ms
    open = isShowing
    onClose = { _, _ -> close() }
    Alert {
        severity = color
        variant = AlertVariant.filled
        +message
    }
}
