package process.pitch

import kotlin.math.roundToLong
import model.TICKS_IN_BEAT

fun tickFromMilliSec(msec: Double, bpm: Double): Long {
    return (msec * bpm * (TICKS_IN_BEAT) / 60000).roundToLong()
}

fun milliSecFromTick(tick: Long, bpm: Double): Double {
    return tick * 60000 / (bpm * TICKS_IN_BEAT)
}
