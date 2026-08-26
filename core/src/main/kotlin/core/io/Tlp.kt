package core.io

import core.model.ExportNotification
import core.model.ExportResult
import core.model.Feature
import core.model.FeatureConfig
import core.model.Format
import core.model.ImportParams
import core.model.ImportWarning
import core.model.Pitch
import core.model.TimeSignature
import core.model.contains
import core.process.pitch.getAbsoluteData
import core.process.validateNotes
import core.util.nameWithoutExtension
import core.util.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.File
import kotlin.math.roundToLong

object Tlp {
    private val format = Format.Tlp

    suspend fun parse(
        file: File,
        params: ImportParams,
    ): core.model.Project {
        val text = file.readText()
        val project = jsonSerializer.decodeFromString(TlpProject.serializer(), text)
        val warnings = mutableListOf<ImportWarning>()

        val timeSignatures =
            project.timeSignatures
                ?.map {
                    TimeSignature(
                        measurePosition = it.barIndex,
                        numerator = it.numerator,
                        denominator = it.denominator,
                    )
                }?.takeIf { it.isNotEmpty() } ?: listOf(TimeSignature.default).also {
                warnings.add(ImportWarning.TimeSignatureNotFound)
            }

        val tempos =
            project.tempos
                ?.map {
                    core.model.Tempo(
                        tickPosition = it.pos.roundToLong(),
                        bpm = it.bpm,
                    )
                }?.takeIf { it.isNotEmpty() } ?: listOf(core.model.Tempo.default).also {
                warnings.add(ImportWarning.TempoNotFound)
            }

        val tracks = parseTracks(project, params)
        return core.model.Project(
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
        project: TlpProject,
        params: ImportParams,
    ): List<core.model.Track> {
        val midiTracks = project.tracks.filter { it.parts.any { p -> p.type == "midi" } }
        val tracksToParse = midiTracks.ifEmpty { project.tracks }
        return tracksToParse.mapIndexed { index, track ->
            val midiParts = track.parts.filter { it.type == "midi" }
            val notes =
                midiParts
                    .flatMap { part ->
                        part.notes.map { note ->
                            val tickOn = (part.pos + note.pos).roundToLong()
                            val tickOff = tickOn + note.dur.roundToLong()
                            core.model.Note(
                                id = 0,
                                tickOn = tickOn,
                                tickOff = tickOff,
                                lyric = note.lyric.takeUnless { it.isBlank() } ?: params.defaultLyric,
                                key = note.pitch,
                                phoneme = note.phonemes?.joinToString(" ") { it.symbol }?.takeUnless { it.isBlank() },
                            )
                        }
                    }.mapIndexed { noteIdx, note -> note.copy(id = noteIdx) }

            val pitch = if (params.simpleImport) null else parsePitch(midiParts)

            core.model
                .Track(
                    id = index,
                    name = track.name ?: "Track ${index + 1}",
                    notes = notes,
                    pitch = pitch,
                ).validateNotes()
        }
    }

    private fun parsePitch(parts: List<TlpPart>): Pitch? {
        val allSegments =
            parts.flatMap { part ->
                part.pitch.mapNotNull { segment ->
                    segment
                        .chunked(2)
                        .mapNotNull { pair ->
                            val tick = pair.getOrNull(0) ?: return@mapNotNull null
                            val pitchVal = pair.getOrNull(1) ?: return@mapNotNull null
                            ((part.pos + tick).roundToLong() to (pitchVal as Double?))
                        }.takeIf { it.isNotEmpty() }
                }
            }
        if (allSegments.isEmpty()) return null
        val points = mutableListOf<Pair<Long, Double?>>()
        for (segment in allSegments) {
            if (points.isNotEmpty()) {
                val lastTick = points.last().first
                val firstTick = segment.first().first
                if (firstTick > lastTick + 1) {
                    points.add(lastTick + 1 to null)
                }
            }
            points.addAll(segment)
        }
        return if (points.isNotEmpty()) Pitch(data = points, isAbsolute = true) else null
    }

    fun generate(
        project: core.model.Project,
        features: List<FeatureConfig>,
    ): ExportResult {
        val tempos =
            project.tempos.map {
                TlpTempo(pos = it.tickPosition.toDouble(), bpm = it.bpm)
            }
        val timeSignatures =
            project.timeSignatures.map {
                TlpTimeSignature(
                    barIndex = it.measurePosition,
                    numerator = it.numerator,
                    denominator = it.denominator,
                )
            }
        val tracks =
            project.tracks.mapIndexed { _, track ->
                val notes =
                    track.notes.map { note ->
                        val phonemes =
                            note.phoneme
                                ?.split(" ")
                                ?.filter { it.isNotBlank() }
                                ?.map { symbol ->
                                    TlpPhoneme(
                                        startTime = 0.0,
                                        endTime = 0.0,
                                        symbol = symbol,
                                    )
                                } ?: emptyList()

                        TlpNote(
                            pos = note.tickOn.toDouble(),
                            dur = note.length.toDouble(),
                            pitch = note.key,
                            lyric = note.lyric,
                            pronunciation = "",
                            phonemes = phonemes,
                        )
                    }
                val pitchSegments =
                    if (features.contains(Feature.ConvertPitch)) {
                        generatePitchData(track)
                    } else {
                        emptyList()
                    }
                val partDur = (track.notes.maxOfOrNull { it.tickOff } ?: 0L).toDouble() + 1920.0
                val part =
                    TlpPart(
                        name = "Part_1",
                        pos = 0.0,
                        dur = partDur,
                        type = "midi",
                        gain = 0.0,
                        notes = notes,
                        pitch = pitchSegments,
                        vibratos = emptyList(),
                    )
                TlpTrack(
                    name = track.name,
                    gain = 0.0,
                    pan = 0.0,
                    mute = false,
                    solo = false,
                    color = "#73B5E5",
                    asRefer = false,
                    parts = listOf(part),
                )
            }

        val tlpProject =
            TlpProject(
                version = 0,
                tempos = tempos,
                timeSignatures = timeSignatures,
                tracks = tracks,
            )

        val jsonString = jsonSerializer.encodeToString(TlpProject.serializer(), tlpProject)
        val blob = Blob(arrayOf(jsonString), BlobPropertyBag("application/json"))
        return ExportResult(
            blob = blob,
            fileName = format.getFileName(project.name),
            notifications =
                listOfNotNull(
                    if (features.contains(Feature.ConvertPitch)) ExportNotification.PitchDataExported else null,
                ),
        )
    }

    private fun generatePitchData(track: core.model.Track): List<List<Double>> {
        val absoluteData = track.pitch?.getAbsoluteData(track.notes) ?: return emptyList()
        val segments = mutableListOf<List<Double>>()
        var currentSegment = mutableListOf<Double>()
        var lastTick: Long? = null

        for ((tick, value) in absoluteData) {
            if (value != null) {
                if (lastTick != null && tick - lastTick > 240L && currentSegment.isNotEmpty()) {
                    segments.add(currentSegment)
                    currentSegment = mutableListOf()
                }
                currentSegment.add(tick.toDouble())
                currentSegment.add(value)
                lastTick = tick
            } else {
                if (currentSegment.isNotEmpty()) {
                    segments.add(currentSegment)
                    currentSegment = mutableListOf()
                    lastTick = null
                }
            }
        }
        if (currentSegment.isNotEmpty()) {
            segments.add(currentSegment)
        }
        return segments
    }

    private val jsonSerializer =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }

