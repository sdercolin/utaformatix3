package core.io

import core.external.JsZip
import core.model.Format
import core.model.ImportParams
import core.model.ImportWarning
import core.model.Note
import core.model.Pitch
import core.model.Project
import core.model.Tempo
import core.model.TimeSignature
import core.model.Track
import core.process.pitch.getAbsoluteData
import core.process.validateNotes
import core.util.nameWithoutExtension
import core.util.readBinary
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.w3c.files.File

object Vprx {
    suspend fun parse(
        file: File,
        params: ImportParams,
    ): Project {
        val zip = JsZip().loadAsync(file.readBinary()).await()
        val entry = requireNotNull(zip.file("Project/sequence.json") ?: zip.file("Project\\sequence.json"))
        val content = json.decodeFromString(Sequence.serializer(), entry.async("string").await() as String)
        val warnings = mutableListOf<ImportWarning>()
        val master = content.masterTrack
        val timeSignatures =
            master?.timeSig?.events.orEmpty().map { TimeSignature(it.bar, it.numer, it.denom) }.ifEmpty {
                warnings.add(ImportWarning.TimeSignatureNotFound)
                listOf(TimeSignature.default)
            }
        val tempoMap = master?.tempo
        val globalTempo = tempoMap?.global?.takeIf { it.isEnabled && it.value.isFinite() && it.value > 0 }
        val tempos =
            globalTempo?.let { listOf(Tempo(0, it.value / 100)) }
                ?: tempoMap?.events.orEmpty().map { Tempo(it.pos, it.value / 100) }.ifEmpty {
                    warnings.add(ImportWarning.TempoNotFound)
                    listOf(Tempo.default)
                }
        val tracks =
            content.tracks
                .filterNot { it.type == AUDIO_TRACK_TYPE }
                .mapIndexed { index, track -> track.parse(index, params) }
        return Project(
            format = Format.Vprx,
            inputFiles = listOf(file),
            name = content.title ?: file.nameWithoutExtension,
            tracks = tracks,
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = 0,
            importWarnings = warnings,
        )
    }

    private fun SequenceTrack.parse(
        index: Int,
        params: ImportParams,
    ): Track {
        val notes =
            parts
                .flatMap { part ->
                    part.notes.mapNotNull { note ->
                        val duration = note.duration ?: return@mapNotNull null
                        if (note.pos < 0 || duration <= 0) return@mapNotNull null
                        Note(
                            id = 0,
                            tickOn = part.pos + note.pos,
                            tickOff = part.pos + note.pos + duration,
                            key = note.number,
                            lyric = note.lyric.takeUnless { it.isNullOrBlank() } ?: params.defaultLyric,
                            phoneme = note.phoneme,
                        )
                    }
                }.validateNotes()
        return Track(
            id = index,
            name = name ?: "Track ${index + 1}",
            notes = notes,
            pitch = if (params.simpleImport) null else parsePitch(parts, notes),
        )
    }

    private fun parsePitch(
        parts: List<Part>,
        notes: List<Note>,
    ): Pitch? {
        val orderedParts = parts.sortedBy { it.pos }
        val directByPart = orderedParts.map { it.directPitchData() }
        val isAbsolute = directByPart.any { it.isNotEmpty() }
        val points = mutableMapOf<Long, Double?>()
        for ((part, direct) in orderedParts.zip(directByPart)) {
            val data =
                direct.ifEmpty {
                    val relative = part.controllerPitchData()
                    if (isAbsolute) Pitch(relative, false).getAbsoluteData(notes).orEmpty() else relative
                }
            points.putAll(data)
        }
        return Pitch(points.toList().sortedBy { it.first }, isAbsolute).takeIf { points.isNotEmpty() }
    }

    private fun Part.directPitchData(): List<Pair<Long, Double?>> {
        val samples = mutableMapOf<Long, Pair<Double, Long>>()
        val resets = mutableSetOf<Long>()
        for (note in notes) {
            val duration = note.duration ?: continue
            if (note.pos < 0 || duration <= 0) continue
            for (point in note.directPitches.orEmpty()) {
                val tick = pos + note.pos + point.pos
                val value = point.value ?: continue
                if (tick < 0) continue
                val distanceToNote = maxOf(0L, -point.pos, point.pos - duration + 1)
                if (value.content == "ZeroPitch" && distanceToNote == 0L) resets.add(tick)
                val offset = value.doubleOrNull?.takeIf { it.isFinite() } ?: continue

                // Adjacent locked notes can store the same transition with offsets from different keys.
                val previous = samples[tick]
                if (previous == null || distanceToNote < previous.second) {
                    samples[tick] = (note.number + offset / 100) to distanceToNote
                }
            }
        }
        if (samples.isEmpty()) return emptyList()
        return samples.map { (tick, sample) -> tick to sample.first } +
            (resets - samples.keys).map { it to null }
    }

    private fun Part.controllerPitchData(): List<Pair<Long, Double>> {
        val bends = controllerEvents("pitchBend")
        val sensitivities = controllerEvents("pitchBendSens")
        var index = 0
        var sensitivity = 2.0
        return bends.map { (tick, value) ->
            while (index < sensitivities.size && sensitivities[index].first <= tick) {
                sensitivity = sensitivities[index++].second
            }
            (pos + tick) to (value * sensitivity / 8191)
        }
    }

    private fun Part.controllerEvents(name: String): List<Pair<Long, Double>> {
        val controller = controllers?.firstOrNull { it.name == name } ?: return emptyList()
        return controller.events
            .mapNotNull { event ->
                val value = event.value?.doubleOrNull ?: return@mapNotNull null
                if (value.isFinite()) event.pos to value else null
            }.sortedBy { it.first }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private const val AUDIO_TRACK_TYPE = 1

    @Serializable
    private data class Sequence(
        val title: String? = null,
        val masterTrack: MasterTrack? = null,
        val tracks: List<SequenceTrack> = emptyList(),
    )

    @Serializable
    private data class MasterTrack(
        val tempo: TempoMap? = null,
        val timeSig: MeterMap? = null,
    )

    @Serializable
    private data class TempoMap(
        val global: GlobalTempo? = null,
        val events: List<TempoEvent> = emptyList(),
    )

    @Serializable
    private data class GlobalTempo(
        val isEnabled: Boolean = false,
        val value: Double = 0.0,
    )

    @Serializable
    private data class TempoEvent(
        val pos: Long,
        val value: Double,
    )

    @Serializable
    private data class MeterMap(
        val events: List<MeterEvent> = emptyList(),
    )

    @Serializable
    private data class MeterEvent(
        val bar: Int,
        val numer: Int,
        val denom: Int,
    )

    @Serializable
    private data class SequenceTrack(
        val name: String? = null,
        val type: Int? = null,
        val parts: List<Part> = emptyList(),
    )

    @Serializable
    private data class Part(
        val pos: Long,
        val notes: List<SequenceNote> = emptyList(),
        val controllers: List<Controller>? = null,
    )

    @Serializable
    private data class SequenceNote(
        val pos: Long,
        val duration: Long? = null,
        val number: Int,
        val lyric: String? = null,
        val phoneme: String? = null,
        val directPitches: List<PitchPoint>? = null,
    )

    @Serializable
    private data class Controller(
        val name: String? = null,
        val events: List<PitchPoint> = emptyList(),
    )

    @Serializable
    private data class PitchPoint(
        val pos: Long,
        val value: JsonPrimitive? = null,
    )
}
