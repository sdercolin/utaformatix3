@file:JsModule("@material-ui/core/DialogTitle")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val dialogTitle: RClass<DialogTitleProps>

external interface DialogTitleProps : RProps {
    var variant: String
}
