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
    private val segments =
        buildList<Segment> {
            (tempos.zipWithNext() + (tempos.last() to null)).forEach { (thisTempo, nextTempo) ->
                val range = thisTempo.tickPosition until (nextTempo?.tickPosition ?: Long.MAX_VALUE)
                val rate = thisTempo.bpm.bpmToSecPerTick()
                val segment =
                    if (isEmpty()) {
                        Segment(range, 0.0, rate)
                    } else {
                        val lastParams = last()
                        val offset =
                            lastParams.offset +
                                (lastParams.range.last + 1 - lastParams.range.first) * lastParams.secPerTick
                        Segment(range, offset, rate)
                    }
                add(segment)
            }
        }

    // segments are contiguous and sorted; binary search for the last one starting at or before the key
    private fun segmentAtTick(tick: Long): Segment {
        var low = 0
        var high = segments.lastIndex
        var index = 0
        while (low <= high) {
            val mid = (low + high) / 2
            if (segments[mid].range.first <= tick) {
                index = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return segments[index]
    }

    private fun segmentAtSec(sec: Double): Segment {
        var low = 0
        var high = segments.lastIndex
        var index = 0
        while (low <= high) {
            val mid = (low + high) / 2
            if (segments[mid].offset <= sec) {
                index = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return segments[index]
    }

    fun tickToSec(tick: Long) = segmentAtTick(tick).let { it.offset + (tick - it.range.first) * it.secPerTick }

    fun tickToMilliSec(tick: Long) = tickToSec(tick) * 1000

    fun tickDistanceToSec(
        tickStart: Long,
        tickEnd: Long,
    ) = (tickToSec(tickEnd) - tickToSec(tickStart))

    fun tickDistanceToMilliSec(
        tickStart: Long,
        tickEnd: Long,
    ) = tickDistanceToSec(tickStart, tickEnd) * 1000

    fun secToTick(sec: Double) = segmentAtSec(sec).let { ((sec - it.offset) / it.secPerTick).toLong() + it.range.first }

    fun milliSecToTick(milliSec: Double) = secToTick(milliSec / 1000.0)
}
