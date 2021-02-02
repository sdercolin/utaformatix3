package ui.model

import ui.strings.Strings
import ui.strings.string

enum class Stage(val index: Int, private val displayNameKey: Strings?) {
    ExtraPage(-1, null),
    Import(0, Strings.ImportProjectCaption),
    SelectOutputFormat(1, Strings.SelectOutputFormatCaption),
    Configure(2, Strings.ConfigurationEditorCaption),
    Export(3, Strings.ExportCaption);

    val displayName get() = displayNameKey?.let { string(it) }

    companion object {
        val forStepper get() = listOf(Import, SelectOutputFormat, Configure, Export)
    }
}
