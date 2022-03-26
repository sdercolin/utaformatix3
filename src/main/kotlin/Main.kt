import kotlinx.browser.document
import react.dom.render
import ui.App
import ui.appThemeOptions
import ui.external.materialui.themeProvider
import ui.strings.Language
import ui.strings.initializeI18n

const val APP_NAME = "UtaFormatix"
const val APP_VERSION = "3.11"

suspend fun main() {
    initializeI18n(Language.English)
    render(document.getElementById("root")) {
        themeProvider(appThemeOptions) {
            child(App::class) {}
        }
    }
}
