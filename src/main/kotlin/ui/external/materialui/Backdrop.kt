@file:JsModule("@material-ui/core/CircularProgress")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val circularProgress: RClass<CircularProgressProps>

external interface CircularProgressProps : RProps {
    var color: String
    var disableShrink: Boolean
}

