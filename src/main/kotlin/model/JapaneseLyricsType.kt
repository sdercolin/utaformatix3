package model

enum class JapaneseLyricsType(
    val isRomaji: Boolean,
    val isCV: Boolean,
) {
    Unknown(false, false),
    RomajiCv(true, true),
    RomajiVcv(true, false),
    KanaCv(false, true),
    KanaVcv(false, false);

    fun findBestConversionTargetIn(outputFormat: Format): JapaneseLyricsType? {
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