    @Serializable
    private data class TlpProject(
        val version: Int = 0,
        val tempos: List<TlpTempo>? = null,
        val timeSignatures: List<TlpTimeSignature>? = null,
        val tracks: List<TlpTrack> = emptyList(),
    )

    @Serializable
    private data class TlpTempo(
        val pos: Double = 0.0,
        val bpm: Double = 120.0,
    )

    @Serializable
    private data class TlpTimeSignature(
        val barIndex: Int = 0,
        val numerator: Int = 4,
        val denominator: Int = 4,
    )

    @Serializable
    private data class TlpTrack(
        val name: String? = null,
        val gain: Double = 0.0,
        val pan: Double = 0.0,
        val mute: Boolean = false,
        val solo: Boolean = false,
        val color: String = "#73B5E5",
        val asRefer: Boolean = false,
        val parts: List<TlpPart> = emptyList(),
    )

    @Serializable
    private data class TlpPart(
        val name: String? = null,
        val pos: Double = 0.0,
        val dur: Double = 0.0,
        val type: String = "midi",
        val gain: Double = 0.0,
        val voice: JsonElement? = null,
        val properties: JsonElement? = null,
        val notes: List<TlpNote> = emptyList(),
        val automations: JsonElement? = null,
        val pitch: List<List<Double>> = emptyList(),
        val vibratos: List<JsonElement> = emptyList(),
    )

    @Serializable
    private data class TlpNote(
        val pos: Double = 0.0,
        val dur: Double = 0.0,
        val pitch: Int = 60,
        val lyric: String = "",
        val pronunciation: String = "",
        val properties: JsonElement? = null,
        val phonemes: List<TlpPhoneme>? = null,
    )

    @Serializable
    private data class TlpPhoneme(
        val startTime: Double = 0.0,
        val endTime: Double = 0.0,
        val symbol: String = "",
    )
}
