package io

import external.JsZip
import external.JsZipOption
import external.Resources
import kotlinx.coroutines.await
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
import model.TickCounter
import model.TimeSignature
import org.w3c.files.Blob
import org.w3c.files.File
import process.pitch.VocaloidPartPitchData
import process.pitch.generateForVocaloid
import process.pitch.pitchFromVocaloidParts
import process.validateNotes
import util.nameWithoutExtension
import util.readBinary

object Vpr {
    suspend fun parse(file: File, params: ImportParams): model.Project {
        val content = readContent(file)
        val warnings = mutableListOf<ImportWarning>()
        val tracks = content.tracks.mapIndexed { index, track ->
            parseTrack(track, index, params)
        }
        val timeSignatures = content.masterTrack?.timeSig?.events?.map {
            TimeSignature(
                measurePosition = it.bar,
                numerator = it.numer,
                denominator = it.denom,
            )
        }?.takeIf { it.isNotEmpty() } ?: listOf(TimeSignature.default).also {
            warnings.add(ImportWarning.TimeSignatureNotFound)
        }
        val tempos = content.masterTrack?.tempo?.events?.map {
            model.Tempo(
                tickPosition = it.pos,
                bpm = it.value.toDouble() / BPM_RATE,
            )
        }?.takeIf { it.isNotEmpty() } ?: listOf(model.Tempo.default).also {
            warnings.add(ImportWarning.TempoNotFound)
        }
        return model.Project(
            format = format,
            inputFiles = listOf(file),
            name = content.title ?: file.nameWithoutExtension,
            tracks = tracks,
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = 0,
            importWarnings = warnings,
        )
    }

    private fun parseTrack(track: Track, trackIndex: Int, params: ImportParams): model.Track {
        val notes = track.parts
            .flatMap { part -> part.notes.map { part.pos to it } }
            .mapIndexed { index, (tickOffset, note) ->
                model.Note(
                    id = index,
                    tickOn = tickOffset + note.pos,
                    tickOff = tickOffset + note.pos + note.duration,
                    lyric = note.lyric.takeUnless { it.isNullOrBlank() } ?: DEFAULT_LYRIC,
                    key = note.number,
                    phoneme = note.phoneme,
                )
            }
        val pitch = if (params.simpleImport) null else parsePitchData(track)
        return model.Track(
            id = trackIndex,
            name = track.name ?: "Track ${trackIndex + 1}",
            notes = notes,
            pitch = pitch,
        ).validateNotes()
    }

    private fun parsePitchData(track: Track): Pitch? {
        val dataByParts = track.parts.map { part ->
            VocaloidPartPitchData(
                startPos = part.pos,
                pit = part.getControllerEvents(PITCH_BEND_NAME)
                    .map { VocaloidPartPitchData.Event.fromPair(it.pos to it.value.toInt()) },
                pbs = part.getControllerEvents(PITCH_BEND_SENSITIVITY_NAME)
                    .map { VocaloidPartPitchData.Event.fromPair(it.pos to it.value.toInt()) },
            )
        }
        return pitchFromVocaloidParts(dataByParts)
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
        return jsonSerializer.decodeFromString(Project.serializer(), text)
    }

    suspend fun generate(project: model.Project, features: List<Feature>): ExportResult {
        val jsonText = generateContent(project, features)
        val zip = JsZip()
        zip.file(possibleJsonPaths.first(), jsonText)
        val option = JsZipOption().also {
            it.type = "blob"
            it.mimeType = "application/octet-stream"
        }
        val blob = zip.generateAsync(option).await() as Blob
        val name = format.getFileName(project.name)
        return ExportResult(
            blob,
            name,
            listOfNotNull(
                if (project.hasXSampaData) null else ExportNotification.PhonemeResetRequiredV5,
                if (features.contains(Feature.ConvertPitch)) ExportNotification.PitchDataExported else null,
            ),
        )
    }

