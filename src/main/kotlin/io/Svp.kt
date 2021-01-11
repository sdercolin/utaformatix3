package io

import external.Resources
import external.generateUUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import model.DEFAULT_LYRIC
import model.ExportResult
import model.Feature
import model.Format
import model.ImportWarning
import model.Pitch
import model.TimeSignature
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.File
import process.pitch.appendPitchPointsForSvpOutput
import process.pitch.getRelativeData
import process.pitch.processSvpInputPitchData
import process.validateNotes
import util.nameWithoutExtension
import util.readText
import kotlin.math.roundToLong

object Svp {
    suspend fun parse(file: File): model.Project {
        val text = file.readText().let {
            val index = it.lastIndexOf('}')
            it.take(index + 1)
        }
        val project = jsonSerializer.parse(Project.serializer(), text)
        val warnings = mutableListOf<ImportWarning>()
        val timeSignatures = project.time.meter?.map {
            TimeSignature(
                measurePosition = it.index,
                numerator = it.numerator,
                denominator = it.denominator
            )
        }?.takeIf { it.isNotEmpty() } ?: listOf(TimeSignature.default).also {
            warnings.add(ImportWarning.TimeSignatureNotFound)
        }
        val tempos = project.time.tempo?.map {
            model.Tempo(
                tickPosition = it.position / TICK_RATE,
                bpm = it.bpm
            )
        }?.takeIf { it.isNotEmpty() } ?: listOf(model.Tempo.default).also {
            warnings.add(ImportWarning.TempoNotFound)
        }
        val tracks = parseTracks(project)
        return model.Project(
            format = Format.SVP,
            inputFiles = listOf(file),
            name = file.nameWithoutExtension,
            tracks = tracks,
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = 0,
            importWarnings = warnings
        )
    }

    private fun parseTracks(project: Project): List<model.Track> = project.tracks.mapIndexed { index, track ->
        model.Track(
            id = index,
            name = track.name ?: "Track ${index + 1}",
            notes = parseNotes(track, project),
            pitch = parsePitch(track, project)
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
            lyric = note.lyrics.takeUnless { it.isNullOrBlank() } ?: DEFAULT_LYRIC
        )
    }

    private fun parsePitch(track: Track, project: Project): Pitch? {
        val main = track.mainGroup?.let { group ->
            val ref = track.mainRef ?: return@let null
            parsePitchFromGroup(ref, group)
        }.orEmpty()
        val extras = track.groups?.flatMap { ref ->
            project.library.find { it.uuid == ref.groupID }
                ?.let { group -> parsePitchFromGroup(ref, group) }
                .orEmpty()
        }.orEmpty()
        val all = (main + extras).sortedBy { it.first }
        return Pitch(all, isAbsolute = false).takeIf { it.data.isNotEmpty() }
    }

    private fun parsePitchFromGroup(ref: Ref, group: Group): List<Pair<Long, Double>> {
        val pitchDelta = group.parameters?.pitchDelta ?: return emptyList()
        val mode = pitchDelta.mode ?: return emptyList()
        val points = pitchDelta.points ?: return emptyList()
        val convertedPoints = points.asSequence()
            .withIndex()
            .groupBy { it.index / 2 }
            .map { it.value }
            .map { it.map { indexedValue -> indexedValue.value } }
            .mapNotNull {
                val rawTick = it.getOrNull(0) ?: return@mapNotNull null
                val centValue = it.getOrNull(1) ?: return@mapNotNull null
                val tick = (rawTick + ref.blickOffset) / TICK_RATE
                val value = centValue / 100
                tick.roundToLong() to value
            }
            .toList()
        return processSvpInputPitchData(convertedPoints, mode)
    }

    fun generate(project: model.Project, features: List<Feature>): ExportResult {
        val jsonText = generateContent(project, features)
        val blob = Blob(arrayOf(jsonText), BlobPropertyBag("application/octet-stream"))
        val name = project.name + Format.SVP.extension
        return ExportResult(blob, name, listOf())
    }

