package ui

import csstype.px
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import react.FC
import react.Props
import react.css.css
import react.dom.html.ReactHTML.div
import react.useState
import ui.external.react.Markdown

val EmbeddedPage = FC<EmbeddedPageProps> { props ->
    var url: String? by useState { props.url }
    var content: String? by useState()

    GlobalScope.launch {
        if (content == null || url != props.url) {
            url = props.url
            val result = try {
                window.fetch(props.url).await().text().await()
            } catch (t: Throwable) {
                t.toString()
            }
            content = result
        }
    }

    div {
        css {
            marginTop = 32.px
            marginBottom = 48.px
        }
        Markdown {
            +content.orEmpty()
        }
    }
}

external interface EmbeddedPageProps : Props {
    var url: String
}
