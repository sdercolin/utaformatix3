@file:JsModule("@material-ui/core/FormLabel")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val formLabel: RClass<FormLabelProps>

external interface FormLabelProps : RProps {
    var focused: Boolean
    var required: Boolean
}

