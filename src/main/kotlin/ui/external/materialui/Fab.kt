@file:JsModule("@material-ui/core/Fab")
@file:JsNonModule

package ui.external.materialui

import org.w3c.dom.events.Event
import react.RClass
import react.RProps

@JsName("default")
external val fab: RClass<FabProps>

external interface FabProps : RProps {
    var onClick: (Event) -> Unit
    var color: String
    var size: String
    var style: Style
}
