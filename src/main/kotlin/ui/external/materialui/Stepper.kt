@file:JsModule("@material-ui/core/Stepper")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val stepper: RClass<StepperProps>

external interface StepperProps : RProps {
    var activeStep: Number
    var alternativeLabel: Boolean
    var children: dynamic
    var classes: dynamic
    var connector: dynamic
    var nonLinear: Boolean
    var orientation: String
    var style: Style
}
