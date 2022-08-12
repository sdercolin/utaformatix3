package model

import exception.IllegalNotePositionException
import org.w3c.files.File
import process.lyrics.analyseLyricsTypeForProject

data class Project(
    val format: Format,
    val inputFiles: List<File>,
    val name: String,
    val tracks: List<Track>,
    val timeSignatures: List<TimeSignature>,
    val tempos: List<Tempo>,
    val measurePrefix: Int,
    val importWarnings: List<ImportWarning>,
    val lyricsType: LyricsType = LyricsType.Unknown
) {

    fun lyricsTypeAnalysed() =
        copy(
            lyricsType = analyseLyricsTypeForProject(this)
                .takeIf { format.possibleLyricsTypes.contains(it) }
                ?: LyricsType.Unknown
        )

    fun withoutEmptyTracks() =
        copy(
            tracks = tracks.filter { it.notes.isNotEmpty() }
                .mapIndexed { index, track -> track.copy(id = index) }
        ).takeIf { it.tracks.isNotEmpty() }

    val hasXSampaData get() = tracks.any { track -> track.notes.any { it.phoneme != null } }

    fun requireValid() = this.also {
        tracks.forEachIndexed { index, track ->
            val firstNote = track.notes.firstOrNull() ?: return@forEachIndexed
            if (firstNote.tickOn < 0L) {
                throw IllegalNotePositionException(firstNote, index)
            }
        }
    }
}
