package io

import external.JsYaml
import external.Resources
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import model.ExportNotification
import model.ExportResult
import model.Feature
import model.Format
import model.ImportParams
import model.Note
import model.Project
import model.Tempo
import model.TimeSignature
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.File
import process.pitch.OpenUtauNotePitchData
import process.pitch.OpenUtauPartPitchData
import process.pitch.UtauNoteVibratoParams
import process.pitch.mergePitchFromUstxParts
import process.pitch.pitchFromUstxPart
import process.pitch.reduceRepeatedPitchPointsFromUstxTrack
import process.pitch.toOpenUtauPitchData
import util.readText

object Ustx {

    private const val PITCH_CURVE_ABBR = "pitd"

    suspend fun parse(file: File, params: ImportParams): model.Project {
        val yamlText = file.readText()
        val yaml = JsYaml.load(yamlText)
        val jsonText = JSON.stringify(yaml)
        val project = jsonSerializer.decodeFromString(Project.serializer(), jsonText)
        val timeSignature = TimeSignature(0, project.beatPerBar, project.beatUnit)
        val tempo = Tempo(0, project.bpm)
        return Project(
            format = Format.Ustx,
            inputFiles = listOf(file),
            name = project.name,
            tracks = parseTracks(project, params, tempo.bpm),
            timeSignatures = listOf(timeSignature),
            tempos = listOf(tempo),
            measurePrefix = 0,
            importWarnings = listOf()
        )
    }

    private fun parseTracks(project: Project, params: ImportParams, bpm: Double): List<model.Track> {
        val trackMap = List(project.tracks.size) { index: Int ->
            model.Track(
                id = index,
                name = "Track ${index + 1}",
                notes = listOf()
            )
        }.associateBy { it.id }.toMutableMap()
        for (voicePart in project.voiceParts) {
            val trackId = voicePart.trackNo
            val track = trackMap[trackId] ?: continue
            val tickPrefix = voicePart.position
            val notes = voicePart.notes.map {
                Note(
                    id = 0,
                    key = it.tone,
                    lyric = it.lyric,
                    tickOn = it.position + tickPrefix,
                    tickOff = it.position + it.duration + tickPrefix
                )
            }
            val notePitches = if (params.simpleImport) null
            else voicePart.notes.map { note -> parseNotePitch(note) }
            val (validatedNotes, validatedNotePitches) = getValidatedNotes(notes, notePitches)

            val pitchCurve = if (params.simpleImport) null
            else voicePart.curves.find { it.abbr == PITCH_CURVE_ABBR }?.let { curve ->
                curve.xs.zip(curve.ys).map { OpenUtauPartPitchData.Point(it.first + tickPrefix, it.second.toInt()) }
            }
            val pitch: model.Pitch? = if (validatedNotePitches?.isNotEmpty() == true || pitchCurve != null) {
                val partPitchData = OpenUtauPartPitchData(
                    pitchCurve.orEmpty(),
                    validatedNotePitches.orEmpty()
                )
                pitchFromUstxPart(validatedNotes, partPitchData, bpm)
            } else null
            val mergedPitch = mergePitchFromUstxParts(track.pitch, pitch)
            val newTrack = track.copy(notes = track.notes + validatedNotes, pitch = mergedPitch)
            trackMap[trackId] = newTrack
        }
        return trackMap.values
            .map {
                it.copy(
                    notes = it.notes.mapIndexed { index, note -> note.copy(id = index) },
                    pitch = it.pitch.reduceRepeatedPitchPointsFromUstxTrack()
                )
            }
            .sortedBy { it.id }
    }

    private fun parseNotePitch(note: Note): OpenUtauNotePitchData {
        val points = note.pitch.data.map {
            OpenUtauNotePitchData.Point(
                x = it.x,
                y = it.y,
                shape = OpenUtauNotePitchData.Shape.values()
                    .find { shape -> shape.textValue == it.shape }
                    ?: OpenUtauNotePitchData.Shape.EaseInOut
            )
        }
        val vibrato = note.vibrato.let {
            UtauNoteVibratoParams(
                length = it.length,
                period = it.period,
                depth = it.depth,
                fadeIn = it.`in`,
                fadeOut = it.out,
                phaseShift = it.shift,
                shift = it.drift
            )
        }
        return OpenUtauNotePitchData(points, vibrato)
    }

    private fun getValidatedNotes(
        notes: List<model.Note>,
        notePitches: List<OpenUtauNotePitchData>?
    ): Pair<List<model.Note>, List<OpenUtauNotePitchData>?> {
        val validatedNotes = mutableListOf<model.Note>()
        val validatedNotePitches = if (notePitches != null) mutableListOf<OpenUtauNotePitchData>() else null
        var pos = 0L
        for (i in notes.indices) {
            val note = notes[i]
            if (note.tickOn >= pos) {
                validatedNotes.add(note)
                notePitches?.get(i)?.let { validatedNotePitches?.add(it) }
                pos = note.tickOff
            }
        }
        return validatedNotes to validatedNotePitches
    }

