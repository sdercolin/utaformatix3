package process.pitch

import model.Note
import kotlin.math.PI
import kotlin.math.sin

/**
 * Shared by processes for OpenUtau and Utau mode2
 */

data class UtauNoteVibratoParams(
    val length: Double, // percentage of the note's length
    val period: Double, // milliSec
    val depth: Double, // cent
    val fadeIn: Double, // percentage of the vibrato's length
    val fadeOut: Double, // percentage of the vibrato's length
    val phaseShift: Double, // percentage of period
    val shift: Double, // percentage of depth
)

fun List<Pair<Long, Double>>.appendUtauNoteVibrato(
    vibratoParams: UtauNoteVibratoParams?,
    thisNote: Note,
    tickTimeTransformer: TickTimeTransformer,
    sampleIntervalTick: Long,
): List<Pair<Long, Double>> {
    vibratoParams ?: return this

    // x-axis: milliSec, y-axis: 100cents
    val noteLength = tickTimeTransformer.tickDistanceToMilliSec(tickStart = thisNote.tickOn, tickEnd = thisNote.tickOff)
    val vibratoLength = noteLength * vibratoParams.length / 100
    if (vibratoLength <= 0) return this
    val frequency = 1.0 / vibratoParams.period
    if (frequency.isFinite().not()) return this
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
                .takeIf { it.isFinite() } ?: 1.0
            val easeOutFactor = ((noteLength - t) / easeOutLength).coerceIn(0.0..1.0)
                .takeIf { it.isFinite() } ?: 1.0
            val x = 2 * PI * (frequency * (t - start) - phase)
            depth * easeInFactor * easeOutFactor * (sin(x) + shift)
        }
    }

    val noteStartInMillis = tickTimeTransformer.tickToMilliSec(thisNote.tickOn)

    // get approximate interval for interpolation
    val sampleIntervalInMillis = tickTimeTransformer.tickDistanceToMilliSec(
        tickStart = thisNote.tickOn,
        tickEnd = thisNote.tickOn + sampleIntervalTick,
    )

    return map { (tickTimeTransformer.tickToMilliSec(it.first) - noteStartInMillis) to it.second }
        .fold(listOf<Pair<Double, Double>>()) { acc, inputPoint ->
            val lastPoint = acc.lastOrNull()
            val newPoint = inputPoint.let { it.first to (it.second + vibrato(it.first)) }
            if (lastPoint == null) {
                acc + newPoint
            } else {
                val interpolatedXs = mutableListOf<Double>()
                var pos = lastPoint.first + sampleIntervalInMillis
                while (pos < newPoint.first) {
                    interpolatedXs.add(pos)
                    pos += sampleIntervalInMillis
                }
                val interpolatedPoints = interpolatedXs.map { x -> x to (lastPoint.second + vibrato(x)) }
                acc + interpolatedPoints + newPoint
            }
        }
        .map { (milliSecFromNoteStart, value) ->
            val tick = tickTimeTransformer.milliSecToTick(milliSecFromNoteStart + noteStartInMillis)
            tick to value
        }
}
