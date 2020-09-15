package util

import kotlin.math.pow

object MidiUtil {
    enum class MetaType(val value: Int) {
        TEXT(1),
        TRACK_NAME(3),
        TEMPO(81),
        TIME_SIGNATURE(88),
        END_OF_TRACK(47);

        companion object {
            fun parse(value: Int?): MetaType? = values().find { it.value == value }
        }
    }

    fun convertMidiTempoToBpm(midiTempo: Int) =
        1000 * 1000 * 60 / midiTempo.toDouble()

    fun convertBpmToMidiTempo(bpm: Double) =
        (1000 * 1000 * 60 / bpm).toInt()

    fun parseMidiTimeSignature(data: dynamic): Pair<Int, Int> {
        data as Array<Int>
        val numerator = data[0]
        val denominator = (2f.pow(data[1])).toInt()
        return numerator to denominator
    }

    fun generateMidiTimeSignature(numerator: Int, denominator: Int): Array<Int> {
        return arrayOf(
            numerator,
            kotlin.math.log2(denominator.toDouble()).toInt(),
            24,
            8
        )
    }
}
