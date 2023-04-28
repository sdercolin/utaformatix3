package io

import external.Resources
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
import process.pitch.getRelativeData
import process.validateNotes
import util.nameWithoutExtension
import util.readText
import kotlin.math.roundToLong

object S5p {
    private const val TICK_RATE = 1470000L
    private const val DEFAULT_INTERVAL = 5512500L

    suspend fun parse(file: File, params: ImportParams): model.Project {
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
            model.Tempo(
                tickPosition = it.position / TICK_RATE,
                bpm = it.beatPerMinute,
            )
        }.takeIf { it.isNotEmpty() } ?: listOf(model.Tempo.default).also {
            warnings.add(ImportWarning.TempoNotFound)
        }
        val tracks = parseTracks(project, params)
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

    private fun parseTracks(project: Project, params: ImportParams): List<model.Track> =
        project.tracks.mapIndexed { index, track ->
            model.Track(
                id = index,
                name = track.name ?: "Track ${index + 1}",
                notes = parseNotes(track),
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

    private fun parseNotes(track: Track): List<model.Note> = track.notes.filterNotNull().map { note ->
        val tickOn = note.onset / TICK_RATE
        model.Note(
            id = 0,
            key = note.pitch,
            tickOn = tickOn,
            tickOff = tickOn + note.duration / TICK_RATE,
            lyric = note.lyric.takeUnless { it.isNullOrBlank() } ?: DEFAULT_LYRIC,
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
        val template = Resources.s5pTemplate
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

    private fun generateTrack(track: model.Track, emptyTrack: Track, features: List<Feature>): Track {
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

    private fun generatePitchData(track: model.Track, features: List<Feature>, interval: Long): List<Double> {
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
