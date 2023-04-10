package io

import org.khronos.webgl.Uint8Array
import org.w3c.files.File
import util.MidiUtil
import util.asByteTypedArray
import util.decode
import util.readAsArrayBuffer

object Mid {

    suspend fun loadMidiTracks(file: File): Array<dynamic> {
        val bytes = file.readAsArrayBuffer()
        val midiParser = external.require("midi-parser-js")
        val midi = midiParser.parse(Uint8Array(bytes))

        return midi.track as Array<dynamic>
    }

    fun extractVsqTextsFromMetaEvents(midiTracks: Array<dynamic>): List<String> {
        return midiTracks.drop(1)
            .map { track ->
                (track.event as Array<dynamic>)
                    .fold("") { accumulator, element ->
                        val metaType = MidiUtil.MetaType.parse(element.metaType as? Byte)
                        if (metaType != MidiUtil.MetaType.Text) accumulator
                        else {
                            var text = element.data as String
                            text = text.asByteTypedArray().decode("SJIS")
                            text = text.drop(3)
                            text = text.drop(text.indexOf(':') + 1)
                            accumulator + text
                        }
                    }
            }
    }
}
