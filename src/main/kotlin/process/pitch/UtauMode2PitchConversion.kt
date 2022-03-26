package process.pitch

import io.Ust
import kotlin.math.roundToLong
import model.Note
import model.Pitch
import model.TICKS_IN_BEAT
import model.Tempo
import process.interpolateCosineEaseIn
import process.interpolateCosineEaseInOut
import process.interpolateCosineEaseOut
import process.interpolateLinear
import process.simplifyShapeTo

private const val SAMPLING_INTERVAL_TICK = 4L

data class UtauMode2TrackPitchData(
    val notes: List<UtauMode2NotePitchData?>
)

data class UtauMode2NotePitchData(
    val bpm: Double?,
    val start: Double, // msec
    val startShift: Double, // 10 cents
    val widths: List<Double>, // msec
    val shifts: List<Double>, // 10 cents
    val curveTypes: List<String>, // (blank)/s/r/j
    val vibratoParams: List<Double>? // length(%), period(msec), depth(cent), easeIn(%), easeOut(%), phase(%), shift(%)
)

fun pitchToUtauMode2Track(pitch: Pitch?, notes: List<Note>, tempos: List<Tempo>): UtauMode2TrackPitchData? {
    pitch ?: return null
    val absolutePitch = pitch.getAbsoluteData(notes) ?: return null

    data class NotePitchData(
        val pitch: List<Pair<Long, Double>>,
        val offset: Long,
        val bpm: Double
    )

    val toRelative = { from: List<Pair<Long, Double?>>, key: Int ->
        from.map { Pair(it.first, (it.second ?: key.toDouble()) - key.toDouble()) }
    }

    val dotPitData = listOf(
        listOf(
            NotePitchData(
                toRelative(absolutePitch.filter { it.first < notes.first().tickOff }, notes.first().key),
                -(absolutePitch.filter { it.first < 0 }.unzip().first.minOrNull() ?: 0),
                tempos.bpmForNote(notes.first())
            )
        ), // first note
        notes.drop(1).map { note ->
            NotePitchData(
                toRelative(
                    absolutePitch.filter { it.first >= note.tickOn && it.first < note.tickOff },
                    note.key
                ), (absolutePitch.firstOrNull { it.first >= note.tickOn }?.first ?: note.tickOn) - note.tickOn,
                tempos.bpmForNote(note)
            )
        }).flatten()

    val dotPitDataSimplified = dotPitData.map {
        NotePitchData(simplifyShapeTo(it.pitch, Ust.MODE2_PITCH_MAX_POINT_COUNT), it.offset, it.bpm)
    }

    return UtauMode2TrackPitchData(dotPitDataSimplified.map { currNote ->
        if (currNote.pitch.isEmpty()) null else UtauMode2NotePitchData(
            currNote.bpm,
            milliSecFromTick(currNote.offset, currNote.bpm),
            currNote.pitch.first().second * 10, // *10 = semitone -> 10 cents
            currNote.pitch.zipWithNext().map { milliSecFromTick(it.second.first - it.first.first, currNote.bpm) },
            currNote.pitch.drop(1).unzip().second.map { it * 10 },
            List(currNote.pitch.size - 1) { "" },
            null
        )
    })
}

fun pitchFromUtauMode2Track(pitchData: UtauMode2TrackPitchData?, notes: List<Note>): Pitch? {
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
        pitchPoints.addAll(pendingPitchPoints.filter { it.first < (points.firstOrNull()?.first ?: Long.MAX_VALUE) })
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
        if (firstPoint != null && firstPoint.first > thisNote.tickOn) {
            listOf(thisNote.tickOn to firstPoint.second) + this
        } else this
    }

private fun List<Pair<Long, Double>>.appendVibrato(
    vibratoParams: List<Double>?,
    thisNote: Note,
    bpm: Double
): List<Pair<Long, Double>> {
    vibratoParams?.takeIf { it.isNotEmpty() } ?: return this

    // x-axis: tick, y-axis: 100cents
    val noteLength = thisNote.length
    val vibratoLength = noteLength * vibratoParams[0] / 100
    if (vibratoLength <= 0) return this
    val frequency = 1.0 / tickFromMilliSec(vibratoParams[1], bpm)
    if (frequency.isNaN()) return this
    val depth = (vibratoParams.getOrNull(2) ?: 0.0) / 100
    if (depth <= 0) return this
    val easeInLength = noteLength * (vibratoParams.getOrNull(3) ?: 0.0) / 100
    val easeOutLength = noteLength * (vibratoParams.getOrNull(4) ?: 0.0) / 100
    val phase = (vibratoParams.getOrNull(5) ?: 0.0) / 100
    val shift = depth * (vibratoParams.getOrNull(6) ?: 0.0) / 100

    val start = noteLength - vibratoLength
    val vibrato = { t: Double ->
        if (t < start) 0.0
        else {
            val easeInFactor = ((t - start) / easeInLength).coerceIn(0.0..1.0)
                .takeIf { !it.isNaN() } ?: 1.0
            val easeOutFactor = ((noteLength - t) / easeOutLength).coerceIn(0.0..1.0)
                .takeIf { !it.isNaN() } ?: 1.0
            val x = 2 * kotlin.math.PI * (frequency * (t - start) - phase)
            depth * easeInFactor * easeOutFactor * kotlin.math.sin(x) + shift
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

private fun List<Tempo>.bpmForNote(note: Note): Double {
    return this.last { it.tickPosition <= note.tickOn }.bpm
}

private fun tickFromMilliSec(msec: Double, bpm: Double): Long {
    return (msec * bpm * (TICKS_IN_BEAT) / 60000).roundToLong()
}

private fun milliSecFromTick(tick: Long, bpm: Double): Double {
    return tick * 60000 / (bpm * TICKS_IN_BEAT)
}
