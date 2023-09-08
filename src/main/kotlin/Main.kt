import kotlinx.browser.document
import react.create
import react.dom.client.createRoot
import ui.App
import ui.strings.Language
import ui.strings.initializeI18n

const val APP_NAME = "UtaFormatix"
const val APP_VERSION = "3.19"

suspend fun main() {
    initializeI18n(Language.English)
    createRoot(document.createElement("div").also { document.body!!.appendChild(it) })
        .render(App.create())
}
