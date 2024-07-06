package ui

import core.util.runCatchingCancellable
import csstype.px
import emotion.react.css
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import react.Props
import react.dom.html.ReactHTML.div
import react.useEffect
import react.useState
import ui.common.scopedFC
import ui.external.react.Markdown

val EmbeddedPage = scopedFC<EmbeddedPageProps> { props, scope ->
    var url: String? by useState { props.url }
    var content: String? by useState()

    useEffect {
        scope.launch {
            if (content == null || url != props.url) {
                url = props.url
                runCatchingCancellable {
                    window.fetch(props.url).await().text().await()
                }.onFailure { t ->
                    t.toString()
                }.onSuccess {
                    content = it
                }
            }
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
