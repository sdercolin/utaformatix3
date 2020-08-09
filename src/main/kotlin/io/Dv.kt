package io

import model.Format
import model.ImportWarning
import model.Note
import model.Project
import model.Tempo
import model.TickCounter
import model.TimeSignature
import model.Track
import org.w3c.files.File
import util.ArrayBufferReader
import util.nameWithoutExtension
import util.readAsArrayBuffer

object Dv {

    suspend fun parse(file: File): Project {
        val reader = ArrayBufferReader(file.readAsArrayBuffer())
        val warnings = mutableListOf<ImportWarning>()

        // header
        reader.skip(48)

        // tempo
        val tempoCount = reader.readInt()
        var tempos = mutableListOf<Tempo>()
        repeat(tempoCount) {
            val tickPosition = reader.readInt().toLong()
            val bpm = reader.readInt().toDouble() / 100
            tempos.add(Tempo(tickPosition, bpm))
        }
        reader.skip(4)

        // time signature
        val timeSignatureCount = reader.readInt()
        var timeSignatures = mutableListOf<TimeSignature>()
        repeat(timeSignatureCount) {
            val measurePosition = reader.readInt()
            val numerator = reader.readInt()
            val denominator = reader.readInt()
            timeSignatures.add(TimeSignature(measurePosition, numerator, denominator))
        }
        val tickPrefix = getTickPrefix(timeSignatures)
        tempos = tempos.cleanup(tickPrefix, warnings).toMutableList()
        timeSignatures = timeSignatures.cleanup(warnings).toMutableList()

        // tracks
        val trackCount = reader.readInt()
        var tracks = mutableListOf<Track>()
        repeat(trackCount) {
            parseTrack(tickPrefix, reader)?.let { track ->
                tracks.add(track.validateNotes())
            }
        }
        tracks = tracks.mapIndexed { index, track -> track.copy(id = index) }.toMutableList()

        return Project(
            format = Format.DV,
            inputFiles = listOf(file),
            name = file.nameWithoutExtension,
            tracks = tracks,
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = FIXED_MEASURE_PREFIX,
            importWarnings = listOf()
        )
    }

    private fun parseTrack(tickPrefix: Long, reader: ArrayBufferReader): Track? {
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
                        tickOn = segmentStart + noteStart - tickPrefix,
                        tickOff = segmentStart + noteStart - tickPrefix + noteLength
                    )
                )
            }
            skipRestOfSegment(reader)
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

    private fun skipRestOfSegment(reader: ArrayBufferReader) {
        repeat(7) {
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

    private fun getTickPrefix(timeSignatures: List<TimeSignature>): Long {
        val counter = TickCounter()
        timeSignatures
            .map { it.copy(measurePosition = it.measurePosition - STARTING_MEASURE_POSITION) }
            .filter { it.measurePosition < FIXED_MEASURE_PREFIX }
            .forEach { counter.goToMeasure(it) }
        counter.goToMeasure(FIXED_MEASURE_PREFIX)
        return counter.tick
    }

    private fun List<TimeSignature>.cleanup(warnings: MutableList<ImportWarning>): List<TimeSignature> {
        console.log(this.toList())
        val results = this
            .map { it.copy(measurePosition = it.measurePosition - STARTING_MEASURE_POSITION - FIXED_MEASURE_PREFIX) }
            .toMutableList()

        console.log(results.toList())
        // Delete all time signatures inside prefix, add apply the last as the first
        val firstTimeSignatureIndex = results
            .last { it.measurePosition <= 0 }
            .let { results.indexOf(it) }
        repeat(firstTimeSignatureIndex) {
            val removed = results.removeAt(0)
            warnings.add(ImportWarning.TimeSignatureIgnoredInPreMeasure(removed))
        }
        console.log(results.toList())
        results[0] = results[0].copy(measurePosition = 0)
        return results
    }

    private fun List<Tempo>.cleanup(tickPrefix: Long, warnings: MutableList<ImportWarning>): List<Tempo> {
        val results = this
            .map { it.copy(tickPosition = it.tickPosition - tickPrefix) }
            .toMutableList()

        // Delete all tempo tags inside prefix, add apply the last as the first
        val firstTempoIndex = results
            .last { it.tickPosition <= 0 }
            .let { results.indexOf(it) }
        repeat(firstTempoIndex) {
            val removed = results.removeAt(0)
            warnings.add(ImportWarning.TempoIgnoredInPreMeasure(removed))
        }
        results[0] = results[0].copy(tickPosition = 0)
        return results
    }

    private const val STARTING_MEASURE_POSITION = -3
    private const val FIXED_MEASURE_PREFIX = 4
}
