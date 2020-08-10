package io

import kotlinx.serialization.toUtf8Bytes
import model.ExportResult
import model.Format
import model.ImportWarning
import model.Note
import model.Project
import model.Tempo
import model.TickCounter
import model.TimeSignature
import model.Track
import org.khronos.webgl.Uint8Array
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.File
import util.ArrayBufferReader
import util.addBlock
import util.addInt
import util.addList
import util.addListBlock
import util.addString
import util.nameWithoutExtension
import util.readAsArrayBuffer
import kotlin.math.max

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

        // time signature
        reader.skip(4)
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
            importWarnings = warnings
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
                val noteKey = convertNoteKey(reader.readInt())
                reader.skip(4)
                val lyric = reader.readString()
                reader.readString() // lyric in Chinese character
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
        val results = this
            .map { it.copy(measurePosition = it.measurePosition - STARTING_MEASURE_POSITION - FIXED_MEASURE_PREFIX) }
            .toMutableList()

        // Delete all time signatures inside prefix, add apply the last as the first
        val firstTimeSignatureIndex = results
            .last { it.measurePosition <= 0 }
            .let { results.indexOf(it) }
        repeat(firstTimeSignatureIndex) {
            val removed = results.removeAt(0)
            warnings.add(ImportWarning.TimeSignatureIgnoredInPreMeasure(removed))
        }
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

    fun generate(project: Project): ExportResult {
        val contentBytes = generateContent(project)
        val blob = Blob(arrayOf(contentBytes), BlobPropertyBag("application/octet-stream"))
        return ExportResult(blob, project.name + Format.DV.extension, listOf())
    }

    private fun generateContent(project: Project): Uint8Array {
        val bytes = mutableListOf<Byte>()
        bytes.addAll(header)
        val tickPrefix = project.timeSignatures.first().ticksInMeasure.toLong() * FIXED_MEASURE_PREFIX
        val mainBlock = mutableListOf<Byte>().apply {
            addAll("ext1ext2ext3ext4ext5ext6ext7".toUtf8Bytes().toList())
            addListBlock(generateTempos(project.tempos, tickPrefix))
            addListBlock(generateTimeSignatures(project.timeSignatures))
            addList(project.tracks.map { generateTrack(it, tickPrefix) })
        }
        bytes.addBlock(mainBlock)
        return Uint8Array(bytes.toTypedArray())
    }

    private fun generateTempos(tempos: List<Tempo>, tickPrefix: Long): List<List<Byte>> {
        val firstTempo = tempos.first().copy(tickPosition = 0)
        val restOfTempos = tempos.drop(1).map { it.copy(tickPosition = it.tickPosition + tickPrefix) }
        return (listOf(firstTempo) + restOfTempos)
            .map {
                mutableListOf<Byte>().apply {
                    addInt(it.tickPosition.toInt())
                    addInt((it.bpm * 100).toInt())
                }
            }
    }

    private fun generateTimeSignatures(timeSignatures: List<TimeSignature>): List<List<Byte>> {
        val firstTimeSignature = timeSignatures.first().copy(measurePosition = STARTING_MEASURE_POSITION)
        val restOfTimeSignatures = timeSignatures.drop(1).map {
            it.copy(measurePosition = it.measurePosition + FIXED_MEASURE_PREFIX + STARTING_MEASURE_POSITION)
        }
        return (listOf(firstTimeSignature) + restOfTimeSignatures)
            .map {
                mutableListOf<Byte>().apply {
                    addInt(it.measurePosition)
                    addInt(it.numerator)
                    addInt(it.denominator)
                }
            }
    }

    private fun generateTrack(track: Track, tickPrefix: Long): List<Byte> {
        val segmentBytes = mutableListOf<Byte>().apply {
            addInt(tickPrefix.toInt())
            val lastNoteTickOff = track.notes.lastOrNull()?.tickOff?.toInt() ?: 0
            addInt(max(lastNoteTickOff, MIN_SEGMENT_LENGTH))
            addString(track.name)
            addString("")
            addListBlock(track.notes.map { generateNote(it) })
            addAll(segmentDefaultParameterData)
        }
        return mutableListOf<Byte>().apply {
            addInt(0)
            addString(track.name)
            add(0x00)
            add(0x00)
            addInt(DEFAULT_VOLUME)
            addInt(0)
            addListBlock(listOf(segmentBytes))
        }
    }

    private fun generateNote(note: Note): List<Byte> {
        return mutableListOf<Byte>().apply {
            addInt(note.tickOn.toInt())
            addInt(note.length.toInt())
            addInt(convertNoteKey(note.key))
            addInt(0)
            addString(note.lyric)
            addString(note.lyric)
            add(0x00)
            addBlock(
                mutableListOf<Byte>().apply {
                    addListBlock(listOf())
                    addListBlock(listOf())
                    addListBlock(listOf())
                }
            )
            addBlock(listOf())
            addAll(noteUnknownPhonemes)
            addInt(8)
            addInt(5)
            addInt(16)
            addInt(16)
            addInt(-1)
            addString("")
            addInt(-1)
        }
    }

    private fun convertNoteKey(key: Int) = NOTE_KEY_SUM - key
    private const val NOTE_KEY_SUM = 115

    private const val STARTING_MEASURE_POSITION = -3
    private const val FIXED_MEASURE_PREFIX = 4
    private val header = listOf(
        0x53, 0x48, 0x41, 0x52, 0x50, 0x4B, 0x45, 0x59, 0x05, 0x00, 0x00, 0x00
    ).map { it.toByte() }
    private const val DEFAULT_VOLUME = 30
    private const val MIN_SEGMENT_LENGTH = 480 * 4
    private val segmentDefaultParameterData = listOf(
        0x14, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF,
        0x80, 0x00, 0x00, 0x00, 0x01, 0xB0, 0x04, 0x00, 0x80, 0x00, 0x00, 0x00,
        0x14, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0x01, 0xB0, 0x04, 0x00, 0xFF, 0xFF, 0xFF, 0xFF,
        0x14, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF,
        0x80, 0x00, 0x00, 0x00, 0x01, 0xB0, 0x04, 0x00, 0x80, 0x00, 0x00, 0x00,
        0x14, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF,
        0x80, 0x00, 0x00, 0x00, 0x01, 0xB0, 0x04, 0x00, 0x80, 0x00, 0x00, 0x00,
        0x14, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF,
        0x80, 0x00, 0x00, 0x00, 0x01, 0xB0, 0x04, 0x00, 0x80, 0x00, 0x00, 0x00,
        0x14, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF,
        0x80, 0x00, 0x00, 0x00, 0x01, 0xB0, 0x04, 0x00, 0x80, 0x00, 0x00, 0x00,
        0x14, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF,
        0x00, 0x00, 0x00, 0x00, 0x01, 0xB0, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00
    ).map { it.toByte() }
    private val noteUnknownPhonemes = listOf(
        0x00, 0x00, 0x00, 0x80, 0x3F, 0x00, 0x00, 0x00, 0x80, 0x3F, 0x00, 0x00,
        0x80, 0x3F, 0x00, 0x00, 0x80, 0x3F
    ).map { it.toByte() }
}
