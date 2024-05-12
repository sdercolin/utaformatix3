package core.process.lyrics.chinese

import core.external.Resources
import core.model.Project

fun convertChineseLyricsToPinyin(project: Project): Project {
    val tracks = project.tracks.map { track ->
        track.copy(
            notes = track.notes.map { note ->
                note.copy(lyric = note.lyric.toPinyin())
            },
        )
    }
    return project.copy(tracks = tracks)
}

private fun String.toPinyin(): String {
    return dictionary[this] ?: this
}

private val dictionary by lazy {
    Resources.chineseLyricsDictionaryText.lines()
        .map { it.split(",") }
        .associate { it[0] to it[1] }
}
