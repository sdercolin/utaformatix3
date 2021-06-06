@file:JsModule("@material-ui/core/Step")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")

external var step: RClass<StepProps>

external interface StepProps : RProps {
    var key: String
}
