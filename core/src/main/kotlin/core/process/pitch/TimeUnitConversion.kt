package core.process.pitch

import core.model.TICKS_IN_BEAT

fun milliSecFromTick(tick: Long, bpm: Double): Double {
    return tick * 60000 / (bpm * TICKS_IN_BEAT)
}
