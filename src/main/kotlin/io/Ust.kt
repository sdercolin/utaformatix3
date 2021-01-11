package io

import external.Encoding
import external.JsZip
import external.JsZipOption
import kotlinx.coroutines.await
import model.DEFAULT_METER_HIGH
import model.DEFAULT_METER_LOW
import model.ExportNotification
import model.ExportResult
import model.Format
import model.ImportWarning
import model.Note
import model.Project
import model.Tempo
import model.TimeSignature
import model.Track
import org.khronos.webgl.Uint8Array
import org.w3c.files.Blob
import org.w3c.files.File
import process.pitch.UtauNotePitchData
import process.pitch.UtauTrackPitchData
import process.pitch.pitchFromUtauTrack
import process.validateNotes
import util.encode
import util.getSafeFileName
import util.linesNotBlank
import util.nameWithoutExtension
import util.padStartZero
import util.readBinary
import util.readText
import util.toFixed

object Ust {
    suspend fun parse(files: List<File>): Project {
        val results = files.map {
            parseFile(it)
        }
        val projectName = results
            .mapNotNull { it.projectName }
            .firstOrNull()
            ?: files.first().nameWithoutExtension
        val tracks = results.mapIndexed { index, result ->
            Track(
                id = index,
                name = result.file.nameWithoutExtension,
                notes = result.notes,
                pitch = pitchFromUtauTrack(result.pitchData, result.notes)
            ).validateNotes()
        }
        val warnings = mutableListOf<ImportWarning>()
        val tempos = results.firstOrNull { it.tempos.isNotEmpty() }?.tempos.let {
            if (it == null || it.isEmpty()) {
                warnings.add(ImportWarning.TempoNotFound)
            }
            it ?: listOf(Tempo.default)
        }
        warnings.addAll(results.flatMap { result ->
            val ignoredTempos = result.tempos - tempos
            ignoredTempos.map { ImportWarning.TempoIgnoredInFile(result.file, it) }
        })
        return Project(
            format = Format.UST,
            inputFiles = files,
            name = projectName,
            tracks = tracks,
            measurePrefix = 0,
            timeSignatures = listOf(TimeSignature.default),
            tempos = tempos,
            importWarnings = warnings
        )
    }

    private suspend fun readFileContent(file: File): String {
        val binary = file.readBinary()
        val encoding = Encoding.detect(binary)
        return if (encoding == "UTF8") {
            file.readText()
        } else {
            file.readText("shift-jis")
        }
    }

    private suspend fun parseFile(file: File): FileParseResult {
        val lines = readFileContent(file).linesNotBlank()
        var projectName: String? = null
        val notes = mutableListOf<Note>()
        val notePitchDataList = mutableListOf<UtauNotePitchData>()
        val tempos = mutableListOf<Tempo>()
        var isHeader = true
        var time = 0L
        var pendingNoteKey: Int? = null
        var pendingNoteLyric: String? = null
        var pendingNoteTickOn: Long? = null
        var pendingNoteTickOff: Long? = null
        var pendingBpm: Double? = null
        var pendingPBS: Pair<Double, Double>? = null
        var pendingPBW: List<Double>? = null
        var pendingPBY: List<Double>? = null
        var pendingPBM: List<String>? = null
        for (line in lines) {
            line.tryGetValue("ProjectName")?.let {
                projectName = it
            }
            line.tryGetValue("Tempo")?.let {
                val bpm = it.toDoubleOrNull() ?: return@let
                if (isHeader) {
                    tempos.add(Tempo(0, bpm))
                } else {
                    val tick = pendingNoteTickOn
                    if (tick != null) {
                        tempos.add(Tempo(tick, bpm))
                    } else {
                        pendingBpm = bpm
                    }
                }
            }
            if (line.contains("[#0000]")) {
                isHeader = false
            }
            if (line.contains("[#")) {
                if (pendingNoteKey != null &&
                    pendingNoteLyric != null &&
                    pendingNoteTickOn != null &&
                    pendingNoteTickOff != null
                ) {
                    notes.add(
                        Note(
                            id = notes.size,
                            key = pendingNoteKey,
                            lyric = pendingNoteLyric,
                            tickOn = pendingNoteTickOn,
                            tickOff = pendingNoteTickOff
                        )
                    )
                    notePitchDataList.add(
                        UtauNotePitchData(
                            bpm = tempos.last().bpm,
                            start = pendingPBS?.first ?: 0.0,
                            startShift = pendingPBS?.second ?: 0.0,
                            widths = pendingPBW.orEmpty(),
                            shifts = pendingPBY.orEmpty(),
                            curveTypes = pendingPBM.orEmpty()
                        )
                    )
                }
                pendingNoteKey = null
                pendingNoteLyric = null
                pendingNoteTickOn = null
                pendingNoteTickOff = null
                pendingPBS = null
                pendingPBW = null
                pendingPBY = null
                pendingPBM = null
            }
            line.tryGetValue("Length")?.let {
                val length = it.toLongOrNull() ?: return@let
                pendingNoteTickOn = time
                pendingBpm?.let { bpm ->
                    tempos.add(Tempo(time, bpm))
                    pendingBpm = null
                }
                time += length
                pendingNoteTickOff = time
            }
            line.tryGetValue("Lyric")?.let {
                val validLyric = it.takeIf { it != "R" && it != "r" } ?: return@let
                pendingNoteLyric = validLyric
            }
            line.tryGetValue("NoteNum")?.let {
                val key = it.toIntOrNull() ?: return@let
                pendingNoteKey = key
            }
            line.tryGetValue("PBS")?.let {
                val cells = it.split(';')
                val start = cells[0].toDoubleOrNull() ?: return@let
                val startShift = cells.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                pendingPBS = start to startShift
            }
            line.tryGetValue("PBW")?.let {
                pendingPBW = it.split(',').map { width -> width.toDoubleOrNull() ?: 0.0 }
            }
            line.tryGetValue("PBY")?.let {
                pendingPBY = it.split(',').map { shift -> shift.toDoubleOrNull() ?: 0.0 }
            }
            line.tryGetValue("PBM")?.let {
                pendingPBM = it.split(',')
            }
        }
        val pitchData = notePitchDataList.ifEmpty { null }?.let { UtauTrackPitchData(it) }
        return FileParseResult(file, projectName, notes, tempos, pitchData)
    }

