package core.process

import core.model.Project
import core.model.TimeSignature
import core.model.Track
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

fun Project.needWarningZoom(factor: Double): Boolean {
    return timeSignatures.any {
        val newPosition = it.measurePosition * factor
        ceil(newPosition) != newPosition
    }
}

fun Project.zoom(factor: Double): Project {
    val tracks = tracks.map { it.zoom(factor) }
    val tempos = tempos.map {
        it.copy(tickPosition = (it.tickPosition * factor).roundToLong(), bpm = it.bpm * factor)
    }
    val timeSignatures = timeSignatures
        .map { it.copy(measurePosition = (it.measurePosition * factor).roundToInt()) }
        .fold(listOf<TimeSignature>()) { acc, timeSignature ->
            if (acc.isEmpty()) {
                listOf(timeSignature)
            } else {
                val prev = acc.last()
                if (prev.measurePosition == timeSignature.measurePosition) {
                    acc
                } else {
                    acc + timeSignature
                }
            }
        }

    return copy(tracks = tracks, tempos = tempos, timeSignatures = timeSignatures)
}

private fun Track.zoom(factor: Double): Track {
    val notes = notes.map {
        it.copy(
            tickOn = (it.tickOn * factor).roundToLong(),
            tickOff = (it.tickOff * factor).roundToLong(),
        )
    }
    val pitch = pitch?.copy(
        data = pitch.data.map { (tick, value) ->
            (tick * factor).roundToLong() to value
        },
    )
    return copy(notes = notes, pitch = pitch)
}

val projectZoomFactorOptions = listOf("2", "5/3", "3/2", "4/3", "6/5", "4/5", "3/4", "3/5", "1/2")
