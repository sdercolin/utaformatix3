@file:OptIn(ExperimentalSerializationApi::class)

package io

import com.sdercolin.utaformatix.data.Document
import com.sdercolin.utaformatix.data.Note
import com.sdercolin.utaformatix.data.Pitch
import com.sdercolin.utaformatix.data.Project
import com.sdercolin.utaformatix.data.Tempo
import com.sdercolin.utaformatix.data.TimeSignature
import com.sdercolin.utaformatix.data.Track
import com.sdercolin.utaformatix.data.UtaFormatixDataVersion
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import model.ExportNotification
import model.ExportResult
import model.Feature
import model.Format
import model.ImportParams
import model.ImportWarning
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.File
import process.validateNotes
import util.readText

/**
 * IO logics for UtaFormatix Data.
 * @see [com.sdercolin.utaformatix.data.Document]
 */
object UfData {

    suspend fun parse(file: File, params: ImportParams): model.Project {
        val text = file.readText()
        val document = jsonSerializer.decodeFromString(Document.serializer(), text)
        val version = document.formatVersion
        val importWarnings = mutableListOf<ImportWarning>()
        if (version > UtaFormatixDataVersion) {
            importWarnings.add(
                ImportWarning.IncompatibleFormatSerializationVersion(
                    currentVersion = UtaFormatixDataVersion.toString(),
                    dataVersion = version.toString(),
                ),
            )
        }
        return model.Project(
            format = format,
            inputFiles = listOf(file),
            name = document.project.name,
            tracks = document.project.tracks.mapIndexed { index, track -> parseTrack(index, track, params) },
            timeSignatures = document.project.timeSignatures.map(::parseTimeSignature),
            tempos = document.project.tempos.map(::parseTempo),
            measurePrefix = document.project.measurePrefix,
            importWarnings = importWarnings,
        )
    }

    private fun parseTrack(index: Int, track: Track, params: ImportParams): model.Track {
        val notes = track.notes.mapIndexed { noteIndex, note ->
            model.Note(
                id = noteIndex,
                key = note.key,
                lyric = note.lyric,
                tickOn = note.tickOn,
                tickOff = note.tickOff,
                phoneme = note.phoneme,
            )
        }
        val pitch = if (params.simpleImport) null else track.pitch?.let {
            model.Pitch(data = it.ticks.zip(it.values), isAbsolute = it.isAbsolute)
        }
        return model.Track(
            id = index,
            name = track.name,
            notes = notes,
            pitch = pitch,
        ).validateNotes()
    }

    private fun parseTimeSignature(timeSignature: TimeSignature): model.TimeSignature {
        return model.TimeSignature(
            measurePosition = timeSignature.measurePosition,
            numerator = timeSignature.numerator,
            denominator = timeSignature.denominator,
        )
    }

    private fun parseTempo(tempo: Tempo): model.Tempo {
        return model.Tempo(
            tickPosition = tempo.tickPosition,
            bpm = tempo.bpm,
        )
    }

    fun generate(project: model.Project, features: List<Feature>): ExportResult {
        val document = Document(
            formatVersion = UtaFormatixDataVersion,
            project = Project(
                name = project.name,
                tracks = project.tracks.map { generateTrack(it, features) },
                timeSignatures = project.timeSignatures.map(::generateTimeSignature),
                tempos = project.tempos.map(::generateTempo),
                measurePrefix = project.measurePrefix,
            ),
        )
        val text = jsonSerializer.encodeToString(Document.serializer(), document)
        val blob = Blob(arrayOf(text), BlobPropertyBag("application/octet-stream"))
        val name = format.getFileName(project.name)
        return ExportResult(
            blob,
            name,
            listOfNotNull(
                if (features.contains(Feature.ConvertPitch)) ExportNotification.PitchDataExported else null,
            ),
        )
    }

    private fun generateTrack(track: model.Track, features: List<Feature>): Track {
        val notes = track.notes.map {
            Note(
                key = it.key,
                lyric = it.lyric,
                tickOn = it.tickOn,
                tickOff = it.tickOff,
                phoneme = it.phoneme,
            )
        }
        val pitch = if (features.contains(Feature.ConvertPitch)) {
            track.pitch?.let {
                Pitch(
                    ticks = it.data.map { point -> point.first },
                    values = it.data.map { point -> point.second },
                    isAbsolute = it.isAbsolute,
                )
            }
        } else null
        return Track(
            name = track.name,
            notes = notes,
            pitch = pitch,
        )
    }

    private fun generateTimeSignature(timeSignature: model.TimeSignature): TimeSignature {
        return TimeSignature(
            measurePosition = timeSignature.measurePosition,
            numerator = timeSignature.numerator,
            denominator = timeSignature.denominator,
        )
    }

    private fun generateTempo(tempo: model.Tempo): Tempo {
        return Tempo(
            tickPosition = tempo.tickPosition,
            bpm = tempo.bpm,
        )
    }

    private val jsonSerializer = Json {
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val format = Format.UfData
}
