package ui

import kotlinx.css.LinearDimension
import kotlinx.css.margin
import react.RBuilder
import styled.css
import styled.styledDiv
import ui.external.materialui.TypographyVariant
import ui.external.materialui.typography
import ui.strings.Strings
import ui.strings.string

fun RBuilder.title(titleKey: Strings) = styledDiv {
    css {
        margin(vertical = LinearDimension("20px"))
    }
    typography {
        attrs.variant = TypographyVariant.h3
        +string(titleKey)
    }
}
