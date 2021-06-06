@file:JsModule("@material-ui/core/StepLabel")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val stepLabel: RClass<StepLabelProps>

external interface StepLabelProps : RProps {
    @Suppress("PropertyName")
    var StepIconProps: StepIconProps
}
