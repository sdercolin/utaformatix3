package ui.common

import csstype.AlignItems
import csstype.Display
import csstype.JustifyContent
import mui.material.Backdrop
import mui.material.CircularProgress
import mui.material.CircularProgressColor
import mui.material.Grid
import mui.material.GridDirection
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.responsive
import react.ChildrenBuilder
import react.css.css

data class ProgressProps(
    val isShowing: Boolean,
    val total: Int? = null,
    val current: Int? = null,
) {
    companion object {
        val Initial = ProgressProps(false)
    }
}

fun ChildrenBuilder.progress(props: ProgressProps) {
    Backdrop {
        open = props.isShowing
        Grid {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
            }
            container = true
            direction = responsive(GridDirection.column)
            spacing = responsive(3)
            Grid {
                item = true
                CircularProgress {
                    color = CircularProgressColor.secondary
                    disableShrink = true
                }
            }
            if (props.total != null && props.current != null && props.total > 1) {
                Grid {
                    item = true
                    Typography {
                        variant = TypographyVariant.h6
                        +"${props.current} / ${props.total}"
                    }
                }
            }
        }
    }
}
