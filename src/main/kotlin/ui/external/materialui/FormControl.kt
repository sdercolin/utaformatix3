@file:JsModule("@material-ui/core/FormControl")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val formControl: RClass<FormControlProps>

external interface FormControlProps : RProps {
    var label: String
    var margin: String
    var disabled: Boolean
    var focused: Boolean
}

