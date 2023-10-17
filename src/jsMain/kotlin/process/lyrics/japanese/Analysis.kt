package process.lyrics.japanese

import model.JapaneseLyricsType
import model.JapaneseLyricsType.KanaCv
import model.JapaneseLyricsType.KanaVcv
import model.JapaneseLyricsType.RomajiCv
import model.JapaneseLyricsType.RomajiVcv
import model.JapaneseLyricsType.Unknown
import model.Note
import model.Project
import model.Track

fun analyseJapaneseLyricsTypeForProject(project: Project): JapaneseLyricsType {
    if (project.tracks.isEmpty()) return Unknown

    val maxNoteCountInAllTracks = project.tracks.maxByOrNull { it.notes.count() }!!.notes.count()
    val minNoteCountForAvailableTrack = maxNoteCountInAllTracks * MIN_NOTE_RATIO_FOR_AVAILABLE_TRACK

    val availableResults = project.tracks
        .filter { it.notes.count() >= minNoteCountForAvailableTrack }
        .map { analyseLyricsTypeForTrack(it) }
        .distinct()
        .filter { it != Unknown }

    return if (availableResults.count() > 1) {
        Unknown
    } else {
        availableResults.firstOrNull() ?: Unknown
    }
}

private fun analyseLyricsTypeForTrack(track: Track): JapaneseLyricsType {
    val total = track.notes.count()
    val types = track.notes.map { checkNoteType(it) }
    val typePercentages = JapaneseLyricsType.values()
        .map { type -> type to types.count { it == type } }
        .map { it.first to (it.second.toDouble() / total) }
    return typePercentages
        .find { it.second > MIN_RELIABLE_PERCENTAGE }
        ?.first
        ?: Unknown
}

private fun checkNoteType(note: Note): JapaneseLyricsType {
    val lyric = note.lyric
    if (lyric.contains(" ")) {
        val mainLyric = lyric.substring(lyric.indexOf(" ") + 1)
        if (mainLyric.isKana) return KanaVcv
        if (mainLyric.isRomaji) return RomajiVcv
    } else {
        if (lyric.isKana) return KanaCv
        if (lyric.isRomaji) return RomajiCv
    }
    return Unknown
}

private const val MIN_RELIABLE_PERCENTAGE = 0.7
private const val MIN_NOTE_RATIO_FOR_AVAILABLE_TRACK = 0.1
