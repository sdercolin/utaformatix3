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
import core.process.pitch.getRelativeData
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

object S5p {
    private const val TICK_RATE = 1470000L
    private const val DEFAULT_INTERVAL = 5512500L

    suspend fun parse(file: File, params: ImportParams): core.model.Project {
        val text = file.readText().let {
            val index = it.lastIndexOf('}')
            it.take(index + 1)
        }
        val project = jsonSerializer.decodeFromString(Project.serializer(), text)
        val warnings = mutableListOf<ImportWarning>()
        val timeSignatures = project.meter.map {
            TimeSignature(
                measurePosition = it.measure,
                numerator = it.beatPerMeasure,
                denominator = it.beatGranularity,
            )
        }.takeIf { it.isNotEmpty() } ?: listOf(TimeSignature.default).also {
            warnings.add(ImportWarning.TimeSignatureNotFound)
        }
        val tempos = project.tempo.map {
            core.model.Tempo(
                tickPosition = it.position / TICK_RATE,
                bpm = it.beatPerMinute,
            )
        }.takeIf { it.isNotEmpty() } ?: listOf(core.model.Tempo.default).also {
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

    private fun parseTracks(project: Project, params: ImportParams): List<core.model.Track> =
        project.tracks.mapIndexed { index, track ->
            core.model.Track(
                id = index,
                name = track.name ?: "Track ${index + 1}",
                notes = parseNotes(track, params.defaultLyric),
                pitch = if (params.simpleImport) null else parsePitch(track),
            ).validateNotes()
        }

    private fun parsePitch(track: Track): Pitch? {
        val pitchDelta = track.parameters?.pitchDelta ?: return Pitch(emptyList(), isAbsolute = false)
        val convertedPoints = pitchDelta.asSequence()
            .chunked(2)
            .mapNotNull {
                val rawTick = it.getOrNull(0) ?: return@mapNotNull null
                val centValue = it.getOrNull(1) ?: return@mapNotNull null

                val tick = rawTick * (track.parameters?.interval!!.toDouble().div(TICK_RATE))
                val value = centValue / 100

                tick.roundToLong() to value
            }
            .toList()
        return Pitch(convertedPoints, isAbsolute = false).takeIf { it.data.isNotEmpty() }
    }

    private fun parseNotes(track: Track, defaultLyric: String): List<core.model.Note> =
        track.notes.filterNotNull().map { note ->
            val tickOn = note.onset / TICK_RATE
            core.model.Note(
                id = 0,
                key = note.pitch,
                tickOn = tickOn,
                tickOff = tickOn + note.duration / TICK_RATE,
                lyric = note.lyric.takeUnless { it.isNullOrBlank() } ?: defaultLyric,
            )
        }

    fun generate(project: core.model.Project, features: List<FeatureConfig>): ExportResult {
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

    private fun generateContent(project: core.model.Project, features: List<FeatureConfig>): String {
        val template = core.external.Resources.s5pTemplate
        val s5p = jsonSerializer.decodeFromString(Project.serializer(), template)
        s5p.meter = project.timeSignatures.map {
            Meter(
                measure = it.measurePosition,
                beatPerMeasure = it.numerator,
                beatGranularity = it.denominator,
            )
        }
        s5p.tempo = project.tempos.map {
            Tempo(
                position = it.tickPosition * TICK_RATE,
                beatPerMinute = it.bpm,
            )
        }
        val emptyTrack = s5p.tracks.first()
        s5p.tracks = project.tracks.map {
            generateTrack(it, emptyTrack, features)
        }
        return jsonSerializer.encodeToString(Project.serializer(), s5p)
    }

    private fun generateTrack(track: core.model.Track, emptyTrack: Track, features: List<FeatureConfig>): Track {
        return emptyTrack.copy(
            name = track.name,
            displayOrder = track.id,
            notes = track.notes.map {
                Note(
                    onset = it.tickOn * TICK_RATE,
                    duration = it.length * TICK_RATE,
                    lyric = it.lyric,
                    pitch = it.key,
                )
            },
            parameters = emptyTrack.parameters!!.copy(
                interval = DEFAULT_INTERVAL,
                pitchDelta = generatePitchData(track, features, DEFAULT_INTERVAL),
            ),
        )
    }

    private fun generatePitchData(
        track: core.model.Track,
        features: List<FeatureConfig>,
        interval: Long,
    ): List<Double> {
        if (!features.contains(Feature.ConvertPitch)) return emptyList()
        val data = track.pitch?.getRelativeData(track.notes)
            ?.map { (it.first / (interval.toDouble().div(TICK_RATE)) to (it.second * 100)) }
            ?: return emptyList()
        return data.flatMap { listOf(it.first, it.second) }
    }

    private val jsonSerializer = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class Project(
        var instrumental: JsonElement? = null,
        var meter: List<Meter> = listOf(),
        var mixer: JsonElement? = null,
        var tempo: List<Tempo> = listOf(),
        var tracks: List<Track> = listOf(),
        var version: Int? = null,
    )

    @Serializable
    private data class Meter(
        var beatGranularity: Int,
        var beatPerMeasure: Int,
        var measure: Int,
    )

    @Serializable
    private data class Tempo(
        var beatPerMinute: Double,
        var position: Long,
    )

    @Serializable
    private data class Track(
        var color: String? = null,
        var dbDefaults: JsonElement? = null,
        var dbName: String? = null,
        var displayOrder: Int? = null,
        var mixer: JsonElement? = null,
        var name: String? = null,
        var notes: List<Note?> = listOf(),
        var parameters: Parameters? = null,
    )

    @Serializable
    private data class Note(
        var comment: String? = null,
        var dF0Jitter: Double? = null,
        var duration: Long,
        var lyric: String? = null,
        var onset: Long,
        var pitch: Int,
    )

    @Serializable
    private data class Parameters(
        var breathiness: List<Double>? = null,
        var gender: List<Double>? = null,
        var interval: Long? = null,
        var loudness: List<Double>? = null,
        var pitchDelta: List<Double>? = null,
        var tension: List<Double>? = null,
        var vibratoEnv: List<Double>? = null,
        var voicing: List<Double>? = null,
    )

    private val format = Format.S5p
}
