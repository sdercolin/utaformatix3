@file:JsModule("@material-ui/core/Typography")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val typography: RClass<TypographyProps>

external interface TypographyProps : RProps {
    var variant: String
    var className: String
    var color: String
    var noWrap: Boolean
    var style: Style
}
