@file:JsModule("@material-ui/core/StepLabel")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val stepLabel: RClass<StepLabelProps>

external interface StepLabelProps : RProps {
    var children: dynamic
    var classes: dynamic
    var disabled: Boolean
    var error: Boolean
    var icon: dynamic
    var optional: dynamic
    var stepIconComponent: dynamic

    @Suppress("PropertyName")
    var StepIconProps: StepIconProps
}
