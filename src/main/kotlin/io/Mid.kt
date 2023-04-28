package io

import model.ImportWarning
import model.Project
import model.Tempo
import model.TickCounter
import model.TimeSignature
import model.Track
import org.khronos.webgl.Uint8Array
import org.w3c.files.File
import util.MidiUtil
import util.addBlock
import util.addInt
import util.addIntVariableLengthBigEndian
import util.addShort
import util.addString
import util.asByteTypedArray
import util.decode
import util.readAsArrayBuffer

object Mid {

    private fun customInterpreter(
        metaType: Byte,
        arrayBuffer: dynamic,
    ): dynamic {
        // we need to handle 0x20 `Channel Prefix` meta event,
        // otherwise the parser will break and all the following data get shifted
        if (metaType != 0x20.toByte()) return false
        return arrayOf(arrayBuffer.readInt(1))
    }

    suspend fun parseMidi(file: File): dynamic {
        val bytes = file.readAsArrayBuffer()
        val midiParser = external.require("midi-parser-js")
        midiParser.customInterpreter = ::customInterpreter
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

    fun generateContent(
        project: Project,
        generateTrackBytes: (track: Track, tickPrefix: Int, measurePrefix: Int) -> List<Byte>,
    ):
        Uint8Array {
        val bytes = mutableListOf<Byte>()
        bytes.addAll(headerLabel)
        bytes.addInt(6, IS_LITTLE_ENDIAN)
        bytes.addShort(1, IS_LITTLE_ENDIAN)
        bytes.addShort((project.tracks.count() + 1).toShort(), IS_LITTLE_ENDIAN)
        bytes.addAll(timeDivisions)

        val tickPrefix = project.timeSignatures.first().ticksInMeasure * project.measurePrefix

        // master track
        bytes.addAll(trackLabel)
        bytes.addBlock(
            generateMasterTrack(project, tickPrefix),
            IS_LITTLE_ENDIAN,
            lengthInVariableLength = false,
        )

        // normal tracks
        project.tracks.forEach {
            bytes.addAll(trackLabel)
            bytes.addBlock(
                generateTrackBytes(it, tickPrefix, project.measurePrefix),
                IS_LITTLE_ENDIAN,
                lengthInVariableLength = false,
            )
        }
        return Uint8Array(bytes.toTypedArray())
    }

    private fun generateMasterTrack(project: Project, tickPrefix: Int): List<Byte> {
        val bytes = mutableListOf<Byte>()
        bytes.add(0x00)
        bytes.addAll(MidiUtil.MetaType.TrackName.eventHeaderBytes)
        bytes.addString("Master Track", IS_LITTLE_ENDIAN, lengthInVariableLength = true)

        val tickEventPairs = mutableListOf<Pair<Long, Any>>()
        project.tempos.forEach {
            val tick = if (it.tickPosition == 0L) 0L else it.tickPosition + tickPrefix
            tickEventPairs.add(tick to it)
        }
        val counter = TickCounter()
        counter.goToMeasure(project.timeSignatures.first())
        tickEventPairs.add(0L to project.timeSignatures.first())
        project.timeSignatures.drop(1).forEach {
            counter.goToMeasure(it)
            tickEventPairs.add(counter.outputTick + tickPrefix to it)
        }
        tickEventPairs.sortBy { it.first }
        val deltaEventPairs = listOf(0L to tickEventPairs.first().second) +
            tickEventPairs
                .zipWithNext()
                .map { (previous, current) ->
                    (current.first - previous.first) to current.second
                }
        for ((delta, event) in deltaEventPairs) {
            bytes.addIntVariableLengthBigEndian(delta.toInt())
            when (event) {
                is TimeSignature -> {
                    bytes.addAll(MidiUtil.MetaType.TimeSignature.eventHeaderBytes)
                    bytes.addBlock(
                        MidiUtil.generateMidiTimeSignatureBytes(event.numerator, event.denominator),
                        IS_LITTLE_ENDIAN,
                        lengthInVariableLength = true,
                    )
                }
                is Tempo -> {
                    bytes.addAll(MidiUtil.MetaType.Tempo.eventHeaderBytes)
                    val tempoBytes = mutableListOf<Byte>().let {
                        it.addInt(MidiUtil.convertBpmToMidiTempo(event.bpm), IS_LITTLE_ENDIAN)
                        it.takeLast(3)
                    }
                    bytes.addBlock(tempoBytes.takeLast(3), IS_LITTLE_ENDIAN, lengthInVariableLength = true)
                }
                else -> throw IllegalStateException()
            }
        }
        bytes.add(0x00)
        bytes.addAll(MidiUtil.MetaType.EndOfTrack.eventHeaderBytes)
        bytes.add(0x00)
        return bytes
    }

    private val headerLabel = listOf(0x4d, 0x54, 0x68, 0x64).map { it.toByte() }
    private val timeDivisions = listOf(0x01, 0xe0).map { it.toByte() }
    private val trackLabel = listOf(0x4d, 0x54, 0x72, 0x6b).map { it.toByte() }
    const val IS_LITTLE_ENDIAN = false
}
