@file:JsModule("@material-ui/core/Backdrop")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val backdrop: RClass<BackdropProps>

external interface BackdropProps : RProps {
    var open: Boolean
    var style: Style
}
