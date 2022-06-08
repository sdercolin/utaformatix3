package process.pitch

import kotlin.math.PI
import kotlin.math.sin
import model.Note

/**
 * Shared by processes for OpenUtau and Utau mode2
 */

data class UtauNoteVibratoParams(
    val length: Double, // percentage of the note's length
    val period: Double, // msec
    val depth: Double, // cent
    val fadeIn: Double, // percentage of the vibrato's length
    val fadeOut: Double, // percentage of the vibrato's length
    val phaseShift: Double, // percentage of period
    val shift: Double // percentage of depth
)

fun List<Pair<Long, Double>>.appendUtauNoteVibrato(
    vibratoParams: UtauNoteVibratoParams?,
    thisNote: Note,
    bpm: Double,
    sampleIntervalTick: Long
): List<Pair<Long, Double>> {
    vibratoParams ?: return this

    // x-axis: tick, y-axis: 100cents
    val noteLength = thisNote.length
    val vibratoLength = noteLength * vibratoParams.length / 100
    if (vibratoLength <= 0) return this
    val frequency = 1.0 / tickFromMilliSec(vibratoParams.period, bpm)
    if (frequency.isNaN()) return this
    val depth = vibratoParams.depth / 100
    if (depth <= 0) return this
    val easeInLength = noteLength * vibratoParams.fadeIn / 100
    val easeOutLength = noteLength * vibratoParams.fadeOut / 100
    val phase = vibratoParams.phaseShift / 100
    val shift = vibratoParams.shift / 100

    val start = noteLength - vibratoLength
    val vibrato = { t: Double ->
        if (t < start) 0.0
        else {
            val easeInFactor = ((t - start) / easeInLength).coerceIn(0.0..1.0)
                .takeIf { !it.isNaN() } ?: 1.0
            val easeOutFactor = ((noteLength - t) / easeOutLength).coerceIn(0.0..1.0)
                .takeIf { !it.isNaN() } ?: 1.0
            val x = 2 * PI * (frequency * (t - start) - phase)
            depth * easeInFactor * easeOutFactor * (kotlin.math.sin(x) + shift)
        }
    }

    return map { (it.first - thisNote.tickOn) to it.second }
        .fold(listOf<Pair<Long, Double>>()) { acc, inputPoint ->
            val lastPoint = acc.lastOrNull()
            val newPoint = inputPoint.let { it.first to (it.second + vibrato(it.first.toDouble())) }
            if (lastPoint == null) {
                acc + newPoint
            } else {
                val interpolatedIndexes = ((lastPoint.first + 1) until inputPoint.first)
                    .filter { (it - lastPoint.first) % sampleIntervalTick == 0L }
                val interpolatedPoints =
                    interpolatedIndexes.map { it to (inputPoint.second + vibrato(it.toDouble())) }
                acc + interpolatedPoints + newPoint
            }
        }
        .map { (it.first + thisNote.tickOn) to it.second }
}
