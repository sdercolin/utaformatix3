package io

import model.ImportWarning
import model.Tempo
import model.TickCounter
import model.TimeSignature
import org.khronos.webgl.Uint8Array
import org.w3c.files.File
import util.MidiUtil
import util.asByteTypedArray
import util.decode
import util.readAsArrayBuffer

object Mid {

    suspend fun parseMidi(file: File): dynamic {
        val bytes = file.readAsArrayBuffer()
        val midiParser = external.require("midi-parser-js")
        return midiParser.parse(Uint8Array(bytes))
    }

    fun parseMasterTrack(
        timeDivision: Int,
        masterTrack: dynamic,
        measurePrefix: Int,
        warnings: MutableList<ImportWarning>,
    ): Triple<List<Tempo>, List<TimeSignature>, Long> {
        val events = masterTrack.event as Array<dynamic>
        var tickPosition = 0
        val tickCounter = TickCounter()
        val rawTempos = mutableListOf<Tempo>()
        val rawTimeSignatures = mutableListOf<TimeSignature>()
        for (event in events) {
            tickPosition += MidiUtil.convertInputTimeToStandardTime(event.deltaTime as Int, timeDivision)
            when (MidiUtil.MetaType.parse(event.metaType as? Byte)) {
                MidiUtil.MetaType.Tempo -> {
                    rawTempos.add(
                        Tempo(
                            tickPosition.toLong(),
                            MidiUtil.convertMidiTempoToBpm(event.data as Int),
                        ),
                    )
                }
                MidiUtil.MetaType.TimeSignature -> {
                    val (numerator, denominator) = MidiUtil.parseMidiTimeSignature(event.data)
                    tickCounter.goToTick(tickPosition.toLong(), numerator, denominator)
                    rawTimeSignatures.add(
                        TimeSignature(
                            tickCounter.measure,
                            numerator,
                            denominator,
                        ),
                    )
                }
                else -> {
                }
            }
        }

        if (rawTimeSignatures.isEmpty()) {
            rawTimeSignatures.add(TimeSignature.default)
            warnings.add(ImportWarning.TimeSignatureNotFound)
        }

        if (rawTempos.isEmpty()) {
            rawTempos.add(Tempo.default)
            warnings.add(ImportWarning.TempoNotFound)
        }

        // Calculate before time signatures are cleaned up
        val tickPrefix = getTickPrefix(rawTimeSignatures, measurePrefix)

        val timeSignatures = rawTimeSignatures
            .map { it.copy(measurePosition = it.measurePosition - measurePrefix) }
            .toMutableList()

        // Delete all time signatures inside prefix, add apply the last as the first
        val firstTimeSignatureIndex = timeSignatures
            .last { it.measurePosition <= 0 }
            .let { timeSignatures.indexOf(it) }
        repeat(firstTimeSignatureIndex) {
            val removed = timeSignatures.removeAt(0)
            warnings.add(ImportWarning.TimeSignatureIgnoredInPreMeasure(removed))
        }
        timeSignatures[0] = timeSignatures[0].copy(measurePosition = 0)

        // Delete all tempo tags inside prefix, add apply the last as the first
        val tempos = rawTempos
            .map { it.copy(tickPosition = it.tickPosition - tickPrefix) }
            .toMutableList()
        val firstTempoIndex = tempos
            .last { it.tickPosition <= 0 }
            .let { tempos.indexOf(it) }
        repeat(firstTempoIndex) {
            val removed = tempos.removeAt(0)
            warnings.add(ImportWarning.TempoIgnoredInPreMeasure(removed))
        }
        tempos[0] = tempos[0].copy(tickPosition = 0)

        return Triple(
            tempos,
            timeSignatures,
            tickPrefix,
        )
    }

    private fun getTickPrefix(timeSignatures: List<TimeSignature>, measurePrefix: Int): Long {
        val counter = TickCounter()
        timeSignatures
            .filter { it.measurePosition < measurePrefix }
            .forEach { counter.goToMeasure(it) }
        counter.goToMeasure(measurePrefix)
        return counter.tick
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
