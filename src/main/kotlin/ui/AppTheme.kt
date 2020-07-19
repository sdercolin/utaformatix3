package ui

import ui.external.materialui.PaletteColorOptions
import ui.external.materialui.PaletteOptions
import ui.external.materialui.ThemeOptions

val appThemeOptions = ThemeOptions(
    PaletteOptions(
        type = "dark",
        primary = PaletteColorOptions(
            main = "#3b3b3b",
            light = "#757575",
            dark = "#212121",
            contrastText = "#e8e8e8"
        ),
        secondary = PaletteColorOptions(
            main = "#f48fb1",
            light = "#f0afc5",
            dark = "#fff"
        )
    )
)
