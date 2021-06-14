@file:JsModule("@material-ui/core/RadioGroup")
@file:JsNonModule

package ui.external.materialui

import org.w3c.dom.events.Event
import react.RClass
import react.RProps

@JsName("default")
external val radioGroup: RClass<RadioGroupProps>

external interface RadioGroupProps : RProps {
    var onChange: (Event) -> Unit
    var value: String
    var row: Boolean
}
