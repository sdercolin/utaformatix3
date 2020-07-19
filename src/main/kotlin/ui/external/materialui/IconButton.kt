@file:JsModule("@material-ui/core/IconButton")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")

external val iconButton: RClass<IconButtonProps>

external interface IconButtonProps : RProps {
    var edge: String
    var className: String
    var color: String
    var ariaLabel: String
}
