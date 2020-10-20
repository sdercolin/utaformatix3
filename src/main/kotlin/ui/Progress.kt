package ui

import kotlinx.css.Align
import kotlinx.css.Display
import kotlinx.css.JustifyContent
import kotlinx.css.alignItems
import kotlinx.css.display
import kotlinx.css.justifyContent
import react.RBuilder
import styled.css
import styled.styledDiv
import ui.external.materialui.Style
import ui.external.materialui.TypographyVariant
import ui.external.materialui.backdrop
import ui.external.materialui.typography
import ui.strings.Strings
import ui.strings.string

fun RBuilder.progress() {
    backdrop {
        attrs {
            open = true
            style = Style(zIndex = 1201) // just over drawer: 1200
        }
        styledDiv {
            css {
                display = Display.flex
                alignItems = Align.center
                justifyContent = JustifyContent.center
            }
            typography {
                attrs {
                    variant = TypographyVariant.h3
                }
                +(string(Strings.ProcessingOverlay))
            }
        }
    }
}
