@file:JsModule("@material-ui/core/Switch")
@file:JsNonModule

package ui.external.materialui

import org.w3c.dom.events.Event
import react.RClass
import react.RProps

@JsName("default")
external val switch: RClass<SwitchProps>

external interface SwitchProps : RProps {
    var onChange: (Event) -> Unit
    var color: String
    var checked: Boolean
}
