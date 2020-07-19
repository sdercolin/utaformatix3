@file:JsModule("@material-ui/lab/Alert")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val alert: RClass<AlertProps>

external interface AlertProps : RProps {
    var severity: String
    var variant: String
    var style: Style
}
