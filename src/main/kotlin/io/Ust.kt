package io

import external.Encoding
import external.JsZip
import external.JsZipOption
import kotlin.math.roundToLong
import kotlinx.coroutines.await
import model.DEFAULT_METER_HIGH
import model.DEFAULT_METER_LOW
import model.ExportNotification
import model.ExportResult
import model.Feature
import model.Format
import model.ImportParams
import model.ImportWarning
import model.Note
import model.Project
import model.Tempo
import model.TimeSignature
import model.Track
import org.khronos.webgl.Uint8Array
import org.w3c.files.Blob
import org.w3c.files.File
import process.pitch.UtauMode1NotePitchData
import process.pitch.UtauMode1TrackPitchData
import process.pitch.UtauMode2NotePitchData
import process.pitch.UtauMode2TrackPitchData
import process.pitch.pitchFromUtauMode1Track
import process.pitch.pitchFromUtauMode2Track
import process.pitch.pitchToUtauMode1Track
import process.pitch.pitchToUtauMode2Track
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
    suspend fun parse(files: List<File>, params: ImportParams): Project {
        val results = files.map {
            parseFile(it)
        }
        val projectName = results
            .mapNotNull { it.projectName }
            .firstOrNull()
            ?: files.first().nameWithoutExtension
        val tracks = results.mapIndexed { index, result ->
            val pitch = when {
                params.simpleImport -> null
                result.isMode2 -> pitchFromUtauMode2Track(
                    result.pitchDataMode2,
                    result.notes
                )
                else -> pitchFromUtauMode1Track(result.pitchDataMode1, result.notes)
            }
            Track(
                id = index,
                name = result.file.nameWithoutExtension,
                notes = result.notes,
                pitch = pitch
            ).validateNotes()
        }
        val warnings = mutableListOf<ImportWarning>()
        val tempos = results.firstOrNull { it.tempos.isNotEmpty() }?.tempos.let {
            if (it == null || it.isEmpty()) {
                warnings.add(ImportWarning.TempoNotFound)
            }
            it ?: listOf(Tempo.default)
        }
        warnings.addAll(
            results.flatMap { result ->
                val ignoredTempos = result.tempos - tempos
                ignoredTempos.map { ImportWarning.TempoIgnoredInFile(result.file, it) }
            }
        )
        return Project(
            format = Format.Ust,
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
        val notePitchDataListMode1 = mutableListOf<UtauMode1NotePitchData>()
        val notePitchDataListMode2 = mutableListOf<UtauMode2NotePitchData>()
        val tempos = mutableListOf<Tempo>()
        var isHeader = true
        var time = 0L
        var pendingNoteKey: Int? = null
        var pendingNoteLyric: String? = null
        var pendingNoteTickOn: Long? = null
        var pendingNoteTickOff: Long? = null
        var isMode2 = false
        var pendingBpm: Double? = null
        // Pitch field for Mode1
        var pendingPitchBend: List<Double>? = null
        // Pitch field for Mode2
        var pendingPBS: Pair<Double, Double>? = null
        var pendingPBW: List<Double>? = null
        var pendingPBY: List<Double>? = null
        var pendingPBM: List<String>? = null
        var pendingVBR: List<Double>? = null
        for (line in lines) {
            line.tryGetValue("ProjectName")?.let {
                projectName = it
            }
            line.tryGetValue("Tempo")?.let {
                // Some locales use ',' instead of '.' for tempo
                val bpm = it.replace(',', '.').toDoubleOrNull() ?: return@let
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
            if (line.contains("Mode2=True")) {
                isMode2 = true
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
                    notePitchDataListMode2.add(
                        UtauMode2NotePitchData(
                            bpm = tempos.last().bpm,
                            start = pendingPBS?.first ?: 0.0,
                            startShift = pendingPBS?.second ?: 0.0,
                            widths = pendingPBW.orEmpty(),
                            shifts = pendingPBY.orEmpty(),
                            curveTypes = pendingPBM.orEmpty(),
                            vibratoParams = pendingVBR
                        )
                    )
                    notePitchDataListMode1.add(
                        UtauMode1NotePitchData(
                            pendingPitchBend
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
                pendingVBR = null
                pendingPitchBend = null
            }
            line.tryGetValue("Length")?.let {
                val length = it.toDoubleOrNull()?.roundToLong() ?: return@let
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
            if (isMode2) {
                line.tryGetValue("PBS")?.let {
                    val cells = it.split(';', ',')
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
                line.tryGetValue("VBR")?.let {
                    pendingVBR = it.split(',').mapNotNull { cell -> cell.toDoubleOrNull() }
                }
            } else {
                // Parse Mode1 pitch data. As these fields would contain same data, if pendingNotePitches is not null,
                // which means one field has been parsed, we will skip other fields.
                line.tryGetValue("Piches")?.let {
                    if (pendingPitchBend != null) {
                        return@let
                    }
                    pendingPitchBend = parseMode1PitchData(it)
                }
                line.tryGetValue("Pitches")?.let {
                    if (pendingPitchBend != null) {
                        return@let
                    }
                    pendingPitchBend = parseMode1PitchData(it)
                }
                line.tryGetValue("PitchBend")?.let {
                    if (pendingPitchBend != null) {
                        return@let
                    }
                    pendingPitchBend = parseMode1PitchData(it)
                }
            }
        }
        val pitchDataMode1 = notePitchDataListMode1.ifEmpty { null }?.let { UtauMode1TrackPitchData(it) }
        val pitchDataMode2 = notePitchDataListMode2.ifEmpty { null }?.let { UtauMode2TrackPitchData(it) }
        return FileParseResult(file, projectName, notes, tempos, isMode2, pitchDataMode1, pitchDataMode2)
    }

    private fun parseMode1PitchData(
        pitchString: String
    ): List<Double> {
        return pitchString.split(",").map { pitchPointString ->
            pitchPointString.toDoubleOrNull() ?: 0.0
        }
    }

    private data class FileParseResult(
        val file: File,
        val projectName: String?,
        val notes: List<Note>,
        val tempos: List<Tempo>,
        val isMode2: Boolean,
        val pitchDataMode1: UtauMode1TrackPitchData?,
        val pitchDataMode2: UtauMode2TrackPitchData?
    )

    suspend fun generate(project: Project, features: List<Feature>): ExportResult {
        val zip = JsZip()
        for (track in project.tracks) {
            val content = generateTrackContent(project, track, features)
            val contentEncodedArray = content.encode("SJIS")
            val trackNameUrlSafe = getSafeFileName(track.name)
            val trackFileName = "${project.name}_${track.id + 1}_$trackNameUrlSafe${Format.Ust.extension}"
            zip.file(trackFileName, Uint8Array(contentEncodedArray))
        }
        val option = JsZipOption().also { it.type = "blob" }
        val blob = zip.generateAsync(option).await() as Blob
        val name = project.name + ".zip"
        val notifications = mutableListOf<ExportNotification>()
        // TODO: Maybe find a better way to handle multi tempo export in future
        if (project.tempos.distinctBy { it.bpm }.count() > 1) {
            notifications.add(ExportNotification.TempoChangeIgnored)
        }
        if (project.timeSignatures.any { it.numerator != DEFAULT_METER_HIGH || it.denominator != DEFAULT_METER_LOW }) {
            notifications.add(ExportNotification.TimeSignatureIgnored)
        }
        return ExportResult(blob, name, notifications)
    }

    private fun generateTrackContent(project: Project, track: Track, features: List<Feature>): String {
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
        val pitchDataMode1 = if (features.contains(Feature.ConvertPitch)) {
            pitchToUtauMode1Track(track.pitch, track.notes)
        } else null
        val pitchDataMode2 = if (features.contains(Feature.ConvertPitch)) {
            pitchToUtauMode2Track(track.pitch, track.notes, project.tempos)
        } else null
        for ((index, note) in track.notes.withIndex()) {
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
            builder.appendLine("PreUtterance=")

            if (features.contains(Feature.ConvertPitch)) {
                builder.appendLine("PBType=5")
                val pitchString = makeMode1PitchDataString(pitchDataMode1?.notes?.get(index))
                builder.appendLine("PitchBend=$pitchString")
                builder.appendLine("PBStart=0")

                val mode2Pitch = pitchDataMode2?.notes?.get(index)
                builder.appendLine("PBS=${mode2Pitch?.start}")
                // We insert startShift in PBW and PBY with width=1, as UTAU would just ignore it
                // Theoretically this would make all pit data moved behind by 1 tick, but hey, who can tell the difference...
                builder.appendLine("PBW=1,${mode2Pitch?.widths?.joinToString(",") { it.toString() }}")
                builder.appendLine("PBY=${mode2Pitch?.startShift},${
                    mode2Pitch?.shifts
                        ?.joinToString(",") { it.toString() }
                }"
                )
                builder.appendLine("PBM=${mode2Pitch?.curveTypes?.joinToString(",")}")
                if (mode2Pitch?.vibratoParams != null)
                    builder.appendLine("VBR=${mode2Pitch.vibratoParams.joinToString(",")}")
            }

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

    private fun makeMode1PitchDataString(notePitch: UtauMode1NotePitchData?): String? {
        return notePitch?.pitchPoints?.joinToString(separator = ",") { it.toInt().toString() }
    }

    const val MODE1_PITCH_SAMPLING_INTERVAL_TICK = 5L
    const val MODE2_PITCH_MAX_POINT_COUNT = 50L
    private const val LINE_SEPARATOR = "\r\n"
}
