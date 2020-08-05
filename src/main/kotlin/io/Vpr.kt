package io

import external.JsZip
import external.JsZipOption
import external.Resources
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import model.DEFAULT_LYRIC
import model.ExportNotification
import model.ExportResult
import model.Format
import model.ImportWarning
import model.TickCounter
import model.TimeSignature
import org.w3c.files.Blob
import org.w3c.files.File
import util.nameWithoutExtension
import util.readBinary

object Vpr {
    suspend fun parse(file: File): model.Project {
        val content = readContent(file)
        val warnings = mutableListOf<ImportWarning>()
        val tracks = content.tracks.mapIndexed { index, track ->
            parseTrack(track, index)
        }
        val timeSignatures = content.masterTrack?.timeSig?.events?.map {
            TimeSignature(
                measurePosition = it.bar,
                numerator = it.numer,
                denominator = it.denom
            )
        }?.takeIf { it.isNotEmpty() } ?: listOf(TimeSignature.default).also {
            warnings.add(ImportWarning.TimeSignatureNotFound)
        }
        val tempos = content.masterTrack?.tempo?.events?.map {
            model.Tempo(
                tickPosition = it.pos,
                bpm = it.value.toDouble() / BPM_RATE
            )
        }?.takeIf { it.isNotEmpty() } ?: listOf(model.Tempo.default).also {
            warnings.add(ImportWarning.TempoNotFound)
        }
        return model.Project(
            format = Format.VPR,
            inputFiles = listOf(file),
            name = content.title ?: file.nameWithoutExtension,
            tracks = tracks,
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = 0,
            importWarnings = warnings
        )
    }

    private fun parseTrack(track: Track, trackIndex: Int): model.Track {
        val notes = track.parts
            .flatMap { part -> part.notes.map { part.pos to it } }
            .mapIndexed { index, (tickOffset, note) ->
                model.Note(
                    id = index,
                    tickOn = tickOffset + note.pos,
                    tickOff = tickOffset + note.pos + note.duration,
                    lyric = note.lyric.takeUnless { it.isNullOrBlank() } ?: DEFAULT_LYRIC,
                    key = note.number
                )
            }

        return model.Track(
            id = trackIndex,
            name = track.name ?: "Track ${trackIndex + 1}",
            notes = notes
        ).validateNotes()
    }

    private suspend fun readContent(file: File): Project {
        val binary = file.readBinary()
        val zip = JsZip().loadAsync(binary).await()
        val vprEntry = possibleJsonPaths.let {
            it.forEach { path ->
                val vprFile = zip.file(path)
                if (vprFile != null) return@let vprFile
            }
            null
        }
        val text = requireNotNull(vprEntry).async("string").await() as String
        return jsonSerializer.parse(Project.serializer(), text)
    }

    suspend fun generate(project: model.Project): ExportResult {
        val jsonText = generateContent(project)
        val zip = JsZip()
        zip.file(possibleJsonPaths.first(), jsonText)
        val option = JsZipOption().also {
            it.type = "blob"
            it.mimeType = "application/octet-stream"
        }
        val blob = zip.generateAsync(option).await() as Blob
        val name = project.name + Format.VPR.extension
        return ExportResult(blob, name, listOf(ExportNotification.PhonemeResetRequiredV5))
    }

    private fun generateContent(project: model.Project): String {
        val template = Resources.vprTemplate
        val vpr = jsonSerializer.parse(Project.serializer(), template)
        var endTick = 0L
        vpr.title = project.name
        val tickCounter = TickCounter()
        val timeSigEvents = project.timeSignatures.map {
            tickCounter.goToMeasure(it)
            TimeSigEvent(bar = it.measurePosition, denom = it.denominator, numer = it.numerator)
        }
        vpr.masterTrack!!.timeSig!!.events = timeSigEvents
        endTick = endTick.coerceAtLeast(tickCounter.outputTick)
        val tempoEvents = project.tempos.map {
            TempoEvent(pos = it.tickPosition, value = (it.bpm * BPM_RATE).toInt())
        }
        vpr.masterTrack!!.tempo!!.events = tempoEvents
        endTick = endTick.coerceAtLeast(tempoEvents.map { it.pos }.max() ?: 0)
        val emptyTrack = vpr.tracks.first()
        val emptyNote = emptyTrack.parts.first().notes.first()
        val tracks = project.tracks.map { track ->
            val notes = track.notes.map {
                emptyNote.copy(
                    pos = it.tickOn,
                    duration = it.length,
                    number = it.key,
                    lyric = it.lyric
                )
            }
            val duration = track.notes.lastOrNull()?.tickOff
            val part = duration?.let { emptyTrack.parts.first().copy(duration = it, notes = notes) }
            emptyTrack.copy(
                name = track.name,
                parts = listOfNotNull(part)
            )
        }
        vpr.tracks = tracks
        endTick = endTick.coerceAtLeast(tracks.map { it.parts.firstOrNull()?.duration ?: 0 }.max() ?: 0)
        vpr.masterTrack!!.loop!!.end = endTick
        return jsonSerializer.stringify(Project.serializer(), vpr)
    }

