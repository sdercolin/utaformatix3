package io

import model.Format
import model.Note
import model.Project
import model.Tempo
import model.TimeSignature
import model.Track
import org.w3c.files.File
import util.ArrayBufferReader
import util.nameWithoutExtension
import util.readAsArrayBuffer

object Dv {

    suspend fun parse(file: File): Project {
        val reader = ArrayBufferReader(file.readAsArrayBuffer())

        // header
        reader.skip(48)

        // tempo
        val tempoCount = reader.readInt()
        val tempos = mutableListOf<Tempo>()
        repeat(tempoCount) {
            val tickPosition = reader.readInt().toLong()
            val bpm = reader.readInt().toDouble() / 100
            tempos.add(Tempo(tickPosition, bpm))
        }
        console.log(tempos)
        reader.skip(4)

        // time signature
        val timeSignatureCount = reader.readInt()
        val timeSignatures = mutableListOf<TimeSignature>()
        repeat(timeSignatureCount) {
            // TODO: handle pre-measures, measure starting from -3
            val measurePosition = reader.readInt()
            val numerator = reader.readInt()
            val denominator = reader.readInt()
            timeSignatures.add(TimeSignature(measurePosition, numerator, denominator))
        }
        console.log(timeSignatures)

        // tracks
        val trackCount = reader.readInt()
        var tracks = mutableListOf<Track>()
        repeat(trackCount) {
            parseTrack(reader)?.let { track ->
                tracks.add(track.validateNotes())
            }
        }
        tracks = tracks.mapIndexed { index, track -> track.copy(id = index) }.toMutableList()
        console.log(tracks)

        return Project(
            format = Format.DV,
            inputFiles = listOf(file),
            name = file.nameWithoutExtension,
            tracks = tracks,
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = 0,
            importWarnings = listOf()
        )
    }

    private fun parseTrack(reader: ArrayBufferReader): Track? {
        val trackType = reader.readInt()
        if (trackType != 0) {
            skipRestOfInstTrack(reader)
            return null
        }
        val trackName = reader.readString()
        reader.skip(14)

        val notes = mutableListOf<Note>()
        val segmentCount = reader.readInt()
        repeat(segmentCount) {
            val segmentStart = reader.readInt()
            reader.readInt() // segment length
            reader.readString() // segment name
            reader.readString() // voice bank name
            reader.skip(4)
            val noteCount = reader.readInt()
            repeat(noteCount) {
                val noteStart = reader.readInt()
                val noteLength = reader.readInt()
                val noteKey = 115 - reader.readInt()
                reader.skip(4)
                reader.readString() // lyric in Chinese character
                val lyric = reader.readString()
                skipRestOfNote(reader)
                notes.add(
                    Note(
                        id = 0,
                        key = noteKey,
                        lyric = lyric,
                        tickOn = (segmentStart + noteStart).toLong(),
                        tickOff = (segmentStart + noteStart + noteLength).toLong()
                    )
                )
            }
        }
        return Track(
            id = 0,
            name = trackName,
            notes = notes
        )
    }

    private fun skipRestOfInstTrack(reader: ArrayBufferReader) {
        reader.readBytes()
        reader.skip(14)
        if (reader.readInt() > 0) {
            reader.skip(8)
            reader.readBytes()
            reader.readBytes()
        }
    }

    private fun skipRestOfNote(reader: ArrayBufferReader) {
        reader.skip(1)
        reader.readBytes()
        reader.readBytes()
        reader.skip(38)
        reader.readBytes()
        reader.skip(4)
    }
}
