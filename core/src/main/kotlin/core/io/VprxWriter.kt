package core.io

import core.external.JsZip
import core.external.JsZipOption
import core.external.Resources
import core.model.ExportNotification
import core.model.ExportResult
import core.model.Feature
import core.model.FeatureConfig
import core.model.Format
import core.model.ImportParams
import core.model.Note
import core.model.Project
import core.model.TickCounter
import core.model.Track
import core.model.contains
import core.util.extensionName
import core.util.runCatchingCancellable
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.w3c.files.Blob
import kotlin.math.roundToInt

internal object VprxWriter {
    suspend fun generate(
        project: Project,
        features: List<FeatureConfig>,
    ): ExportResult {
        val convertPitch = features.contains(Feature.ConvertPitch)
        val nativeParts = if (convertPitch) project.nativeParts() else emptyMap()
        val zip = JsZip()
        zip.file("Project/sequence.json", generateContent(project, convertPitch, nativeParts).toString())
        val options =
            JsZipOption().also {
                it.type = "blob"
                it.mimeType = "application/octet-stream"
            }
        return ExportResult(
            zip.generateAsync(options).await() as Blob,
            Format.Vprx.getFileName(project.name),
            listOfNotNull(ExportNotification.PitchDataExported.takeIf { convertPitch }),
        )
    }

    private fun generateContent(
        project: Project,
        convertPitch: Boolean,
        nativeParts: Map<Int, JsonArray>,
    ): JsonObject {
        val tickCounter = TickCounter()
        val timeSignatures =
            project.timeSignatures.map {
                tickCounter.goToMeasure(it)
                jsonObject(
                    "bar" to it.measurePosition,
                    "numer" to it.numerator,
                    "denom" to it.denominator,
                )
            }
        val tempos = project.tempos.map { jsonObject("pos" to it.tickPosition, "value" to (it.bpm * 100).roundToInt()) }
        val endTick =
            maxOf(
                tickCounter.outputTick,
                project.tempos.maxOfOrNull { it.tickPosition } ?: 0L,
                project.tracks.maxOfOrNull { it.notes.lastOrNull()?.tickOff ?: 0L } ?: 0L,
            )
        val master = template.getValue("masterTrack").jsonObject
        val loop = JsonObject(master.getValue("loop").jsonObject + ("end" to JsonPrimitive(endTick)))
        return JsonObject(
            template +
                mapOf(
                    "title" to JsonPrimitive(project.name),
                    "masterTrack" to
                        JsonObject(
                            master +
                                mapOf(
                                    "loop" to loop,
                                    "tempo" to withEvents(master, "tempo", tempos),
                                    "timeSig" to withEvents(master, "timeSig", timeSignatures),
                                ),
                        ),
                    "tracks" to
                        JsonArray(
                            project.tracks.map { track ->
                                generateTrack(track, project.format, convertPitch, nativeParts[track.id])
                            },
                        ),
                ),
        )
    }

    private fun generateTrack(
        track: Track,
        sourceFormat: Format,
        convertPitch: Boolean,
        nativeParts: JsonArray?,
    ): JsonObject {
        if (nativeParts != null) {
            return JsonObject(trackTemplate + mapOf("name" to JsonPrimitive(track.name), "parts" to nativeParts))
        }
        val pitch = if (convertPitch) track.directPitchData(sourceFormat) else emptyList()
        val notes = track.notes.map { note -> generateNote(note, note.tickOn, JsonArray(pitch.forNote(note))) }
        val parts =
            track.notes
                .lastOrNull()
                ?.let { note ->
                    listOf(
                        JsonObject(
                            partTemplate +
                                mapOf(
                                    "duration" to JsonPrimitive(note.tickOff),
                                    "notes" to JsonArray(notes),
                                ),
                        ),
                    )
                }.orEmpty()
        return JsonObject(trackTemplate + mapOf("name" to JsonPrimitive(track.name), "parts" to JsonArray(parts)))
    }

    private fun generateNote(
        note: Note,
        position: Long,
        pitch: JsonArray?,
    ): JsonObject {
        val phoneme = note.phoneme?.takeIf { it.isNotBlank() }
        val properties =
            noteTemplate.toMutableMap().apply {
                put("pos", JsonPrimitive(position))
                put("duration", JsonPrimitive(note.length))
                put("number", JsonPrimitive(note.key))
                put("lyric", JsonPrimitive(note.lyric))
                put("isProtected", JsonPrimitive(phoneme != null))
                if (phoneme != null) put("phoneme", JsonPrimitive(phoneme))
                if (!pitch.isNullOrEmpty()) put("directPitches", pitch)
            }
        return JsonObject(properties)
    }

    private suspend fun Project.nativeParts(): Map<Int, JsonArray> {
        if (format != Format.Vprx) return emptyMap()
        val file = inputFiles.singleOrNull()?.takeIf { it.extensionName == Format.Vprx.extension } ?: return emptyMap()
        val (originalTracks, rawTracks) =
            runCatchingCancellable {
                val sequence = Vprx.readSequence(file)
                val original = Vprx.parseSequence(file, ImportParams(), sequence)
                val raw =
                    Json
                        .parseToJsonElement(sequence)
                        .jsonObject["tracks"]
                        ?.jsonArray
                        .orEmpty()
                        .map { it.jsonObject }
                        .filter { it["type"]?.jsonPrimitive?.intOrNull != Vprx.AUDIO_TRACK_TYPE }
                original.tracks to raw
            }.getOrNull() ?: return emptyMap()

        // The shared pitch model drops some stroke ends and part boundaries.
        return tracks
            .mapNotNull { track ->
                val original = originalTracks.getOrNull(track.id) ?: return@mapNotNull null
                if (track.pitch != original.pitch) return@mapNotNull null
                val parts = rawTracks.getOrNull(track.id)?.get("parts")?.jsonArray ?: return@mapNotNull null
                track.rebuildNativeParts(parts)?.let { track.id to it }
            }.toMap()
    }

