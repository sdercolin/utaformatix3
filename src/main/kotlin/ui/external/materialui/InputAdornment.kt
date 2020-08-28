@file:JsModule("@material-ui/core/InputAdornment")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val inputAdornment: RClass<InputAdornmentProps>

external interface InputAdornmentProps : RProps {
    var style: Style
    var position: String
}

