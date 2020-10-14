package ui

import react.RBuilder
import ui.external.materialui.Color
import ui.external.materialui.Style
import ui.external.materialui.backdrop
import ui.external.materialui.circularProgress

fun RBuilder.progress(progressColor: String = Color.inherit) {
    backdrop {
        attrs {
            open = true
            style = Style(zIndex = 1201) // just over drawer: 1200
        }
        circularProgress {
            attrs {
                color = progressColor
                disableShrink = true
            }
        }
    }
}
