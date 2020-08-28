@file:JsModule("@material-ui/core/Tooltip")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val tooltip: RClass<TooltipProps>

external interface TooltipProps : RProps {
    var title: String
    var interactive: Boolean
    var placement: String
}
