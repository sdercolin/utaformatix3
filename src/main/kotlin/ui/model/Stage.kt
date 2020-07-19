package ui.model

import ui.strings.Strings
import ui.strings.string

enum class Stage(val index: Int, private val displayNameKey: Strings) {
    Import(0, Strings.ImportProjectCaption),
    SelectOutputFormat(1, Strings.SelectOutputFormatCaption),
    ConfigureLyrics(2, Strings.ConfigureLyricsCaption),
    Export(3, Strings.ExportCaption);

    val displayName get() = string(displayNameKey)
}
