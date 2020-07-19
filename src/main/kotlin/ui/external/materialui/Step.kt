@file:JsModule("@material-ui/core/Step")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")

external var step: RClass<StepProps>

external interface StepProps : RProps {
    var active: Boolean
    var children: dynamic
    var classes: dynamic
    var completed: Boolean
    var disabled: Boolean
    var expanded: Boolean
    var key: String
}
