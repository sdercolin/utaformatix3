@file:JsModule("@material-ui/core/TextField")
@file:JsNonModule
@file:Suppress("PropertyName")

package ui.external.materialui

import org.w3c.dom.events.Event
import react.RClass
import react.RProps

@JsName("default")
external val textField: RClass<TextFieldProps>

external interface TextFieldProps : RProps {
    var label: String
    var InputProps: TextFieldInputProps?
    var variant: String
    var focused: Boolean
    var onChange: ((Event) -> Unit)
}

