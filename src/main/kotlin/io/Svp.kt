package io

import external.Resources
import external.generateUUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import model.DEFAULT_LYRIC
import model.ExportNotification
import model.ExportResult
import model.Feature
import model.Format
import model.ImportParams
import model.ImportWarning
import model.Pitch
import model.TimeSignature
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.File
import process.pitch.SvpDefaultVibratoParameters
import process.pitch.SvpNoteWithVibrato
import process.pitch.appendPitchPointsForSvpOutput
import process.pitch.getRelativeData
import process.pitch.processSvpInputPitchData
import process.validateNotes
import util.nameWithoutExtension
import util.readText
import kotlin.math.roundToLong

object Svp {
    suspend fun parse(file: File, params: ImportParams): model.Project {
        val text = file.readText().let {
            val index = it.lastIndexOf('}')
            it.take(index + 1)
        }
        val project = jsonSerializer.decodeFromString(Project.serializer(), text)
        val warnings = mutableListOf<ImportWarning>()
        val timeSignatures = project.time.meter?.map {
            TimeSignature(
                measurePosition = it.index,
                numerator = it.numerator,
                denominator = it.denominator,
            )
        }?.takeIf { it.isNotEmpty() } ?: listOf(TimeSignature.default).also {
            warnings.add(ImportWarning.TimeSignatureNotFound)
        }
        val tempos = project.time.tempo?.map {
            model.Tempo(
                tickPosition = it.position / TICK_RATE,
                bpm = it.bpm,
            )
        }?.takeIf { it.isNotEmpty() } ?: listOf(model.Tempo.default).also {
            warnings.add(ImportWarning.TempoNotFound)
        }
        val tracks = parseTracks(project, tempos, params)
        return model.Project(
            format = format,
            inputFiles = listOf(file),
            name = file.nameWithoutExtension,
            tracks = tracks,
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = 0,
            importWarnings = warnings,
        )
    }

    private fun parseTracks(
        project: Project,
        tempos: List<model.Tempo>,
        params: ImportParams,
    ): List<model.Track> = project.tracks.mapIndexed { index, track ->
        model.Track(
            id = index,
            name = track.name ?: "Track ${index + 1}",
            notes = parseNotes(track, project)
                // for some reasons, svp can have negative positioned notes inside
                .filter { it.tickOn >= 0 },
            pitch = if (params.simpleImport) null else parsePitch(track, project, tempos),
        ).validateNotes()
    }

    private fun parseNotes(track: Track, project: Project): List<model.Note> {
        val mainNotes = track.mainGroup?.let { group ->
            val ref = track.mainRef ?: return@let null
            parseNotesFromGroup(ref, group)
        }.orEmpty()
        val extraNotes = track.groups?.flatMap { ref ->
            project.library.find { it.uuid == ref.groupID }
                ?.let { group -> parseNotesFromGroup(ref, group) }
                .orEmpty()
        }.orEmpty()
        return mainNotes + extraNotes
    }

    private fun parseNotesFromGroup(ref: Ref, group: Group): List<model.Note> = group.notes.map { note ->
        val tickOn = (note.onset + ref.blickOffset) / TICK_RATE
        model.Note(
            id = 0,
            key = note.pitch + ref.pitchOffset,
            tickOn = tickOn,
            tickOff = tickOn + note.duration / TICK_RATE,
            lyric = note.lyrics.takeUnless { it.isNullOrBlank() } ?: DEFAULT_LYRIC,
        )
    }

    private fun parsePitch(track: Track, project: Project, tempos: List<model.Tempo>): Pitch? {
        val main = track.mainGroup?.let { group ->
            val ref = track.mainRef ?: return@let null
            parsePitchFromGroup(ref, group, tempos)
        }.orEmpty()
        val extras = track.groups?.flatMap { ref ->
            project.library.find { it.uuid == ref.groupID }
                ?.let { group -> parsePitchFromGroup(ref, group, tempos) }
                .orEmpty()
        }.orEmpty()
        val all = (main + extras).sortedBy { it.first }
        return Pitch(all, isAbsolute = false).takeIf { it.data.isNotEmpty() }
    }

