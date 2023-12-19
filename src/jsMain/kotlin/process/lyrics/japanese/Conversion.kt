package process.lyrics.japanese

import model.Format
import model.JapaneseLyricsType
import model.Note
import model.Project
import model.Track

fun convertJapaneseLyrics(project: Project, targetType: JapaneseLyricsType, targetFormat: Format): Project {
    val sourceType = project.japaneseLyricsType
    var tracks = cleanupJapaneseLyrics(project.tracks, sourceType)
    when {
        sourceType.isRomaji && !targetType.isRomaji -> tracks = convertRomajiToKana(tracks)
        !sourceType.isRomaji && targetType.isRomaji -> tracks = convertKanaToRomaji(tracks)
    }
    when {
        sourceType.isCV && !targetType.isCV -> tracks = convertCVToVCV(tracks)
        !sourceType.isCV && targetType.isCV -> tracks = convertVCVToCV(tracks)
    }
    if (targetFormat == Format.Ust) tracks = convertVowelConnections(tracks, targetType)
    return project.copy(tracks = tracks)
}

private fun convertRomajiToKana(tracks: List<Track>) = tracks.convertBetweenRomajiAndKana {
    it.toKana()
}

private fun convertKanaToRomaji(tracks: List<Track>) = tracks.convertBetweenRomajiAndKana {
    it.toRomaji()
}

private fun List<Track>.convertBetweenRomajiAndKana(conversion: (String) -> String) = convertJapaneseLyrics { notes ->
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

private fun convertCVToVCV(tracks: List<Track>) = tracks.convertJapaneseLyrics { notes ->
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

private fun convertVCVToCV(tracks: List<Track>) = tracks.convertJapaneseLyrics { notes ->
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

private fun List<Track>.convertJapaneseLyrics(conversion: (List<Note>) -> List<Note>) = map { track ->
    track.copy(notes = conversion(track.notes))
}

private fun convertVowelConnections(tracks: List<Track>, targetType: JapaneseLyricsType): List<Track> {
    return tracks.map { track ->
        val lyrics = track.notes.map { it.lyric }.toMutableList()
        if (lyrics.size < 2) return@map track

        for (index in 1 until lyrics.size) {
            val currentLyric = lyrics[index]
            if (currentLyric !in listOf("-", "ー")) continue
            val previousLyric = lyrics[index - 1]
            val newCurrentLyric = if (targetType.isRomaji) {
                if (previousLyric.isRomaji) previousLyric.takeLast(1)
                else null
            } else {
                if (previousLyric.isKana) findVowelKana(previousLyric)
                else null
            }
            newCurrentLyric?.let { lyrics[index] = it }
        }
        val notes = track.notes.mapIndexed { index, note -> note.copy(lyric = lyrics[index]) }
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