    private fun generateContent(project: model.Project, features: List<Feature>): String {
        val template = Resources.vprTemplate
        val vpr = jsonSerializer.decodeFromString(Project.serializer(), template)
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
        endTick = endTick.coerceAtLeast(tempoEvents.maxOfOrNull { it.pos } ?: 0)
        val emptyTrack = vpr.tracks.first()
        val emptyNote = emptyTrack.parts.first().notes.first()
        val tracks = project.tracks.map { track ->
            val notes = track.notes.map {
                emptyNote.copy(
                    pos = it.tickOn,
                    duration = it.length,
                    number = it.key,
                    lyric = it.lyric,
                    phoneme = it.phoneme ?: emptyNote.phoneme,
                )
            }
            val duration = track.notes.lastOrNull()?.tickOff
            val controllers = if (features.contains(Feature.ConvertPitch)) generatePitchData(track) else null
            val part = duration?.let {
                emptyTrack.parts.first().copy(
                    duration = it,
                    notes = notes,
                    controllers = controllers,
                )
            }
            emptyTrack.copy(
                name = track.name,
                parts = listOfNotNull(part),
            )
        }
        vpr.tracks = tracks
        endTick = endTick.coerceAtLeast(tracks.maxOfOrNull { it.parts.firstOrNull()?.duration ?: 0 } ?: 0)
        vpr.masterTrack!!.loop!!.end = endTick
        return jsonSerializer.encodeToString(Project.serializer(), vpr)
    }

    private fun generatePitchData(track: model.Track): List<Controller>? {
        val pitchRawData = track.pitch?.generateForVocaloid(track.notes) ?: return null
        val controllers = mutableListOf<Controller>()
        if (pitchRawData.pbs.isNotEmpty()) {
            controllers.add(
                Controller(
                    name = PITCH_BEND_SENSITIVITY_NAME,
                    events = pitchRawData.pbs.map { ControllerEvent(pos = it.pos, value = it.value.toLong()) },
                ),
            )
        }
        if (pitchRawData.pit.isNotEmpty()) {
            controllers.add(
                Controller(
                    name = PITCH_BEND_NAME,
                    events = pitchRawData.pit.map {
                        ControllerEvent(pos = it.pos, value = it.value.toLong())
                    },
                ),
            )
        }
        return controllers.takeIf { it.isNotEmpty() }
    }

    private val jsonSerializer = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private const val BPM_RATE = 100.0
    private val possibleJsonPaths = listOf(
        "Project\\sequence.json",
        "Project/sequence.json",
    )

    private const val PITCH_BEND_NAME = "pitchBend"
    private const val PITCH_BEND_SENSITIVITY_NAME = "pitchBendSens"

    @Serializable
    private data class Project(
        var masterTrack: MasterTrack? = null,
        var title: String? = null,
        var tracks: List<Track> = listOf(),
        var vender: String? = null,
        var version: Version? = null,
        var voices: List<Voice>? = null,
    )

    @Serializable
    private data class MasterTrack(
        var loop: Loop? = null,
        var samplingRate: Int? = null,
        var tempo: Tempo? = null,
        var timeSig: TimeSig? = null,
        var volume: JsonElement? = null,
    )

    @Serializable
    private data class Tempo(
        var events: List<TempoEvent> = listOf(),
        var global: JsonElement? = null,
        var height: Double? = null,
        var isFolded: Boolean? = null,
    )

    @Serializable
    private data class TempoEvent(
        var pos: Long,
        var value: Int,
    )

    @Serializable
    private data class TimeSig(
        var events: List<TimeSigEvent> = listOf(),
        var isFolded: Boolean? = null,
    )

    @Serializable
    private data class TimeSigEvent(
        var bar: Int,
        var denom: Int,
        var numer: Int,
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
        var panpot: JsonElement? = null,
        var parts: List<Part> = listOf(),
        var type: Int? = null,
        var volume: JsonElement? = null,
    )

    @Serializable
    private data class Version(
        var major: Int? = null,
        var minor: Int? = null,
        var revision: Int? = null,
    )

    @Serializable
    private data class Voice(
        var compID: String? = null,
        var name: String? = null,
    )

    @Serializable
    private data class Loop(
        var begin: Long? = null,
        var end: Long? = null,
        var isEnabled: Boolean? = null,
    )

    @Serializable
    private data class Part(
        var duration: Long = 0L,
        var midiEffects: JsonElement? = null,
        var notes: List<Note> = listOf(),
        var pos: Long,
        var styleName: String? = null,
        var voice: JsonElement? = null,
        var controllers: List<Controller>? = null,
    ) {
        fun getControllerEvents(name: String) = controllers?.find { it.name == name }?.events.orEmpty()
    }

    @Serializable
    private data class Controller(
        var name: String,
        var events: List<ControllerEvent> = listOf(),
    )

    @Serializable
    private data class ControllerEvent(
        var pos: Long,
        var value: Long,
    )

    @Serializable
    private data class Note(
        var duration: Long = 0L,
        var exp: JsonElement? = null,
        var isProtected: Boolean? = null,
        var lyric: String? = null,
        var number: Int,
        var phoneme: String? = null,
        var pos: Long,
        var singingSkill: JsonElement? = null,
        var velocity: Int? = null,
        var vibrato: JsonElement? = null,
    )

    private val format = Format.Vpr
}