    private fun parsePitchFromGroup(ref: Ref, group: Group, tempos: List<model.Tempo>): List<Pair<Long, Double>> {
        val vibratoDefaultParameters = ref.voice?.let {
            SvpDefaultVibratoParameters(
                vibratoStart = it.tF0VbrStart,
                easeInLength = it.tF0VbrLeft,
                easeOutLength = it.tF0VbrRight,
                depth = it.dF0Vbr,
                frequency = it.fF0Vbr,
            )
        }
        val pitchDelta = group.parameters?.pitchDelta
        val pitchMode = pitchDelta?.mode
        val pitchPoints = pitchDelta?.points.orEmpty()
            .asSequence()
            .chunked(2)
            .mapNotNull {
                val rawTick = it.getOrNull(0) ?: return@mapNotNull null
                val centValue = it.getOrNull(1) ?: return@mapNotNull null
                val tick = (rawTick + ref.blickOffset) / TICK_RATE
                val value = centValue / 100
                tick.roundToLong() to value
            }
            .toList()
        val vibratoEnv = group.parameters?.vibratoEnv
        val vibratoEnvMode = vibratoEnv?.mode
        val vibratoEnvPoints = vibratoEnv?.points.orEmpty()
            .asSequence()
            .withIndex()
            .groupBy { it.index / 2 }
            .map { it.value }
            .map { it.map { indexedValue -> indexedValue.value } }
            .mapNotNull {
                val rawTick = it.getOrNull(0) ?: return@mapNotNull null
                val value = it.getOrNull(1) ?: return@mapNotNull null
                val tick = (rawTick + ref.blickOffset) / TICK_RATE
                tick.roundToLong() to value
            }
            .toList()
        val notesWithVibrato = group.notes.map { note ->
            SvpNoteWithVibrato(
                noteStartTick = (note.onset + ref.blickOffset) / TICK_RATE,
                noteLengthTick = note.duration / TICK_RATE,
                vibratoStart = note.attributes?.tF0VbrStart,
                easeInLength = note.attributes?.tF0VbrLeft,
                easeOutLength = note.attributes?.tF0VbrRight,
                depth = note.attributes?.dF0Vbr,
                phase = note.attributes?.pF0Vbr,
                frequency = note.attributes?.fF0Vbr,
            )
        }
        return processSvpInputPitchData(
            pitchPoints,
            pitchMode,
            notesWithVibrato,
            tempos,
            vibratoEnvPoints,
            vibratoEnvMode,
            vibratoDefaultParameters,
        )
    }

    fun generate(project: model.Project, features: List<Feature>): ExportResult {
        val jsonText = generateContent(project, features)
        val blob = Blob(arrayOf(jsonText), BlobPropertyBag("application/octet-stream"))
        val name = format.getFileName(project.name)
        return ExportResult(
            blob,
            name,
            listOfNotNull(
                if (features.contains(Feature.ConvertPitch)) ExportNotification.PitchDataExported else null,
            ),
        )
    }

    private fun generateContent(project: model.Project, features: List<Feature>): String {
        val template = Resources.svpTemplate
        val svp = jsonSerializer.decodeFromString(Project.serializer(), template)
        svp.time.meter = project.timeSignatures.map {
            Meter(
                index = it.measurePosition,
                numerator = it.numerator,
                denominator = it.denominator,
            )
        }
        svp.time.tempo = project.tempos.map {
            Tempo(
                position = it.tickPosition * TICK_RATE,
                bpm = it.bpm,
            )
        }
        val emptyTrack = svp.tracks.first()
        svp.tracks = project.tracks.map {
            generateTrack(it, emptyTrack, features)
        }
        return jsonSerializer.encodeToString(Project.serializer(), svp)
    }

    private fun generateTrack(track: model.Track, emptyTrack: Track, features: List<Feature>): Track {
        val uuid = generateUUID()
        return emptyTrack.copy(
            name = track.name,
            mainGroup = emptyTrack.mainGroup!!.copy(
                uuid = uuid,
                notes = track.notes.map {
                    Note(
                        onset = it.tickOn * TICK_RATE,
                        duration = it.length * TICK_RATE,
                        lyrics = it.lyric,
                        phonemes = "",
                        pitch = it.key,
                        attributes = Attributes(),
                    )
                },
                parameters = emptyTrack.mainGroup!!.parameters!!.copy(
                    pitchDelta = generatePitchData(track, features) ?: emptyTrack.mainGroup!!.parameters!!.pitchDelta,
                ),
            ),
            mainRef = emptyTrack.mainRef!!.copy(
                groupID = uuid,
            ),
            dispOrder = track.id,
        )
    }