    private fun generateContent(project: model.Project, features: List<Feature>): String {
        val template = Resources.svpTemplate
        val svp = jsonSerializer.parse(Project.serializer(), template)
        svp.time.meter = project.timeSignatures.map {
            Meter(
                index = it.measurePosition,
                numerator = it.numerator,
                denominator = it.denominator
            )
        }
        svp.time.tempo = project.tempos.map {
            Tempo(
                position = it.tickPosition * TICK_RATE,
                bpm = it.bpm
            )
        }
        val emptyTrack = svp.tracks.first()
        svp.tracks = project.tracks.map {
            generateTrack(it, emptyTrack, features)
        }
        return jsonSerializer.stringify(Project.serializer(), svp)
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
                        attributes = Attributes()
                    )
                },
                parameters = emptyTrack.mainGroup!!.parameters!!.copy(
                    pitchDelta = generatePitchData(track, features) ?: emptyTrack.mainGroup!!.parameters!!.pitchDelta
                )
            ),
            mainRef = emptyTrack.mainRef!!.copy(
                groupID = uuid
            )
        )
    }

    private fun generatePitchData(track: model.Track, features: List<Feature>): PitchDelta? {
        if (!features.contains(Feature.CONVERT_PITCH)) return null
        val data = track.pitch?.getRelativeData(track.notes)
            ?.let(::appendPitchPointsForSvpOutput)
            ?.map { (it.first * TICK_RATE) to (it.second * 100) }
            ?: return null
        return PitchDelta(mode = "cosine", points = data.flatMap { listOf(it.first.toDouble(), it.second) })
    }

    private const val TICK_RATE = 1470000L

    private val jsonSerializer = Json(
        JsonConfiguration.Stable.copy(
            isLenient = true,
            ignoreUnknownKeys = true
        )
    )

    @Serializable
    private data class Project(
        var library: List<Group> = listOf(),
        var renderConfig: RenderConfig? = null,
        var time: Time,
        var tracks: List<Track> = listOf(),
        var version: Int? = null
    )

    @Serializable
    private data class RenderConfig(
        var aspirationFormat: String? = null,
        var bitDepth: Int? = null,
        var destination: String? = null,
        var exportMixDown: Boolean? = null,
        var filename: String? = null,
        var numChannels: Int? = null,
        var sampleRate: Int? = null
    )

    @Serializable
    private data class Time(
        var meter: List<Meter>? = null,
        var tempo: List<Tempo>? = null
    )

    @Serializable
    private data class Track(
        var dispColor: String? = null,
        var dispOrder: Int? = null,
        var groups: List<Ref>? = null,
        var mainGroup: Group? = null,
        var mainRef: Ref? = null,
        var mixer: Mixer? = null,
        var name: String? = null,
        var renderEnabled: Boolean? = null
    )

    @Serializable
    private data class Meter(
        var denominator: Int,
        var index: Int,
        var numerator: Int
    )

    @Serializable
    private data class Tempo(
        var bpm: Double,
        var position: Long
    )

    @Serializable
    private data class Group(
        var name: String? = null,
        var notes: List<Note> = listOf(),
        var parameters: Parameters? = null,
        var uuid: String
    )

    @Serializable
    private data class Ref(
        var audio: Audio? = null,
        var blickOffset: Long = 0,
        var database: Database? = null,
        var dictionary: String? = null,
        var groupID: String,
        var isInstrumental: Boolean? = null,
        var pitchOffset: Int = 0
    )

    @Serializable
    private data class Mixer(
        var display: Boolean? = null,
        var gainDecibel: Double? = null,
        var mute: Boolean? = null,
        var pan: Double? = null,
        var solo: Boolean? = null
    )

    @Serializable
    private data class Note(
        var attributes: Attributes? = null,
        var duration: Long,
        var lyrics: String? = null,
        var onset: Long,
        var phonemes: String? = null,
        var pitch: Int
    )

    @Serializable
    private data class Parameters(
        var breathiness: Breathiness? = null,
        var gender: Gender? = null,
        var loudness: Loudness? = null,
        var pitchDelta: PitchDelta? = null,
        var tension: Tension? = null,
        var vibratoEnv: VibratoEnv? = null,
        var voicing: Voicing? = null
    )

    @Serializable
    private class Attributes

    @Serializable
    private data class Breathiness(
        var mode: String? = null,
        var points: List<String>? = null
    )

    @Serializable
    private data class Gender(
        var mode: String? = null,
        var points: List<String>? = null
    )

    @Serializable
    private data class Loudness(
        var mode: String? = null,
        var points: List<String>? = null
    )

    @Serializable
    private data class PitchDelta(
        var mode: String? = null,
        var points: List<Double>? = null
    )

    @Serializable
    private data class Tension(
        var mode: String? = null,
        var points: List<String>? = null
    )

    @Serializable
    private data class VibratoEnv(
        var mode: String? = null,
        var points: List<String>? = null
    )

    @Serializable
    private data class Voicing(
        var mode: String? = null,
        var points: List<String>? = null
    )

    @Serializable
    private data class Audio(
        var duration: Double? = null,
        var filename: String? = null
    )

    @Serializable
    private data class Database(
        var language: String? = null,
        var name: String? = null,
        var phoneset: String? = null
    )

}
