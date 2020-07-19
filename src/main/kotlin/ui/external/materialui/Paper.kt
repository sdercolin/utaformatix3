@file:JsModule("@material-ui/core/Paper")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val paper: RClass<PaperProps>

external interface PaperProps : RProps {
    var elevation: Int
}
