@file:JsModule("@material-ui/core/MenuItem")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val menuItem: RClass<MenuItemProps>

external interface MenuItemProps : RProps {
    var onClick: () -> Unit
    var value: String
}
