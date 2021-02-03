package io

import external.JsZip
import kotlinx.coroutines.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
                denominator = meter.const.denomi
            )
            if (!meter.useSequence) listOf(first)
            else {
                val sequence = meter.sequence.orEmpty().map { event ->
                    TimeSignature(
                        measurePosition = event.measure,
                        numerator = event.nume,
                        denominator = event.denomi
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
                bpm = tempos.const.toDouble() / BPM_RATE
            )
            if (!tempos.useSequence) listOf(first) else {
                val sequence = tempos.sequence.orEmpty().map { event ->
                    Tempo(
                        tickPosition = event.tick.toLong(),
                        bpm = event.value.toDouble() / BPM_RATE
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
            format = Format.PPSF,
            inputFiles = listOf(file),
            name = name,
            tracks = tracks,
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = 0,
            importWarnings = warnings
        )
    }

    private fun parseTrack(index: Int, dvlTrack: DvlTrack): model.Track {
        val name = dvlTrack.name ?: "Track ${index + 1}"
        val notes = dvlTrack.events.filter { it.enabled != false }.map {
            model.Note(
                id = 0,
                key = it.noteNumber,
                lyric = it.lyric?.takeUnless { lyric -> lyric.isBlank() } ?: DEFAULT_LYRIC,
                tickOn = it.pos.toLong(),
                tickOff = it.pos.toLong() + it.length.toLong()
            )
        }
        return model.Track(
            id = index,
            name = name,
            notes = notes
        ).validateNotes()
    }

    private suspend fun readContent(file: File): Project {
        val binary = file.readBinary()
        val zip = JsZip().loadAsync(binary).await()
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
        @SerialName("ppsf") val ppsf: Root
    )

    @Serializable
    private data class Root(
        @SerialName("app_ver") val appVer: String,
        @SerialName("gui_settings") val guiSettings: GuiSettings? = null,
        @SerialName("ppsf_ver") val ppsfVer: String,
        @SerialName("project") val project: InnerProject
    )

    @Serializable
    private data class GuiSettings(
        @SerialName("file-fullpath") val fileFullpath: String? = null,
        @SerialName("playback-position") val playbackPosition: Int? = null,
        @SerialName("project-length") val projectLength: Int? = null,
        @SerialName("track-editor") val trackEditor: TrackEditor? = null
    )

    @Serializable
    private data class InnerProject(
        @SerialName("audio_track") val audioTrack: List<AudioTrack> = listOf(),
        @SerialName("block_size") val blockSize: Int? = null,
        @SerialName("dvl_track") val dvlTrack: List<DvlTrack> = listOf(),
        @SerialName("loop_point") val loopPoint: LoopPoint? = null,
        @SerialName("meter") val meter: Meter,
        @SerialName("metronome") val metronome: Metronome? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("sampling_rate") val samplingRate: Int,
        @SerialName("singer_table") val singerTable: List<String> = listOf(), // unknown
        @SerialName("tempo") val tempo: Tempo,
        @SerialName("vocaloid_track") val vocaloidTrack: List<String> = listOf() // unknown
    )

    @Serializable
    private data class TrackEditor(
        @SerialName("event-tracks") val eventTracks: List<EventTrack> = listOf(),
        @SerialName("header-width") val headerWidth: Int? = null,
        @SerialName("horizontal-scale") val horizontalScale: Double? = null,
        @SerialName("horizontal-scroll") val horizontalScroll: Int? = null,
        @SerialName("tempo-track") val tempoTrack: TempoTrack? = null,
        @SerialName("height") val height: Int? = null,
        @SerialName("width") val width: Int? = null,
        @SerialName("x") val x: Int? = null,
        @SerialName("y") val y: Int? = null
    )

    @Serializable
    private data class EventTrack(
        @SerialName("curve-points") val curvePoints: List<CurvePoint> = listOf(),
        @SerialName("fsm-effects") val fsmEffects: List<FsmEffect> = listOf(),
        @SerialName("height") val height: Int? = null,
        @SerialName("index") val index: Int? = null,
        @SerialName("mute-solo") val muteSolo: Int? = null,
        @SerialName("notes") val notes: List<Note> = listOf(),
        @SerialName("regions") val regions: List<Region> = listOf(),
        @SerialName("sub-tracks") val subTracks: List<SubTrack> = listOf(),
        @SerialName("total-height") val totalHeight: Int? = null,
        @SerialName("track-type") val trackType: Int? = null,
        @SerialName("vertical-scale") val verticalScale: Double? = null,
        @SerialName("vertical-scroll") val verticalScroll: Int? = null
    )

    @Serializable
    private data class TempoTrack(
        @SerialName("height") val height: Int? = null,
        @SerialName("vertical-scale") val verticalScale: Double? = null,
        @SerialName("vertical-scroll") val verticalScroll: Int? = null
    )

    @Serializable
    private data class FsmEffect(
        @SerialName("parameters") val parameters: List<FsmEffectParameter> = listOf(),
        @SerialName("plugin-id") val pluginId: Int? = null,
        @SerialName("plugin-name") val pluginName: String? = null,
        @SerialName("power-state") val powerState: Boolean? = null,
        @SerialName("program-name") val programName: String? = null,
        @SerialName("program-number") val programNumber: Int? = null,
        @SerialName("version") val version: String? = null
    )

    @Serializable
    private data class Note(
        @SerialName("event_index") val eventIndex: Int? = null,
        @SerialName("language") val language: Int? = null,
        @SerialName("length") val length: Int? = null,
        @SerialName("muted") val muted: Boolean? = null,
        @SerialName("note_env_preset_id") val noteEnvPresetId: Int? = null,
        @SerialName("note_gain_value") val noteGainValue: Int? = null,
        @SerialName("region-index") val regionIndex: Int? = null,
        @SerialName("syllables") val syllables: List<Syllable> = listOf(),
        @SerialName("vibrato_preset_id") val vibratoPresetId: Int? = null,
        @SerialName("voice_color_id") val voiceColorId: Int? = null,
        @SerialName("voice_release_id") val voiceReleaseId: Int? = null
    )

    @Serializable
    private data class Region(
        @SerialName("auto-expand-left") val autoExpandLeft: Boolean? = null,
        @SerialName("auto-expand-right") val autoExpandRight: Boolean? = null,
        @SerialName("length") val length: Int? = null,
        @SerialName("muted") val muted: Boolean? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("position") val position: Int? = null,
        @SerialName("z-order") val zOrder: Int? = null
    )

    @Serializable
    private data class FsmEffectParameter(
        @SerialName("constant") val constant: Int? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("sequence") val sequence: List<FsmEffectSequenceEvent> = listOf(),
        @SerialName("use_sequence") val useSequence: Boolean? = null
    )

    @Serializable
    private data class Syllable(
        @SerialName("footer-text") val footerText: String? = null,
        @SerialName("header-text") val headerText: String? = null,
        @SerialName("is-list-end") val isListEnd: Boolean? = null,
        @SerialName("is-list-top") val isListTop: Boolean? = null,
        @SerialName("is-word-end") val isWordEnd: Boolean? = null,
        @SerialName("is-word-top") val isWordTop: Boolean? = null,
        @SerialName("lyric-text") val lyricText: String? = null,
        @SerialName("symbols-text") val symbolsText: String? = null
    )

    @Serializable
    private data class DvlTrack(
        @SerialName("enabled") val enabled: Boolean? = null,
        @SerialName("events") val events: List<Event> = listOf(),
        @SerialName("mixer") val mixer: Mixer? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("parameters") val parameters: List<DvlParameter> = listOf(),
        @SerialName("plugin_output_bus_index") val pluginOutputBusIndex: Int? = null,
        @SerialName("singer") val singer: Singer? = null
    )

    @Serializable
    private data class LoopPoint(
        @SerialName("begin") val begin: Int? = null,
        @SerialName("enable") val enable: Boolean? = null,
        @SerialName("end") val end: Int? = null
    )

    @Serializable
    private data class Meter(
        @SerialName("const") val const: MeterConstValue,
        @SerialName("sequence") val sequence: List<MeterSequenceEvent>? = null,
        @SerialName("use_sequence") val useSequence: Boolean
    )

    @Serializable
    private data class Metronome(
        @SerialName("enable") val enable: Boolean? = null,
        @SerialName("wav") val wav: String? = null
    )

    @Serializable
    private data class Tempo(
        @SerialName("const") val const: Int,
        @SerialName("sequence") val sequence: List<TempoSequenceEvent>? = null,
        @SerialName("use_sequence") val useSequence: Boolean
    )

    @Serializable
    private data class Event(
        @SerialName("adjust_speed") val adjustSpeed: Boolean? = null,
        @SerialName("attack_speed_rate") val attackSpeedRate: Int? = null,
        @SerialName("consonant_rate") val consonantRate: Int? = null,
        @SerialName("consonant_speed_rate") val consonantSpeedRate: Int? = null,
        @SerialName("enabled") val enabled: Boolean? = null,
        @SerialName("length") val length: Int,
        @SerialName("lyric") val lyric: String? = null,
        @SerialName("note_number") val noteNumber: Int,
        @SerialName("note_off_pit_envelope") val noteOffPitEnvelope: NoteOffPitEnvelope? = null,
        @SerialName("note_on_pit_envelope") val noteOnPitEnvelope: NoteOnPitEnvelope? = null,
        @SerialName("portamento_envelope") val portamentoEnvelope: PortamentoEnvelope? = null,
        @SerialName("portamento_type") val portamentoType: Int? = null,
        @SerialName("pos") val pos: Int,
        @SerialName("protected") val isProtected: Boolean? = null,
        @SerialName("release_speed_rate") val releaseSpeedRate: Int? = null,
        @SerialName("symbols") val symbols: String? = null,
        @SerialName("vcl_like_note_off") val vclLikeNoteOff: Boolean? = null
    )

    @Serializable
    private data class Mixer(
        @SerialName("gain") val gain: Gain? = null,
        @SerialName("mixer_type") val mixerType: String? = null,
        @SerialName("panpot") val panpot: Panpot? = null
    )

    @Serializable
    private data class DvlParameter(
        @SerialName("base-sequence") val baseSequence: BaseSequence? = null
        // not implemented in PPS yet
        // @SerialName("layers") val layers: List<String> = listOf()
    )

    @Serializable
    private data class Singer(
        @SerialName("character_name") val characterName: String? = null,
        @SerialName("do_extrapolation") val doExtrapolation: Boolean? = null,
        @SerialName("frame_shift") val frameShift: Double? = null,
        @SerialName("gender") val gender: Int? = null,
        @SerialName("language_id") val languageId: Int? = null,
        @SerialName("library_id") val libraryId: String? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("singer_name") val singerName: String? = null,
        @SerialName("stationaly_type") val stationalyType: String? = null,
        @SerialName("synthesis_version") val synthesisVersion: String? = null,
        @SerialName("tail_silent") val tailSilent: Double? = null,
        @SerialName("vprm_morph_mode") val vprmMorphMode: String? = null
    )

    @Serializable
    private data class NoteOffPitEnvelope(
        @SerialName("length") val length: Int? = null,
        @SerialName("offset") val offset: Int? = null,
        // unknown
        // @SerialName("points") val points: List<String> = listOf(),
        @SerialName("use_length") val useLength: Boolean? = null
    )

    @Serializable
    private data class NoteOnPitEnvelope(
        @SerialName("length") val length: Int? = null,
        @SerialName("offset") val offset: Int? = null,
        // unknown
        // @SerialName("points") val points: List<String> = listOf(),
        @SerialName("use_length") val useLength: Boolean? = null
    )

    @Serializable
    private data class PortamentoEnvelope(
        @SerialName("length") val length: Int? = null,
        @SerialName("offset") val offset: Int? = null,
        // unknown
        // @SerialName("points") val points: List<String> = listOf(),
        @SerialName("use_length") val useLength: Boolean? = null
    )

    @Serializable
    private data class Gain(
        @SerialName("base-sequence") val baseSequence: BaseSequence? = null
        // not implemented in PPS yet
        // @SerialName("layers") val layers: List<String> = listOf()
    )

    @Serializable
    private data class Panpot(
        @SerialName("base-sequence") val baseSequence: BaseSequence? = null
        // not implemented in PPS yet
        // @SerialName("layers") val layers: List<String> = listOf()
    )

    @Serializable
    private data class BaseSequence(
        @SerialName("constant") val constant: Int? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("sequence") val sequence: List<BaseSequenceEvent> = listOf(),
        @SerialName("use_sequence") val useSequence: Boolean? = null
    )

    @Serializable
    private data class MeterConstValue(
        @SerialName("denomi") val denomi: Int,
        @SerialName("nume") val nume: Int
    )

    @Serializable
    private data class MeterSequenceEvent(
        @SerialName("denomi") val denomi: Int,
        @SerialName("nume") val nume: Int,
        @SerialName("measure") val measure: Int
    )

    @Serializable
    private data class TempoSequenceEvent(
        @SerialName("curve_type") val curveType: Int? = null,
        @SerialName("tick") val tick: Int,
        @SerialName("value") val value: Int
    )

    @Serializable
    private data class SubTrack(
        @SerialName("height") val height: Int? = null,
        @SerialName("sub-track-category") val subTrackCategory: Int? = null,
        @SerialName("sub-track-id") val subTrackId: Int? = null
    )

    @Serializable
    private data class CurvePoint(
        @SerialName("sequence") val sequence: List<CurvePointEvent> = listOf(),
        @SerialName("sub-track-category") val subTrackCategory: Int? = null,
        @SerialName("sub-track-id") val subTrackId: Int? = null
    )

    @Serializable
    private data class CurvePointEvent(
        @SerialName("border-type") val borderType: Int? = null,
        @SerialName("note-index") val noteIndex: Int? = null,
        @SerialName("region-index") val regionIndex: Int? = null
    )

    @Serializable
    private data class BaseSequenceEvent(
        @SerialName("curve_type") val curveType: Int? = null,
        @SerialName("pos") val pos: Int? = null,
        @SerialName("value") val value: Int? = null
    )

    @Serializable
    private data class AudioTrack(
        @SerialName("block_size") val blockSize: Int? = null,
        @SerialName("enabled") val enabled: Boolean? = null,
        @SerialName("events") val events: List<AudioTrackEvent>? = null,
        @SerialName("input_channel") val inputChannel: Int? = null,
        @SerialName("mixer") val mixer: Mixer? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("output_channel") val outputChannel: Int? = null,
        @SerialName("sampling_rate") val samplingRate: Int? = null
    )

    @Serializable
    private data class AudioTrackEvent(
        @SerialName("enabled") val enabled: Boolean? = null,
        @SerialName("file_audio_data") val fileAudioData: FileAudioData? = null,
        @SerialName("playback_offset_sample") val playbackOffsetSample: Int? = null,
        @SerialName("tick_length") val tickLength: Int? = null,
        @SerialName("tick_pos") val tickPos: Int? = null
    )

    @Serializable
    private data class FileAudioData(
        @SerialName("file_path") val filePath: String? = null,
        @SerialName("tempo") val tempo: Int? = null
    )

    @Serializable
    private data class FsmEffectSequenceEvent(
        @SerialName("curve_type") val curveType: Int? = null,
        @SerialName("pos") val pos: Int? = null,
        @SerialName("region-index") val regionIndex: Int? = null,
        @SerialName("value") val value: Int? = null
    )
}
