@file:JsModule("@material-ui/core/Container")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val container: RClass<ContainerProps>

external interface ContainerProps : RProps {
    var maxWidth: dynamic
}
