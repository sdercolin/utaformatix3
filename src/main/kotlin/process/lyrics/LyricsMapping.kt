package process.lyrics

import external.Resources
import kotlinx.serialization.Serializable
import model.Note
import model.Project
import model.Track
import process.validateNotes

@Serializable
data class LyricsMappingRequest(
    val mapLines: List<String>,
    val mapToPhonemes: Boolean,
) {

    val map = mapLines.mapNotNull { line ->
        if (line.contains("=").not()) return@mapNotNull null
        val from = line.substringBefore("=").trim()
        val to = line.substringAfter("=").trim()
        from to to
    }.toMap()

    companion object {

        val Presets: List<Pair<String, LyricsMappingRequest>> by lazy {
            listOf(
                "VX-β 日本語かな -> 発音記号" to LyricsMappingRequest(
                    mapLines = Resources.lyricsMappingVxBetaJaText.lines(),
                    mapToPhonemes = false,
                ),
            )
        }
    }
}

fun Project.mapLyrics(request: LyricsMappingRequest) = copy(
    tracks = tracks.map { it.replaceLyrics(request) },
)

fun Track.replaceLyrics(request: LyricsMappingRequest) = copy(
    notes = notes.mapNotNull { note -> note.replaceLyrics(request).takeIf { it.lyric.isNotEmpty() } }
        .validateNotes(),
)

private fun Note.replaceLyrics(request: LyricsMappingRequest): Note {
    val mappedValue = request.map[this.lyric] ?: this.lyric
    return if (request.mapToPhonemes) {
        this.copy(phoneme = mappedValue)
    } else {
        this.copy(lyric = mappedValue)
    }
}
