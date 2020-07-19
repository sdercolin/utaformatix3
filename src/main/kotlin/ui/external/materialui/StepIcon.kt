package ui.external.materialui

data class StepIconProps(
    val classes: StepIconPropsClasses
)

data class StepIconPropsClasses(
    val root: String? = undefined,
    val active: String? = undefined,
    val completed: String? = undefined
)
