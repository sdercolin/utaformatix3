@file:JsModule("@material-ui/core/ListItemText")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val listItemText: RClass<ListItemTextProps>

external interface ListItemTextProps : RProps {
    var primary: dynamic
    var secondary: dynamic
}
