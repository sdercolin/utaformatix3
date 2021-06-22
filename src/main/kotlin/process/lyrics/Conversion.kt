package process.lyrics

import model.Format
import model.LyricsType
import model.Note
import model.Project
import model.Track

fun convert(project: Project, targetType: LyricsType, targetFormat: Format): Project {
    val sourceType = project.lyricsType
    var tracks = cleanup(project.tracks, sourceType)
    when {
        sourceType.isRomaji && !targetType.isRomaji -> tracks = convertRomajiToKana(tracks)
        !sourceType.isRomaji && targetType.isRomaji -> tracks = convertKanaToRomaji(tracks)
    }
    when {
        sourceType.isCV && !targetType.isCV -> tracks = convertCVToVCV(tracks)
        !sourceType.isCV && targetType.isCV -> tracks = convertVCVToCV(tracks)
    }
    if (targetFormat == Format.Ust) tracks = convertVowelConnections(tracks, targetType)
    tracks = postCleanup(tracks, targetFormat)
    return project.copy(tracks = tracks)
}

private fun convertRomajiToKana(tracks: List<Track>) = tracks.convertBetweenRomajiAndKana {
    it.toKana()
}

private fun convertKanaToRomaji(tracks: List<Track>) = tracks.convertBetweenRomajiAndKana {
    it.toRomaji()
}

private fun List<Track>.convertBetweenRomajiAndKana(conversion: (String) -> String) = convert { notes ->
    notes.map { note ->
        val lyric = note.lyric
        if (lyric.contains(" ")) {
            val blankPos = lyric.indexOf(" ")
            val head = lyric.substring(0, blankPos)
            val body = lyric.substring(blankPos + 1)
            note.copy(lyric = "$head ${conversion(body)}")
        } else {
            note.copy(lyric = conversion(lyric))
        }
    }
}

private fun convertCVToVCV(tracks: List<Track>) = tracks.convert { notes ->
    val result = notes.toMutableList()
    var lastTail = "-"
    for (i in result.indices) {
        var tail = lastTail
        if (i > 0 && result[i].tickOn > result[i - 1].tickOff) {
            tail = "-"
        }
        when {
            result[i].lyric.isKana -> {
                lastTail = result[i].lyric.toRomaji().takeLast(1)
                result[i] = result[i].copy(lyric = "$tail ${result[i].lyric}")
            }
            result[i].lyric.isRomaji -> {
                lastTail = result[i].lyric.takeLast(1)
                result[i] = result[i].copy(lyric = "$tail ${result[i].lyric}")
            }
            else -> {
                lastTail = "-"
            }
        }
    }
    result.toList()
}

private fun convertVCVToCV(tracks: List<Track>) = tracks.convert { notes ->
    notes.map { note ->
        val lyric = note.lyric
        if (!lyric.contains(" ")) return@map note

        val blankPos = lyric.indexOf(" ")
        val body = lyric.substring(blankPos + 1)
        if (body.isKana || body.isRomaji) {
            note.copy(lyric = body)
        } else note
    }
}

private fun List<Track>.convert(conversion: (List<Note>) -> List<Note>) = map { track ->
    track.copy(notes = conversion(track.notes))
}

private fun convertVowelConnections(tracks: List<Track>, targetType: LyricsType): List<Track> {
    return tracks.map { track ->
        val notes = track.notes.zipWithNext().map { (previousNote, currentNote) ->
            if (currentNote.lyric !in listOf("-", "ー")) currentNote
            else {
                val previousLyric = previousNote.lyric
                val currentLyric = if (targetType.isRomaji) {
                    if (previousLyric.isRomaji) previousLyric.takeLast(1)
                    else null
                } else {
                    if (previousLyric.isKana) findVowelKana(previousLyric)
                    else null
                }
                currentLyric?.let { currentNote.copy(lyric = it) } ?: currentNote
            }
        }
        track.copy(notes = notes)
    }
}

private fun String.toRomaji() =
    kanaToRomaji
        .find { it.first == this }
        ?.second
        ?: this

private fun String.toKana() =
    kanaToRomaji
        .find { it.second == this }
        ?.first
        ?: this
