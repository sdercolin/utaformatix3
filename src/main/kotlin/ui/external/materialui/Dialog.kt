@file:JsModule("@material-ui/core/Dialog")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val dialog: RClass<DialogProps>

external interface DialogProps : RProps {
    var open: Boolean
    var onClose: () -> Unit
}
