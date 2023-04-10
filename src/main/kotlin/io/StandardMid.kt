package io

import external.Encoding
import model.Format
import model.ImportWarning
import model.Note
import model.Project
import model.Track
import org.w3c.files.File
import process.validateNotes
import util.MidiUtil
import util.asByteTypedArray
import util.decode

object StandardMid {

    suspend fun parse(file: File): Project {
        val midi = Mid.parseMidi(file)
        console.log(midi)
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
            format = Format.StandardMid,
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
                    pendingNotesHeadsWithLyric[channel] = noteHead.copy(lyric = pendingLyric ?: "")
                }
                pendingLyric = null
                pendingNoteHead = null
            }
            tickPosition += delta
            if (MidiUtil.MetaType.parse(event.metaType as? Byte) == MidiUtil.MetaType.Lyric) {
                val lyricBytes = (event.data as String).asByteTypedArray()
                val detectedEncoding = Encoding.detect(lyricBytes)
                pendingLyric = lyricBytes.decode(detectedEncoding)
            } else {
                val eventType = MidiUtil.EventType.parse(event.type as? Byte)
                if (eventType == MidiUtil.EventType.NoteOn) {
                    val channel = event.channel as Int
                    val key = event.data[0] as Int
                    pendingNoteHead = Note(
                        id = 0,
                        tickOn = tickPosition,
                        tickOff = tickPosition,
                        key = key,
                        lyric = "",
                    ) to channel
                } else if (eventType == MidiUtil.EventType.NoteOff) {
                    val channel = event.channel as Int
                    pendingNotesHeadsWithLyric[channel]?.let {
                        notes += it.copy(tickOff = tickPosition)
                    }
                    pendingNotesHeadsWithLyric.remove(channel)
                }
            }
        }
        return Track(
            id = id,
            name = "Track ${id + 1}",
            notes = notes,
        ).validateNotes()
    }
}
