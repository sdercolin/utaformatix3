package ui

import csstype.Margin
import csstype.px
import mui.material.Typography
import mui.material.styles.TypographyVariant
import react.ChildrenBuilder
import react.css.css
import react.dom.html.ReactHTML.div
import ui.strings.Strings
import ui.strings.string

fun ChildrenBuilder.title(titleKey: Strings) = div {
    css {
        Margin(vertical = 20.px, horizontal = 0.px)
    }
    Typography {
        variant = TypographyVariant.h3
        +string(titleKey)
    }
}
