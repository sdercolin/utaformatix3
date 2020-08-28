package ui.external.materialui

import kotlinx.css.VerticalAlign
import react.RBuilder
import react.RClass
import react.RHandler
import react.RProps

@JsModule("@material-ui/core/styles")
@JsNonModule
private external val stylesModule: dynamic

private val themeProviderComponent =
    stylesModule.ThemeProvider.unsafeCast<RClass<ThemeProviderProps>>()

external interface ThemeProviderProps : RProps {
    var theme: dynamic
}

private fun createMuiTheme(themeOptions: ThemeOptions): dynamic {
    return stylesModule.createMuiTheme(themeOptions)
}

fun RBuilder.themeProvider(themeOptions: ThemeOptions, handler: RHandler<ThemeProviderProps>) =
    themeProviderComponent {
        attrs {
            theme = createMuiTheme(themeOptions)
        }
        handler()
    }

data class ThemeOptions(
    val palette: PaletteOptions? = undefined
)

data class PaletteOptions(
    var type: String? = undefined,
    var primary: PaletteColorOptions? = undefined,
    var secondary: PaletteColorOptions? = undefined
)

data class PaletteColorOptions(
    var light: String? = undefined,
    var main: String? = undefined,
    var dark: String? = undefined,
    var contrastText: String? = undefined
)

data class Style(
    var width: String? = undefined,
    var minWidth: String? = undefined,
    var height: String? = undefined,
    var minHeight: String? = undefined,
    var marginLeft: String? = undefined,
    var marginRight: String? = undefined,
    var marginBottom: String? = undefined,
    var marginTop: String? = undefined,
    var padding: String? = undefined,
    var fontSize: String? = undefined,
    var fontWeight: String? = undefined,
    var borderRadius: String? = undefined,
    var backgroundColor: String? = undefined,
    var zIndex: Int? = undefined,
    var position: String? = undefined,
    var left: String? = undefined,
    var right: String? = undefined,
    var top: String? = undefined,
    var bottom: String? = undefined,
    var verticalAlign: VerticalAlign? = undefined
)
