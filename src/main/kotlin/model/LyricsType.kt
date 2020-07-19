package model

import ui.strings.Strings
import ui.strings.string

enum class LyricsType(
    private val displayNameKey: Strings,
    val isRomaji: Boolean,
    val isCV: Boolean
) {
    UNKNOWN(Strings.LyricsTypeUnknown, false, false),
    ROMAJI_CV(Strings.LyricsTypeRomajiCV, true, true),
    ROMAJI_VCV(Strings.LyricsTypeRomajiVCV, true, false),
    KANA_CV(Strings.LyricsTypeKanaCV, false, true),
    KANA_VCV(Strings.LyricsTypeKanaVCV, false, false);

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
