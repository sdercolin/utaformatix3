@file:JsModule("@material-ui/core/InputLabel")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val inputLabel: RClass<InputLabelProps>

external interface InputLabelProps : RProps {
    var id: String
    var style: Style
    var focused: Boolean
}
