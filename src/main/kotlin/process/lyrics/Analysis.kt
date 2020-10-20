package process.lyrics

import model.LyricsType
import model.LyricsType.KANA_CV
import model.LyricsType.KANA_VCV
import model.LyricsType.ROMAJI_CV
import model.LyricsType.ROMAJI_VCV
import model.LyricsType.UNKNOWN
import model.Note
import model.Project
import model.Track

fun analyseLyricsTypeForProject(project: Project): LyricsType {
    if (project.tracks.isEmpty()) return UNKNOWN

    val maxNoteCountInAllTracks = project.tracks.maxBy { it.notes.count() }!!.notes.count()
    val minNoteCountForAvailableTrack = maxNoteCountInAllTracks * MIN_NOTE_RATIO_FOR_AVAILABLE_TRACK

    val availableResults = project.tracks
        .filter { it.notes.count() >= minNoteCountForAvailableTrack }
        .map { analyseLyricsTypeForTrack(it) }
        .distinct()
        .filter { it != UNKNOWN }

    return if (availableResults.count() > 1) {
        UNKNOWN
    } else {
        availableResults.firstOrNull() ?: UNKNOWN
    }
}

private fun analyseLyricsTypeForTrack(track: Track): LyricsType {
    val total = track.notes.count()
    val types = track.notes.map { checkNoteType(it) }
    val typePercentages = LyricsType.values()
        .map { type -> type to types.count { it == type } }
        .map { it.first to (it.second.toDouble() / total) }
    return typePercentages
        .find { it.second > MIN_RELIABLE_PERCENTAGE }
        ?.first
        ?: UNKNOWN
}

private fun checkNoteType(note: Note): LyricsType {
    val lyric = note.lyric
    if (lyric.contains(" ")) {
        val mainLyric = lyric.substring(lyric.indexOf(" ") + 1)
        if (mainLyric.isKana) return KANA_VCV
        if (mainLyric.isRomaji) return ROMAJI_VCV
    } else {
        if (lyric.isKana) return KANA_CV
        if (lyric.isRomaji) return ROMAJI_CV
    }
    return UNKNOWN
}

private const val MIN_RELIABLE_PERCENTAGE = 0.7
private const val MIN_NOTE_RATIO_FOR_AVAILABLE_TRACK = 0.1
