package ui.common

import csstype.px
import kotlinx.js.jso
import mui.material.Typography
import mui.material.styles.TypographyVariant
import react.ChildrenBuilder
import ui.strings.Strings
import ui.strings.string

fun ChildrenBuilder.title(titleKey: Strings) =
    Typography {
        style =
            jso {
                marginTop = 45.px
                marginBottom = 20.px
            }
        variant = TypographyVariant.h3
        +string(titleKey)
    }
