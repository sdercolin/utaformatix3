package process.pitch

import model.Note
import model.Pitch
import model.TICKS_IN_FULL_NOTE
import process.interpolateCosineEaseIn
import process.interpolateCosineEaseInOut
import process.interpolateCosineEaseOut
import process.interpolateLinear
import kotlin.math.roundToLong

private const val SAMPLING_INTERVAL_TICK = 4L

data class UtauTrackPitchData(
    val notes: List<UtauNotePitchData?>
)

data class UtauNotePitchData(
    val bpm: Double?,
    val start: Double, // msec
    val startShift: Double, // 10 cents
    val widths: List<Double>, // msec
    val shifts: List<Double>, // 10 cents
    val curveTypes: List<String> // (blank)/s/r/j
)

fun pitchFromUtauTrack(pitchData: UtauTrackPitchData?, notes: List<Note>): Pitch? {
    pitchData ?: return null
    val notePitches = notes.zip(pitchData.notes)
    var bpm = notePitches.firstOrNull { it.second?.bpm != null }?.second?.bpm ?: return null
    val pitchPoints = mutableListOf<Pair<Long, Double>>()
    var lastNote: Note? = null
    var pendingPitchPoints = listOf<Pair<Long, Double>>()
    for ((note, notePitch) in notePitches) {
        val points = mutableListOf<Pair<Long, Double>>()
        if (notePitch?.bpm != null) bpm = notePitch.bpm
        if (notePitch != null) {
            var tickPos = note.tickOn + tickFromMilliSec(notePitch.start, bpm)
            points.add(tickPos to notePitch.startShift / 10)
            for (index in notePitch.widths.indices) {
                val width = notePitch.widths[index]
                val shift = notePitch.shifts.getOrNull(index) ?: 0.0
                val curveType = notePitch.curveTypes.getOrNull(index) ?: ""
                tickPos += tickFromMilliSec(width, bpm)
                val thisPoint = tickPos to (shift / 10)
                val lastPoint = points.last()
                val interpolatedPointList = interpolate(lastPoint, thisPoint, curveType)
                points.addAll(interpolatedPointList.drop(1))
            }
        }
        pitchPoints.addAll(pendingPitchPoints.filter { it.first < points.firstOrNull()?.first ?: Long.MAX_VALUE })
        pendingPitchPoints = points
            .fixPointsAtLastNote(note, lastNote)
            .addPointsContinuingLastNote(note, lastNote)
            .shape()
        lastNote = note
    }
    pitchPoints.addAll(pendingPitchPoints)
    return Pitch(pitchPoints, isAbsolute = false)
}

private fun List<Pair<Long, Double>>.fixPointsAtLastNote(thisNote: Note, lastNote: Note?) =
    if (lastNote == null) this
    else {
        val fixed = this.map {
            if (it.first < thisNote.tickOn) it.first to (it.second + thisNote.key - lastNote.key) else it
        }
        val lastPoint = fixed.lastOrNull()
        if (lastPoint != null && lastPoint.first < thisNote.tickOn) fixed + (thisNote.tickOn to 0.0)
        else fixed
    }

private fun List<Pair<Long, Double>>.addPointsContinuingLastNote(thisNote: Note, lastNote: Note?) =
    if (lastNote == null) this
    else {
        val firstPoint = this.firstOrNull()
        if (firstPoint != null && firstPoint.first > thisNote.tickOn) this + (thisNote.tickOn to firstPoint.second)
        else this
    }

private fun List<Pair<Long, Double>>.shape() =
    this.sortedBy { it.first }
        .fold(listOf<Pair<Long, Double>>()) { acc, point ->
            val lastPoint = acc.lastOrNull()
            if (lastPoint != null && lastPoint.first == point.first) {
                acc.dropLast(1) + (lastPoint.first to ((lastPoint.second + point.second) / 2))
            } else {
                acc + point
            }
        }

private fun interpolate(
    lastPoint: Pair<Long, Double>,
    thisPoint: Pair<Long, Double>,
    curveType: String
): List<Pair<Long, Double>> {
    val input = listOf(lastPoint, thisPoint)
    val output = when (curveType) {
        "s" -> input.interpolateLinear(SAMPLING_INTERVAL_TICK)
        "j" -> input.interpolateCosineEaseIn(SAMPLING_INTERVAL_TICK)
        "r" -> input.interpolateCosineEaseOut(SAMPLING_INTERVAL_TICK)
        else -> input.interpolateCosineEaseInOut(SAMPLING_INTERVAL_TICK)
    }
    return output.orEmpty()
}

private fun tickFromMilliSec(msec: Double, bpm: Double): Long {
    return (msec * bpm * (TICKS_IN_FULL_NOTE / 4) / 60000).roundToLong()
}
