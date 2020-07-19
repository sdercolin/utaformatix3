@file:JsModule("@material-ui/core/Menu")
@file:JsNonModule

package ui.external.materialui

import org.w3c.dom.Element
import react.RClass
import react.RProps

@JsName("default")
external val menu: RClass<MenuProps>

external interface MenuProps : RProps {
    var onClose: () -> Unit
    var keepMounted: Boolean
    var open: Boolean
    var anchorEl: Element?
}