    private fun generatePitchData(track: model.Track, features: List<Feature>): PitchDelta? {
        if (!features.contains(Feature.ConvertPitch)) return null
        val data = track.pitch?.getRelativeData(track.notes)
            ?.appendPitchPointsForSvpOutput()
            ?.map { (it.first * TICK_RATE) to (it.second * 100) }
            ?: return null
        return PitchDelta(mode = "cosine", points = data.flatMap { listOf(it.first.toDouble(), it.second) })
    }

    private const val TICK_RATE = 1470000L

    private val jsonSerializer = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class Project(
        var library: List<Group> = listOf(),
        var renderConfig: RenderConfig? = null,
        var time: Time,
        var tracks: List<Track> = listOf(),
        var version: Int? = null,
    )

    @Serializable
    private data class RenderConfig(
        var aspirationFormat: String? = null,
        var bitDepth: Int? = null,
        var destination: String? = null,
        var exportMixDown: Boolean? = null,
        var filename: String? = null,
        var numChannels: Int? = null,
        var sampleRate: Int? = null,
    )

    @Serializable
    private data class Time(
        var meter: List<Meter>? = null,
        var tempo: List<Tempo>? = null,
    )

    @Serializable
    private data class Track(
        var dispColor: String? = null,
        var dispOrder: Int? = null,
        var groups: List<Ref>? = null,
        var mainGroup: Group? = null,
        var mainRef: Ref? = null,
        var mixer: JsonElement? = null,
        var name: String? = null,
        var renderEnabled: Boolean? = null,
    )

    @Serializable
    private data class Meter(
        var denominator: Int,
        var index: Int,
        var numerator: Int,
    )

    @Serializable
    private data class Tempo(
        var bpm: Double,
        var position: Long,
    )

    @Serializable
    private data class Group(
        var name: String? = null,
        var notes: List<Note> = listOf(),
        var parameters: Parameters? = null,
        var uuid: String,
    )

    @Serializable
    private data class Ref(
        var audio: JsonElement? = null,
        var blickOffset: Long = 0,
        var database: JsonElement? = null,
        var dictionary: String? = null,
        var voice: Voice? = null,
        var groupID: String,
        var isInstrumental: Boolean? = null,
        var pitchOffset: Int = 0,
    )

    @Serializable
    private data class Note(
        var attributes: Attributes? = null,
        var duration: Long,
        var lyrics: String? = null,
        var onset: Long,
        var phonemes: String? = null,
        var pitch: Int,
    )

    @Serializable
    private data class Parameters(
        var breathiness: JsonElement? = null,
        var gender: JsonElement? = null,
        var loudness: JsonElement? = null,
        var pitchDelta: PitchDelta? = null,
        var tension: JsonElement? = null,
        var vibratoEnv: VibratoEnv? = null,
        var voicing: JsonElement? = null,
    )

    @Serializable
    private data class Attributes(
        var tF0VbrStart: Double? = null,
        var tF0VbrLeft: Double? = null,
        var tF0VbrRight: Double? = null,
        var dF0Vbr: Double? = null,
        var pF0Vbr: Double? = null,
        var fF0Vbr: Double? = null,
    )

    @Serializable
    private data class PitchDelta(
        var mode: String? = null,
        var points: List<Double>? = null,
    )

    @Serializable
    private data class VibratoEnv(
        var mode: String? = null,
        var points: List<Double>? = null,
    )

    @Serializable
    private data class Voice(
        var tF0Left: JsonElement? = null,
        var tF0Right: JsonElement? = null,
        var dF0Left: JsonElement? = null,
        var dF0Right: JsonElement? = null,
        var tF0VbrStart: Double? = null,
        var tF0VbrLeft: Double? = null,
        var tF0VbrRight: Double? = null,
        var dF0Vbr: Double? = null,
        var fF0Vbr: Double? = null,
        var paramLoudness: JsonElement? = null,
        var paramTension: JsonElement? = null,
        var paramBreathiness: JsonElement? = null,
        var paramGender: JsonElement? = null,
        var paramToneShift: JsonElement? = null,
        var renderMode: JsonElement? = null,
    )

    private val format = Format.Svp
}
