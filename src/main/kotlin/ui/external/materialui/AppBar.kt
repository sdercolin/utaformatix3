@file:JsModule("@material-ui/core/AppBar")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val appBar: RClass<AppBarProps>

external interface AppBarProps : RProps {
    var position: String
}
