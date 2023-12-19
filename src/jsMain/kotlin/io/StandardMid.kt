package io

import exception.IllegalFileException
import external.Encoding
import model.DEFAULT_LYRIC
import model.ExportResult
import model.Format
import model.ImportWarning
import model.Note
import model.Project
import model.Track
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.File
import process.validateNotes
import util.MidiUtil
import util.addBlock
import util.addIntVariableLengthBigEndian
import util.addString
import util.asByteTypedArray
import util.decode
import util.encode

object StandardMid {

    suspend fun parse(file: File): Project {
        val midi = Mid.parseMidi(file)
        if (midi == false) {
            throw IllegalFileException.IllegalMidiFile()
        }
        val timeDivision = midi.timeDivision as Int
        val midiTracks = midi.track as Array<dynamic>

        val warnings = mutableListOf<ImportWarning>()
        val (tempos, timeSignatures, tickPrefix) = Mid.parseMasterTrack(
            timeDivision,
            midiTracks.first(),
            measurePrefix = 0,
            warnings,
        )

        val tracks = midiTracks.drop(1).mapIndexed { index, midiTrack ->
            parseTrack(index, timeDivision, tickPrefix, midiTrack)
        }

        return Project(
            format = format,
            inputFiles = listOf(file),
            name = file.name,
            tracks = tracks,
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = 0,
            importWarnings = warnings,
        )
    }

    private fun parseTrack(
        id: Int,
        timeDivision: Int,
        tickPrefix: Long,
        midiTrack: dynamic,
    ): Track {
        var trackName = "Track ${id + 1}"
        val notes = mutableListOf<Note>()

        var tickPosition = tickPrefix
        var pendingLyric: String? = null
        var pendingNoteHead: Pair<Note, Int>? = null

        val pendingNotesHeadsWithLyric = mutableMapOf<Int, Note>()

        val events = midiTrack.event as Array<dynamic>
        for (event in events) {
            val delta = MidiUtil.convertInputTimeToStandardTime(event.deltaTime as Int, timeDivision)
            if (delta > 0) {
                pendingNoteHead?.let { (noteHead, channel) ->
                    pendingNotesHeadsWithLyric[channel]?.let {
                        // cutting note
                        notes += it.copy(tickOff = tickPosition + delta)
                    }
                    pendingNotesHeadsWithLyric[channel] = noteHead.copy(lyric = pendingLyric ?: DEFAULT_LYRIC)
                }
                pendingLyric = null
                pendingNoteHead = null
            }
            tickPosition += delta
            when (MidiUtil.MetaType.parse(event.metaType as? Byte)) {
                MidiUtil.MetaType.Lyric -> {
                    val lyricBytes = (event.data as String).asByteTypedArray()
                    val detectedEncoding = Encoding.detect(lyricBytes)
                    pendingLyric = lyricBytes.decode(detectedEncoding)
                }
                MidiUtil.MetaType.Text -> {
                    if (pendingLyric == null) {
                        val textBytes = (event.data as String).asByteTypedArray()
                        val detectedEncoding = Encoding.detect(textBytes)
                        pendingLyric = textBytes.decode(detectedEncoding)
                    }
                }
                MidiUtil.MetaType.TrackName -> {
                    val trackNameBytes = (event.data as String).asByteTypedArray()
                    val detectedEncoding = Encoding.detect(trackNameBytes)
                    trackName = trackNameBytes.decode(detectedEncoding)
                }
                else -> when (MidiUtil.EventType.parse(event.type as? Byte)) {
                    MidiUtil.EventType.NoteOn -> {
                        val channel = event.channel as Int
                        val key = event.data[0] as Int
                        pendingNoteHead = Note(
                            id = 0,
                            tickOn = tickPosition,
                            tickOff = tickPosition,
                            key = key,
                            lyric = DEFAULT_LYRIC,
                        ) to channel
                    }
                    MidiUtil.EventType.NoteOff -> {
                        val channel = event.channel as Int
                        pendingNotesHeadsWithLyric[channel]?.let {
                            notes += it.copy(tickOff = tickPosition)
                        }
                        pendingNotesHeadsWithLyric.remove(channel)
                    }
                    else -> Unit
                }
            }
        }
        return Track(
            id = id,
            name = trackName,
            notes = notes,
        ).validateNotes()
    }

    fun generate(project: Project): ExportResult {
        val content = Mid.generateContent(project) { track, tickPrefix, _ ->
            generateTrack(track, tickPrefix)
        }
        val blob = Blob(arrayOf(content), BlobPropertyBag("application/octet-stream"))
        val name = format.getFileName(project.name)
        return ExportResult(
            blob,
            name,
            listOf(),
        )
    }

    private fun generateTrack(
        track: Track,
        tickPrefix: Int,
    ): List<Byte> {
        val bytes = mutableListOf<Byte>()
        bytes.add(0x00)
        bytes.addAll(MidiUtil.MetaType.TrackName.eventHeaderBytes)
        bytes.addString(track.name, Mid.IS_LITTLE_ENDIAN, lengthInVariableLength = true)

        var tickPosition = -tickPrefix.toLong()

        track.validateNotes().notes.forEach { note ->

            var delta = note.tickOn - tickPosition
            tickPosition = note.tickOn

            // write lyric event first
            val lyric = note.lyric.ifBlank { DEFAULT_LYRIC }.encode("UTF-8").toList()
            bytes.addIntVariableLengthBigEndian(delta.toInt())
            bytes.addAll(MidiUtil.MetaType.Lyric.eventHeaderBytes)
            bytes.addBlock(lyric, Mid.IS_LITTLE_ENDIAN, lengthInVariableLength = true)

            // write note on event
            bytes.addIntVariableLengthBigEndian(0) // delta is 0
            bytes.add(MidiUtil.EventType.NoteOn.getStatusByte(channel = 0))
            // note number
            bytes.add(note.key.coerceIn(0..127).toByte())
            // velocity, always 127
            bytes.add(127.toByte())

            // write note off event
            delta = note.tickOff - tickPosition
            tickPosition = note.tickOff

            bytes.addIntVariableLengthBigEndian(delta.toInt())
            bytes.add(MidiUtil.EventType.NoteOff.getStatusByte(channel = 0))
            // note number
            bytes.add(note.key.coerceIn(0..127).toByte())
            // velocity, always 0
            bytes.add(0.toByte())
        }

        bytes.add(0x00)
        bytes.addAll(MidiUtil.MetaType.EndOfTrack.eventHeaderBytes)
        bytes.add(0x00)
        return bytes
    }

    private val format = Format.StandardMid
}
