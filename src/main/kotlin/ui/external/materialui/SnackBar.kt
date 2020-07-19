@file:JsModule("@material-ui/core/Snackbar")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val snackbar: RClass<SnackbarProps>

external interface SnackbarProps : RProps {
    var anchorOrigin: SnackbarAnchorOrigin
    var open: Boolean
    var autoHideDuration: Int
    var onClose: () -> Unit
}
