package model

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
    val lyricsType: LyricsType = LyricsType.UNKNOWN
) {

    fun lyricsTypeAnalysed() =
        copy(
            lyricsType = analyseLyricsTypeForProject(this)
                .takeIf { format.possibleLyricsTypes.contains(it) }
                ?: LyricsType.UNKNOWN
        )
}
