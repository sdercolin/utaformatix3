package ui

import csstype.AlignItems
import csstype.Display
import csstype.JustifyContent
import csstype.integer
import kotlinx.js.jso
import mui.material.Backdrop
import mui.material.CircularProgress
import mui.material.CircularProgressColor
import react.ChildrenBuilder
import react.css.css
import react.dom.html.ReactHTML.div

fun ChildrenBuilder.progress(isShowing: Boolean) {
    Backdrop {
        open = isShowing
        style = jso { zIndex = integer(1201) } // just over drawer: 1200
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
            }
            CircularProgress {
                color = CircularProgressColor.secondary
                disableShrink = true
            }
        }
    }
}