    private data class FileParseResult(
        val file: File,
        val projectName: String?,
        val notes: List<Note>,
        val tempos: List<Tempo>,
        val pitchData: UtauTrackPitchData?
    )

    suspend fun generate(project: Project): ExportResult {
        val zip = JsZip()
        for (track in project.tracks) {
            val content = generateTrackContent(project, track)
            val contentEncodedArray = content.encode("SJIS")
            val trackNameUrlSafe = getSafeFileName(track.name)
            val trackFileName = "${project.name}_${track.id + 1}_$trackNameUrlSafe${Format.UST.extension}"
            zip.file(trackFileName, Uint8Array(contentEncodedArray))
        }
        val option = JsZipOption().also { it.type = "blob" }
        val blob = zip.generateAsync(option).await() as Blob
        val name = project.name + ".zip"
        val notifications = mutableListOf<ExportNotification>()
        if (project.tempos.distinctBy { it.bpm }.count() > 1) {
            notifications.add(ExportNotification.TempoChangeIgnored)
        }
        if (project.timeSignatures.any { it.numerator != DEFAULT_METER_HIGH || it.denominator != DEFAULT_METER_LOW }) {
            notifications.add(ExportNotification.TimeSignatureIgnored)
        }
        return ExportResult(blob, name, notifications)
    }

    private fun generateTrackContent(project: Project, track: Track): String {
        val builder = object {
            var content = ""
                private set

            fun appendLine(line: String) {
                content += "$line$LINE_SEPARATOR"
            }
        }
        builder.appendLine("[#VERSION]")
        builder.appendLine("UST Version1.2")
        builder.appendLine("[#SETTING]")
        val bpm = project.tempos.first().bpm.toFixed(2)
        builder.appendLine("Tempo=$bpm")
        builder.appendLine("Tracks=1")
        builder.appendLine("ProjectName=${track.name}")
        builder.appendLine("Mode2=True")
        var tickPos = 0L
        var restCount = 0
        for (note in track.notes) {
            if (tickPos < note.tickOn) {
                val restNoteNumber = (note.id + restCount).padStartZero(4)
                builder.appendLine("[#$restNoteNumber]")
                builder.appendLine("Length=${note.tickOn - tickPos}")
                builder.appendLine("Lyric=R")
                builder.appendLine("NoteNum=60")
                builder.appendLine("PreUtterance=")
                restCount++
            }
            val noteNumber = (note.id + restCount).padStartZero(4)
            builder.appendLine("[#$noteNumber]")
            builder.appendLine("Length=${note.length}")
            builder.appendLine("Lyric=${note.lyric}")
            builder.appendLine("NoteNum=${note.key}")
            builder.appendLine("PreUtterance=60")
            tickPos = note.tickOff
        }
        builder.appendLine("[#TRACKEND]")
        return builder.content
    }

    private fun String.tryGetValue(key: String): String? {
        if (!startsWith("$key=")) return null
        val index = indexOf("=").takeIf { it in 0 until lastIndex } ?: return null
        return substring(index + 1).takeIf { it.isNotBlank() }
    }

    private const val LINE_SEPARATOR = "\r\n"
}
