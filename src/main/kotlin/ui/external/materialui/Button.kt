@file:JsModule("@material-ui/core/Button")
@file:JsNonModule

package ui.external.materialui

import org.w3c.dom.events.Event
import react.RClass
import react.RProps

@JsName("default")
external val button: RClass<ButtonProps>

external interface ButtonProps : RProps {
    var onClick: (Event) -> Unit
    var color: String
    var variant: String
    var disabled: Boolean
    var style: Style
}
