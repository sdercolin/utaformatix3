@file:JsModule("@material-ui/core/Select")
@file:JsNonModule

package ui.external.materialui

import org.w3c.dom.events.Event
import react.RClass
import react.RProps

@JsName("default")
external val select: RClass<SelectProps>

external interface SelectProps : RProps {
    var onChange: (Event) -> Unit
    var value: String
    var labelId: String
}
