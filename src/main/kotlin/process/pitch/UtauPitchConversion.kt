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
    val curveTypes: List<String>, // (blank)/s/r/j
    val vibratoParams: List<Double>? // length(%), period(msec), depth(cent), easeIn(%), easeOut(%), phase(%), shift(%)
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
            val startShift =
                if (note.tickOn == lastNote?.tickOff) (lastNote.key - note.key).toDouble()
                else notePitch.startShift / 10
            points.add(tickPos to startShift)
            for (index in notePitch.widths.indices) {
                val width = notePitch.widths[index]
                val shift = notePitch.shifts.getOrNull(index) ?: 0.0
                val curveType = notePitch.curveTypes.getOrNull(index) ?: ""
                tickPos += tickFromMilliSec(width, bpm)
                val thisPoint = tickPos to (shift / 10)
                val lastPoint = points.last()
                if (thisPoint.second != lastPoint.second) {
                    val interpolatedPointList = interpolate(lastPoint, thisPoint, curveType)
                    points.addAll(interpolatedPointList.drop(1))
                } else {
                    points.add(thisPoint)
                }
            }
        }
        pitchPoints.addAll(pendingPitchPoints.filter { it.first < points.firstOrNull()?.first ?: Long.MAX_VALUE })
        pendingPitchPoints = points
            .fixPointsAtLastNote(note, lastNote)
            .addPointsContinuingLastNote(note, lastNote)
            .appendVibrato(notePitch?.vibratoParams, note, bpm)
            .shape()
        lastNote = note
    }
    pitchPoints.addAll(pendingPitchPoints)
    return Pitch(pitchPoints, isAbsolute = false)
}

private fun List<Pair<Long, Double>>.fixPointsAtLastNote(thisNote: Note, lastNote: Note?) =
    if (lastNote == null || lastNote.tickOff != thisNote.tickOn) this
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
        if (firstPoint != null && firstPoint.first > thisNote.tickOn)
            listOf(thisNote.tickOn to firstPoint.second) + this
        else this
    }

private fun List<Pair<Long, Double>>.appendVibrato(
    vibratoParams: List<Double>?,
    thisNote: Note,
    bpm: Double
): List<Pair<Long, Double>> {
    vibratoParams ?: return this

    // x-axis: tick, y-axis: 100cents
    val noteLength = thisNote.length
    val vibratoLength = noteLength * vibratoParams[0] / 100
    val frequency = 1.0 / tickFromMilliSec(vibratoParams[1], bpm)
    val depth = vibratoParams[2] / 100
    val easeInLength = noteLength * vibratoParams[3] / 100
    val easeOutLength = noteLength * vibratoParams[4] / 100
    val phase = vibratoParams[5] / 100
    val shift = depth * vibratoParams[6] / 100

    val start = noteLength - vibratoLength
    val vibrato = { t: Double ->
        if (t < start) 0.0
        else {
            val easeInFactor = ((t - start) / easeInLength).coerceIn(0.0..1.0)
            val easeOutFactor = ((noteLength - t) / easeOutLength).coerceIn(0.0..1.0)
            val x = 2 * kotlin.math.PI * (frequency * (t - start) - phase)
            val output = depth * easeInFactor * easeOutFactor * kotlin.math.sin(x) + shift
            output
        }
    }

    val needAppendEndingPoint = this.lastOrNull()?.first != thisNote.tickOff

    return this
        .asSequence()
        .plus(if (needAppendEndingPoint) (thisNote.tickOff to (this.lastOrNull()?.second ?: 0.0)) else null)
        .filterNotNull()
        .map { (it.first - thisNote.tickOn) to it.second }
        .fold(listOf<Pair<Long, Double>>()) { acc, inputPoint ->
            val lastPoint = acc.lastOrNull()
            val newPoint = inputPoint.let { it.first to (it.second + vibrato(it.first.toDouble())) }
            if (lastPoint == null) {
                acc + newPoint
            } else {
                val interpolatedIndexes = ((lastPoint.first + 1) until inputPoint.first)
                    .filter { (it - lastPoint.first) % SAMPLING_INTERVAL_TICK == 0L }
                val interpolatedPoints = interpolatedIndexes.map { it to (lastPoint.second + vibrato(it.toDouble())) }
                acc + interpolatedPoints + newPoint
            }
        }
        .map { (it.first + thisNote.tickOn) to it.second }
        .toList()

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
