package io

import external.require
import model.Format
import model.ImportWarning
import model.Project
import model.Tempo
import model.TickCounter
import model.TimeSignature
import org.khronos.webgl.Uint8Array
import org.w3c.files.File
import util.MidiUtil
import util.asByteTypedArray
import util.decode
import util.linesNotBlank
import util.nameWithoutExtension
import util.readAsArrayBuffer

object Vsq {
    suspend fun parse(file: File): Project {
        val bytes = file.readAsArrayBuffer()
        val midiParser = require("midi-parser-js")
        val midi = midiParser.parse(Uint8Array(bytes))
        console.log(midi)

        val warnings = mutableListOf<ImportWarning>()

        val midiTracks = (midi.track as Array<dynamic>)
        val tracksAsText = extractTextsFromMetaEvents(midiTracks)
        tracksAsText.forEach {
            console.log(it)
        }

        val measurePrefix = getMeasurePrefix(tracksAsText.first())
        val (tempos, timeSignatures, tickPrefix) = parseMasterTrack(
            midiTracks.first(),
            measurePrefix,
            warnings
        )

        return Project(
            format = Format.VSQ,
            inputFiles = listOf(file),
            name = file.nameWithoutExtension,
            tracks = listOf(),
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = measurePrefix,
            importWarnings = listOf()
        )
    }

    private fun extractTextsFromMetaEvents(midiTracks: Array<dynamic>): List<String> {
        return midiTracks.drop(1)
            .map { track ->
                (track.event as Array<dynamic>)
                    .fold("") { accumulator, element ->
                        val metaType = MidiUtil.MetaType.parse(element.metaType as? Int)
                        if (metaType != MidiUtil.MetaType.TEXT) accumulator
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

    private fun getMeasurePrefix(firstTrack: String): Int {
        val parameterName = "PreMeasure"
        firstTrack.linesNotBlank()
            .forEach { line ->
                if (line.contains(parameterName)) {
                    return line.replace("$parameterName=", "").toIntOrNull() ?: 0
                }
            }
        return 0
    }

    private fun getTickPrefix(timeSignatures: List<TimeSignature>, measurePrefix: Int): Long {
        val counter = TickCounter()
        timeSignatures
            .filter { it.measurePosition < measurePrefix }
            .forEach { counter.goToMeasure(it) }
        counter.goToMeasure(measurePrefix)
        return counter.tick
    }

    private fun parseMasterTrack(
        masterTrack: dynamic,
        measurePrefix: Int,
        warnings: MutableList<ImportWarning>
    ): Triple<List<Tempo>, List<TimeSignature>, Long> {
        val events = masterTrack.event as Array<dynamic>
        var tickPosition = 0
        val tickCounter = TickCounter()
        val rawTempos = mutableListOf<Tempo>()
        val rawTimeSignatures = mutableListOf<TimeSignature>()
        for (event in events) {
            tickPosition += event.deltaTime as Int
            when (MidiUtil.MetaType.parse(event.metaType as Int)) {
                MidiUtil.MetaType.TEMPO -> {
                    rawTempos.add(
                        Tempo(
                            tickPosition.toLong(),
                            MidiUtil.convertMidiTempoToBpm(event.data as Int)
                        )
                    )
                }
                MidiUtil.MetaType.TIME_SIGNATURE -> {
                    val (numerator, denominator) = MidiUtil.parseMidiTimeSignature(event.data)
                    tickCounter.goToTick(tickPosition.toLong(), numerator, denominator)
                    rawTimeSignatures.add(
                        TimeSignature(
                            tickCounter.measure,
                            numerator,
                            denominator
                        )
                    )
                }
                else -> {
                }
            }
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
            tickPrefix
        )
    }
}