    private fun Track.rebuildNativeParts(parts: JsonArray): JsonArray? {
        val notesByTick = notes.associateBy { it.tickOn }.toMutableMap()
        val result =
            parts.map { raw ->
                val part = raw.jsonObject
                val start = part.getValue("pos").jsonPrimitive.long
                var end = 0L
                val exportedNotes =
                    part["notes"]?.jsonArray.orEmpty().map { rawNote ->
                        val original = rawNote.jsonObject
                        val pos = original.getValue("pos").jsonPrimitive.long
                        val note = notesByTick.remove(start + pos) ?: return null
                        if (original["duration"]?.jsonPrimitive?.long != note.length ||
                            original["number"]?.jsonPrimitive?.intOrNull != note.key
                        ) {
                            return null
                        }
                        end = maxOf(end, pos + note.length)
                        generateNote(note, pos, original["directPitches"] as? JsonArray)
                    }
                val duration = maxOf((part["duration"] as? JsonPrimitive)?.longOrNull ?: 0L, end)
                val properties =
                    partTemplate.toMutableMap().apply {
                        put("pos", JsonPrimitive(start))
                        put("duration", JsonPrimitive(duration))
                        put("notes", JsonArray(exportedNotes))
                        val controllers =
                            (part["controllers"] as? JsonArray).orEmpty().filter {
                                it.jsonObject["name"]?.jsonPrimitive?.contentOrNull in pitchControllerNames
                            }
                        if (controllers.isNotEmpty()) put("controllers", JsonArray(controllers))
                    }
                JsonObject(properties)
            }
        return if (notesByTick.isEmpty()) JsonArray(result) else null
    }

    private fun Track.directPitchData(sourceFormat: Format): List<Pair<Long, Double?>> {
        val pitch = pitch ?: return emptyList()
        val end = notes.lastOrNull()?.tickOff ?: return emptyList()
        val points =
            pitch.data
                .filter { (tick, value) -> tick in 0..end && (value == null || value.isFinite()) }
                .associate { it }
                .toMutableMap()
        if (points.isEmpty()) return emptyList()
        if (pitch.isAbsolute && sourceFormat == Format.Vprx) return points.toList().sortedBy { it.first }

        val original = points.toList().sortedBy { it.first }
        if (!pitch.isAbsolute) {
            for (note in notes) {
                val index = original.indexAtOrAfter(note.tickOn)
                val previous = original.getOrNull(index - 1)?.second ?: continue
                if (note.tickOn !in points) points[note.tickOn] = previous
            }
        }
        if (end !in points && end - 1 !in points) points[end - 1] = original.last().second
        val samples = points.toList().sortedBy { it.first }
        var noteIndex = 0
        val absolute =
            samples.map { (tick, value) ->
                while (noteIndex < notes.lastIndex && notes[noteIndex + 1].tickOn <= tick) noteIndex++
                tick to value?.let { if (pitch.isAbsolute) it else it + notes[noteIndex].key }
            }
        // The project model holds each value until the next event. Drawn pitch interpolates between samples.
        return absolute
            .zipWithNext()
            .flatMap { (previous, next) ->
                if (previous.second != null && next.first - previous.first > 1 && previous.second != next.second) {
                    listOf(previous, (next.first - 1) to previous.second)
                } else {
                    listOf(previous)
                }
            }.plus(absolute.last())
    }

    private fun List<Pair<Long, Double?>>.forNote(note: Note): List<JsonObject> {
        val first = indexAtOrAfter(note.tickOn)
        val end = indexAtOrAfter(note.tickOff)
        val start = if (first > 0 && first < size && this[first - 1].second != null) first - 1 else first
        val limit = if (end > start && end < size && this[end - 1].second != null) end + 1 else end
        return subList(start, limit)
            .takeIf { points -> points.any { it.second != null } }
            .orEmpty()
            .map { (tick, value) ->
                val offset = value?.let { JsonPrimitive((it - note.key) * 100) } ?: JsonPrimitive("ZeroPitch")
                JsonObject(mapOf("pos" to JsonPrimitive(tick - note.tickOn), "value" to offset))
            }
    }

    private fun List<Pair<Long, Double?>>.indexAtOrAfter(tick: Long): Int {
        val index = binarySearchBy(tick) { it.first }
        return if (index >= 0) index else -index - 1
    }

    private fun withEvents(
        master: JsonObject,
        key: String,
        events: List<JsonObject>,
    ) = JsonObject(master.getValue(key).jsonObject + ("events" to JsonArray(events)))

    private fun jsonObject(vararg fields: Pair<String, Number>) =
        JsonObject(fields.associate { it.first to JsonPrimitive(it.second) })

    private val template by lazy { Json.parseToJsonElement(Resources.vprxTemplate).jsonObject }
    private val pitchControllerNames = setOf("pitchBend", "pitchBendSens")
    private val trackTemplate by lazy {
        template
            .getValue("tracks")
            .jsonArray
            .first()
            .jsonObject
    }
    private val partTemplate by lazy {
        trackTemplate
            .getValue("parts")
            .jsonArray
            .first()
            .jsonObject
    }
    private val noteTemplate by lazy {
        partTemplate
            .getValue("notes")
            .jsonArray
            .first()
            .jsonObject
    }
}
