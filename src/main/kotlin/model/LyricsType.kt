package model

import ui.strings.Strings
import ui.strings.string

enum class LyricsType(
    private val displayNameKey: Strings,
    val isRomaji: Boolean,
    val isCV: Boolean
) {
    Unknown(Strings.LyricsTypeUnknown, false, false),
    RomajiCv(Strings.LyricsTypeRomajiCV, true, true),
    RomajiVcv(Strings.LyricsTypeRomajiVCV, true, false),
    KanaCv(Strings.LyricsTypeKanaCV, false, true),
    KanaVcv(Strings.LyricsTypeKanaVCV, false, false);

    val displayName get() = string(displayNameKey)

    fun findBestConversionTargetIn(outputFormat: Format): LyricsType? {
        outputFormat.suggestedLyricType?.let {
            return it
        }
        val options = outputFormat.possibleLyricsTypes
        if (options.contains(this)) return this
        options.find { it.isRomaji == this.isRomaji }?.let {
            return it
        }
        options.find { it.isCV == this.isCV }?.let {
            return it
        }
        return options.firstOrNull()
    }
}
