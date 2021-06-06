package process.lyrics

import model.Format
import model.Track

fun postCleanup(tracks: List<Track>, targetFormat: Format): List<Track> {
    getDictionary(targetFormat) ?: return tracks
    return tracks.map { track ->
        track.copy(
            notes = track.notes.map { note ->
                note.copy(lyric = notePostCleanup(note.lyric, targetFormat))
            }
        )
    }
}

private fun notePostCleanup(lyric: String, targetFormat: Format): String {
    val dictionary = requireNotNull(getDictionary(targetFormat))
    return dictionary[lyric] ?: lyric
}

private fun getDictionary(targetFormat: Format): Map<String, String>? = when (targetFormat) {
    Format.Ccs -> mapOf("-" to "ー")
    else -> null
}
