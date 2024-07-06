package core.process.pitch

import core.model.TICKS_IN_BEAT
import core.model.Tempo

fun Double.bpmToSecPerTick() = 60.0 / TICKS_IN_BEAT / this

class TickTimeTransformer(
    tempos: List<Tempo>,
) {
    private class Segment(
        val range: LongRange,
        val offset: Double,
        val secPerTick: Double,
    )

    // a piecewise linear transformation from tick to sec
    // simplify the calculation here for every usage to reduce cost
    private val segments = (tempos.zipWithNext() + (tempos.last() to null))
        .fold(listOf<Segment>()) { acc, (thisTempo, nextTempo) ->
            val range = thisTempo.tickPosition until (nextTempo?.tickPosition ?: Long.MAX_VALUE)
            val rate = thisTempo.bpm.bpmToSecPerTick()
            val thisResult = if (acc.isEmpty()) {
                Segment(range, 0.0, rate)
            } else {
                val lastParams = acc.last()
                val offset = lastParams.offset +
                    (lastParams.range.last + 1 - lastParams.range.first) * lastParams.secPerTick
                Segment(range, offset, rate)
            }
            acc + thisResult
        }

    fun tickToSec(tick: Long) = segments
        .firstOrNull { tick in it.range }
        .let { it ?: segments.first() }
        .let { it.offset + (tick - it.range.first) * it.secPerTick }

    fun tickToMilliSec(tick: Long) = tickToSec(tick) * 1000

    fun tickDistanceToSec(tickStart: Long, tickEnd: Long) = (tickToSec(tickEnd) - tickToSec(tickStart))

    fun tickDistanceToMilliSec(tickStart: Long, tickEnd: Long) = tickDistanceToSec(tickStart, tickEnd) * 1000

    fun secToTick(sec: Double) = segments
        .lastOrNull { it.offset <= sec }
        .let { it ?: segments.first() }
        .let { ((sec - it.offset) / it.secPerTick).toLong() + it.range.first }

    fun milliSecToTick(milliSec: Double) = secToTick(milliSec / 1000.0)
}