    fun generate(project: model.Project, features: List<Feature>): ExportResult {
        val yamlText = generateContent(project, features)
        val blob = Blob(arrayOf(yamlText), BlobPropertyBag("application/octet-stream"))
        val name = project.name + Format.Ustx.extension
        return ExportResult(
            blob,
            name,
            listOfNotNull(
                if (features.contains(Feature.ConvertPitch)) ExportNotification.PitchDataExported else null,
                if (project.tempos.count() > 1) ExportNotification.TempoChangeIgnored else null,
                if (project.timeSignatures.count() > 1) ExportNotification.TimeSignatureChangeIgnored else null
            )
        )
    }

    private fun generateContent(project: model.Project, features: List<Feature>): String {
        val templateYamlText = Resources.ustxTemplate
        val templateYaml = JsYaml.load(templateYamlText)
        val templateJsonText = JSON.stringify(templateYaml)
        val template = jsonSerializer.decodeFromString(Project.serializer(), templateJsonText)
        val trackTemplate = template.tracks.first()
        val tracks = project.tracks.map {
            trackTemplate.copy()
        }
        val voicePartTemplate = template.voiceParts.first()
        val voiceParts = project.tracks.map {
            generateVoicePart(voicePartTemplate, it, features)
        }
        val ustx = template.copy(
            name = project.name,
            bpm = project.tempos.first().bpm,
            beatPerBar = project.timeSignatures.first().numerator,
            beatUnit = project.timeSignatures.first().denominator,
            tracks = tracks,
            voiceParts = voiceParts
        )
        val jsonText = jsonSerializer.encodeToString(Project.serializer(), ustx)
        return JsYaml.dump(JSON.parse(jsonText))
    }

    private fun generateVoicePart(template: VoicePart, track: model.Track, features: List<Feature>): VoicePart {
        val noteTemplate = template.notes.first()
        val notes =
            listOfNotNull(track.notes.firstOrNull()?.let { generateNote(noteTemplate, null, it) }) +
                    track.notes.zipWithNext().map { (lastNote, thisNote) ->
                        generateNote(noteTemplate, lastNote, thisNote)
                    }

        val curves = mutableListOf<Curve>()
        if (features.contains(Feature.ConvertPitch)) {
            val points = track.pitch?.toOpenUtauPitchData().orEmpty()
            if (points.isNotEmpty()) {
                val xs = points.map { it.first }
                val ys = points.map { it.second }
                val curve = Curve(xs, ys, PITCH_CURVE_ABBR)
                curves.add(curve)
            }
        }
        return template.copy(
            name = track.name,
            trackNo = track.id,
            position = 0L,
            notes = notes,
            curves = curves
        )
    }

    private fun generateNote(
        template: Note,
        lastNote: model.Note?,
        thisNote: model.Note
    ): Note {
        val firstPitchPointValue = if (lastNote?.tickOff == thisNote.tickOn) {
            (lastNote.key - thisNote.key) * 10.0 // the unit is 10 cents
        } else {
            0.0
        }
        val pitchPoints = template.pitch.data.mapIndexed { index: Int, datum: Datum ->
            if (index == 0) datum.copy(y = firstPitchPointValue) else datum.copy()
        }
        val pitch = template.pitch.copy(data = pitchPoints)
        return Note(
            position = thisNote.tickOn,
            duration = thisNote.length,
            tone = thisNote.key,
            pitch = pitch,
            lyric = thisNote.lyric,
            vibrato = template.vibrato.copy()
        )
    }

    private val jsonSerializer = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class Project(
        val name: String,
        val comment: String,
        @SerialName("output_dir") val outputDir: String,
        @SerialName("cache_dir") val cacheDir: String,
        @SerialName("ustx_version") val ustxVersion: Double,
        val bpm: Double,
        @SerialName("beat_per_bar") val beatPerBar: Int,
        @SerialName("beat_unit") val beatUnit: Int,
        val resolution: Int,
        val expressions: Map<String, Expression>,
        val tracks: List<Track>,
        @SerialName("voice_parts") val voiceParts: List<VoicePart>
    )

    @Serializable
    private data class Expression(
        val name: String,
        val abbr: String,
        val type: String,
        val min: Int,
        val max: Int,
        @SerialName("default_value") val defaultValue: Int,
        @SerialName("is_flag") val isFlag: Boolean,
        val flag: String? = null,
        val options: List<String>? = null
    )

    @Serializable
    private data class Track(
        val phonemizer: String,
        val mute: Boolean,
        val solo: Boolean,
        val volume: Double
    )

    @Serializable
    private data class VoicePart(
        val name: String,
        val comment: String,
        @SerialName("track_no") val trackNo: Int,
        val position: Long,
        val notes: List<Note>,
        val curves: List<Curve>
    )

    @Serializable
    private data class Note(
        val position: Long,
        val duration: Long,
        val tone: Int,
        val lyric: String,
        val pitch: Pitch,
        val vibrato: Vibrato
    )

    @Serializable
    private data class Pitch(
        val data: List<Datum>,
        @SerialName("snap_first") val snapFirst: Boolean
    )

    @Serializable
    private data class Datum(
        val x: Double,
        val y: Double,
        val shape: String
    )

    @Serializable
    private data class Vibrato(
        val length: Double,
        val period: Double,
        val depth: Double,
        val `in`: Double,
        val out: Double,
        val shift: Double,
        val drift: Double
    )

    @Serializable
    private data class Curve(
        val xs: List<Long>,
        val ys: List<Double>,
        val abbr: String
    )
}