    private val jsonSerializer = Json(
        JsonConfiguration.Stable.copy(
            isLenient = true,
            ignoreUnknownKeys = true
        )
    )

    private const val BPM_RATE = 100.0
    private val possibleJsonPaths = listOf(
        "Project\\sequence.json",
        "Project/sequence.json"
    )

    @Serializable
    private data class Project(
        var masterTrack: MasterTrack? = null,
        var title: String? = null,
        var tracks: List<Track> = listOf(),
        var vender: String? = null,
        var version: Version? = null,
        var voices: List<Voice>? = null
    )

    @Serializable
    private data class MasterTrack(
        var loop: Loop? = null,
        var samplingRate: Int? = null,
        var tempo: Tempo? = null,
        var timeSig: TimeSig? = null,
        var volume: MasterVolume? = null
    )

    @Serializable
    private data class Tempo(
        var events: List<TempoEvent> = listOf(),
        var global: Global? = null,
        var height: Double? = null,
        var isFolded: Boolean? = null
    )

    @Serializable
    private data class TempoEvent(
        var pos: Long,
        var value: Int
    )

    @Serializable
    private data class Global(
        var isEnabled: Boolean? = null,
        var varue: Int? = null
    )

    @Serializable
    private data class TimeSig(
        var events: List<TimeSigEvent> = listOf(),
        var isFolded: Boolean? = null
    )

    @Serializable
    private data class TimeSigEvent(
        var bar: Int,
        var denom: Int,
        var numer: Int
    )

    @Serializable
    private data class MasterVolume(
        var events: List<MasterVolumeEvent>? = null,
        var height: Double? = null,
        var isFolded: Boolean? = null
    )

    @Serializable
    private data class MasterVolumeEvent(
        var pos: Long? = null,
        var value: Int? = null
    )

    @Serializable
    private data class Track(
        var busNo: Int? = null,
        var color: Int? = null,
        var height: Double? = null,
        var isFolded: Boolean? = null,
        var isMuted: Boolean? = null,
        var isSoloMode: Boolean? = null,
        var name: String? = null,
        var panpot: Panpot? = null,
        var parts: List<Part> = listOf(),
        var type: Int? = null,
        var volume: Volume? = null
    )

    @Serializable
    private data class Panpot(
        var events: List<PanpotEvent>? = null,
        var height: Double? = null,
        var isFolded: Boolean? = null
    )

    @Serializable
    private data class PanpotEvent(
        var pos: Long? = null,
        var value: Int? = null
    )

    @Serializable
    private data class Version(
        var major: Int? = null,
        var minor: Int? = null,
        var revision: Int? = null
    )

    @Serializable
    private data class Voice(
        var compID: String? = null,
        var name: String? = null
    )

    @Serializable
    private data class Loop(
        var begin: Long? = null,
        var end: Long? = null,
        var isEnabled: Boolean? = null
    )

    @Serializable
    private data class Part(
        var duration: Long = 0L,
        var midiEffects: List<MidiEffect>? = null,
        var notes: List<Note> = listOf(),
        var pos: Long,
        var styleName: String? = null,
        var voice: PartVoice? = null
    )

    @Serializable
    private data class MidiEffect(
        var id: String? = null,
        var isBypassed: Boolean? = null,
        var isFolded: Boolean? = null,
        var parameters: List<Parameter>? = null
    )

    @Serializable
    private data class Parameter(
        var name: String? = null,
        var value: String? = null
    )

    @Serializable
    private data class Note(
        var duration: Long = 0L,
        var exp: Exp? = null,
        var isProtected: Boolean? = null,
        var lyric: String? = null,
        var number: Int,
        var phoneme: String? = null,
        var pos: Long,
        var singingSkill: SingingSkill? = null,
        var velocity: Int? = null,
        var vibrato: Vibrato? = null
    )

    @Serializable
    private data class Exp(
        var opening: Int? = null
    )

    @Serializable
    private data class SingingSkill(
        var duration: Int? = null,
        var weight: Weight? = null
    )

    @Serializable
    private data class Weight(
        var post: Int? = null,
        var pre: Int? = null
    )

    @Serializable
    private data class Vibrato(
        var duration: Int? = null,
        var type: Int? = null
    )

    @Serializable
    private data class PartVoice(
        var compID: String? = null,
        var langID: Int? = null
    )

    @Serializable
    private data class Volume(
        var events: List<VolumeEvent>? = null,
        var height: Double? = null,
        var isFolded: Boolean? = null
    )

    @Serializable
    private data class VolumeEvent(
        var pos: Int? = null,
        var value: Int? = null
    )
}
