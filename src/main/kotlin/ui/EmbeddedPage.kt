package ui

import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.css.LinearDimension
import kotlinx.css.marginBottom
import kotlinx.css.marginTop
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.setState
import styled.css
import styled.styledDiv
import ui.external.react.markdown

class EmbeddedPage : RComponent<EmbeddedPageProps, EmbeddedPageState>() {

    override fun RBuilder.render() {
        val content = state.content
        if (content == null || state.url != props.url) {
            state.url = props.url
            fetch()
        } else {
            styledDiv {
                css {
                    marginTop = LinearDimension("32px")
                    marginBottom = LinearDimension("48px")
                }
                markdown {
                    +content
                }
            }
        }
    }

    private fun fetch() {
        GlobalScope.launch {
            val result = try {
                window.fetch(props.url).await().text().await()
            } catch (t: Throwable) {
                t.toString()
            }
            setState { content = result }
        }
    }
}

external interface EmbeddedPageProps : RProps {
    var url: String
}

external interface EmbeddedPageState : RState {
    var url: String
    var content: String?
}
