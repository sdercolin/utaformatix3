package io

import exception.UnsupportedLegacyPpsfError
import external.JsZip
import kotlinx.coroutines.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import model.DEFAULT_LYRIC
import model.Format
import model.ImportWarning
import model.Tempo
import model.TimeSignature
import org.w3c.files.File
import process.validateNotes
import util.nameWithoutExtension
import util.readBinary

object Ppsf {
    suspend fun parse(file: File): model.Project {
        val content = readContent(file)
        val warnings = mutableListOf<ImportWarning>()

        val name = content.ppsf.project.name?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension

        val timeSignatures = content.ppsf.project.meter.let { meter ->
            val first = TimeSignature(
                measurePosition = 0,
                numerator = meter.const.nume,
                denominator = meter.const.denomi,
            )
            if (!meter.useSequence) listOf(first)
            else {
                val sequence = meter.sequence.orEmpty().map { event ->
                    TimeSignature(
                        measurePosition = event.measure,
                        numerator = event.nume,
                        denominator = event.denomi,
                    )
                }
                if (sequence.none { it.measurePosition == 0 }) listOf(first) + sequence
                else sequence
            }
        }.takeIf { it.isNotEmpty() } ?: listOf(TimeSignature.default).also {
            warnings.add(ImportWarning.TimeSignatureNotFound)
        }

        val tempos = content.ppsf.project.tempo.let { tempos: Tempo ->
            val first = Tempo(
                tickPosition = 0,
                bpm = tempos.const.toDouble() / BPM_RATE,
            )
            if (!tempos.useSequence) listOf(first) else {
                val sequence = tempos.sequence.orEmpty().map { event ->
                    Tempo(
                        tickPosition = event.tick.toLong(),
                        bpm = event.value.toDouble() / BPM_RATE,
                    )
                }
                if (sequence.none { it.tickPosition == 0L }) listOf(first) + sequence
                else sequence
            }
        }.takeIf { it.isNotEmpty() } ?: listOf(model.Tempo.default).also {
            warnings.add(ImportWarning.TempoNotFound)
        }

        val tracks = content.ppsf.project.dvlTrack.mapIndexed { i, track -> parseTrack(i, track) }

        return model.Project(
            format = format,
            inputFiles = listOf(file),
            name = name,
            tracks = tracks,
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = 0,
            importWarnings = warnings,
        )
    }

    private fun parseTrack(index: Int, dvlTrack: DvlTrack): model.Track {
        val name = dvlTrack.name ?: "Track ${index + 1}"
        val notes = dvlTrack.events.filter { it.enabled != false }.map {
            model.Note(
                id = 0,
                key = it.noteNumber,
                lyric = it.lyric?.takeUnless { lyric -> lyric.isBlank() } ?: DEFAULT_LYRIC,
                tickOn = it.pos,
                tickOff = it.pos + it.length,
            )
        }
        return model.Track(
            id = index,
            name = name,
            notes = notes,
        ).validateNotes()
    }

    private suspend fun readContent(file: File): Project {
        val binary = file.readBinary()
        val zip = runCatching { JsZip().loadAsync(binary).await() }.getOrElse {
            throw UnsupportedLegacyPpsfError()
        }
        val vprEntry = zip.file(jsonPath)
        val text = requireNotNull(vprEntry).async("string").await() as String
        return jsonSerializer.decodeFromString(Project.serializer(), text)
    }

    private val jsonSerializer = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private const val BPM_RATE = 10000.0
    private const val jsonPath = "ppsf.json"

    @Serializable
    private data class Project(
        @SerialName("ppsf") val ppsf: Root,
    )

    @Serializable
    private data class Root(
        @SerialName("app_ver") val appVer: String,
        @SerialName("gui_settings") val guiSettings: JsonElement? = null,
        @SerialName("ppsf_ver") val ppsfVer: String,
        @SerialName("project") val project: InnerProject,
    )

    @Serializable
    private data class InnerProject(
        @SerialName("audio_track") val audioTrack: JsonElement? = null,
        @SerialName("block_size") val blockSize: JsonElement? = null,
        @SerialName("dvl_track") val dvlTrack: List<DvlTrack> = listOf(),
        @SerialName("loop_point") val loopPoint: JsonElement? = null,
        @SerialName("meter") val meter: Meter,
        @SerialName("metronome") val metronome: JsonElement? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("sampling_rate") val samplingRate: Int,
        @SerialName("singer_table") val singerTable: JsonElement? = null,
        @SerialName("tempo") val tempo: Tempo,
        @SerialName("vocaloid_track") val vocaloidTrack: JsonElement? = null,
    )

    @Serializable
    private data class DvlTrack(
        @SerialName("enabled") val enabled: Boolean? = null,
        @SerialName("events") val events: List<Event> = listOf(),
        @SerialName("mixer") val mixer: JsonElement? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("parameters") val parameters: JsonElement? = null,
        @SerialName("plugin_output_bus_index") val pluginOutputBusIndex: Int? = null,
        @SerialName("singer") val singer: JsonElement? = null,
    )

    @Serializable
    private data class Meter(
        @SerialName("const") val const: MeterConstValue,
        @SerialName("sequence") val sequence: List<MeterSequenceEvent>? = null,
        @SerialName("use_sequence") val useSequence: Boolean,
    )

    @Serializable
    private data class Tempo(
        @SerialName("const") val const: Int,
        @SerialName("sequence") val sequence: List<TempoSequenceEvent>? = null,
        @SerialName("use_sequence") val useSequence: Boolean,
    )

    @Serializable
    private data class Event(
        @SerialName("adjust_speed") val adjustSpeed: Boolean? = null,
        @SerialName("attack_speed_rate") val attackSpeedRate: JsonElement? = null,
        @SerialName("consonant_rate") val consonantRate: JsonElement? = null,
        @SerialName("consonant_speed_rate") val consonantSpeedRate: JsonElement? = null,
        @SerialName("enabled") val enabled: Boolean? = null,
        @SerialName("length") val length: Long,
        @SerialName("lyric") val lyric: String? = null,
        @SerialName("note_number") val noteNumber: Int,
        @SerialName("note_off_pit_envelope") val noteOffPitEnvelope: JsonElement? = null,
        @SerialName("note_on_pit_envelope") val noteOnPitEnvelope: JsonElement? = null,
        @SerialName("portamento_envelope") val portamentoEnvelope: JsonElement? = null,
        @SerialName("portamento_type") val portamentoType: JsonElement? = null,
        @SerialName("pos") val pos: Long,
        @SerialName("protected") val isProtected: Boolean? = null,
        @SerialName("release_speed_rate") val releaseSpeedRate: JsonElement? = null,
        @SerialName("symbols") val symbols: String? = null,
        @SerialName("vcl_like_note_off") val vclLikeNoteOff: JsonElement? = null,
    )

    @Serializable
    private data class MeterConstValue(
        @SerialName("denomi") val denomi: Int,
        @SerialName("nume") val nume: Int,
    )

    @Serializable
    private data class MeterSequenceEvent(
        @SerialName("denomi") val denomi: Int,
        @SerialName("nume") val nume: Int,
        @SerialName("measure") val measure: Int,
    )

    @Serializable
    private data class TempoSequenceEvent(
        @SerialName("curve_type") val curveType: Int? = null,
        @SerialName("tick") val tick: Int,
        @SerialName("value") val value: Int,
    )

    private val format = Format.Ppsf
}
