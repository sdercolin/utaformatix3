@file:JsModule("@material-ui/core/FormControlLabel")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps
import react.ReactElement

@JsName("default")
external val formControlLabel: RClass<FormControlLabelProps>

external interface FormControlLabelProps : RProps {
    var label: dynamic
    var control: ReactElement
    var labelPlacement: String
    var value: String
}
