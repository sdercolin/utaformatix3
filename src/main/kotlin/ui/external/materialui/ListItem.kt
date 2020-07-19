@file:JsModule("@material-ui/core/ListItem")
@file:JsNonModule

package ui.external.materialui

import org.w3c.dom.events.Event
import react.RClass
import react.RProps

@JsName("default")
external val listItem: RClass<ListItemProps>

external interface ListItemProps : RProps {
    var onClick: (Event) -> Unit
    var button: Boolean
    var style: Style
}
