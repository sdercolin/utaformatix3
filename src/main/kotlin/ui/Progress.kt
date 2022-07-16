package ui

import csstype.AlignItems
import csstype.Display
import csstype.JustifyContent
import mui.material.Backdrop
import mui.material.Typography
import mui.material.styles.TypographyVariant
import react.ChildrenBuilder
import react.css.css
import react.dom.html.ReactHTML.div
import ui.strings.Strings
import ui.strings.string

fun ChildrenBuilder.progress(isShowing: Boolean) {
    Backdrop {
        open = isShowing
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
            }
            Typography {
                variant = TypographyVariant.h3
                +string(Strings.ProcessingOverlay)
            }
        }
    }
}
