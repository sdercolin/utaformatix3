@file:JsModule("@material-ui/core/FormGroup")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val formGroup: RClass<FormGroupProps>

external interface FormGroupProps : RProps {
    var row: Boolean
}
