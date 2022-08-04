package process.pitch

import io.Ust
import model.Note
import model.Pitch
import model.Tempo
import process.interpolateCosineEaseIn
import process.interpolateCosineEaseInOut
import process.interpolateCosineEaseOut
import process.interpolateLinear
import process.simplifyShapeTo
import util.runIf

private const val SAMPLING_INTERVAL_TICK = 4L

data class UtauMode2TrackPitchData(
    val notes: List<UtauMode2NotePitchData?>
)

data class UtauMode2NotePitchData(
    val bpm: Double?,
    val start: Double?, // msec, null only if the note is not applied with pitch
    val startShift: Double?, // 10 cents
    val widths: List<Double>, // msec
    val shifts: List<Double>, // 10 cents
    val curveTypes: List<String>, // (blank)/s/r/j
    val vibratoParams: UtauNoteVibratoParams?
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
                ),
                (absolutePitch.firstOrNull { it.first >= note.tickOn }?.first ?: note.tickOn) - note.tickOn,
                tempos.bpmForNote(note)
            )
        }
    ).flatten()

    val dotPitDataSimplified = dotPitData.map {
        NotePitchData(simplifyShapeTo(it.pitch, Ust.MODE2_PITCH_MAX_POINT_COUNT), it.offset, it.bpm)
    }

    return UtauMode2TrackPitchData(
        dotPitDataSimplified.map { currNote ->
            if (currNote.pitch.isEmpty()) null else UtauMode2NotePitchData(
                currNote.bpm,
                milliSecFromTick(currNote.offset, currNote.bpm),
                currNote.pitch.first().second * 10, // *10 = semitone -> 10 cents
                currNote.pitch.zipWithNext().map { milliSecFromTick(it.second.first - it.first.first, currNote.bpm) },
                currNote.pitch.drop(1).unzip().second.map { it * 10 },
                List(currNote.pitch.size - 1) { "" },
                null
            )
        }
    )
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
        if (notePitch?.start != null) {
            var tickPos = note.tickOn + tickFromMilliSec(notePitch.start, bpm)
            val startShift =
                if (note.tickOn == lastNote?.tickOff) {
                    // always same value as the last note
                    (lastNote.key - note.key).toDouble()
                } else {
                    // actually in this case startShift is always 0.0
                    (notePitch.startShift ?: 0.0) / 10
                }
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
            .appendStartPoint(note)
            .appendEndPoint(note)
            .appendUtauNoteVibrato(notePitch?.vibratoParams, note, bpm, SAMPLING_INTERVAL_TICK)
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
            it.runIf(it.first < thisNote.tickOn) {
                it.first to (it.second + thisNote.key - lastNote.key)
            }
        }
        val lastPoint = fixed.lastOrNull()
        if (lastPoint != null && lastPoint.first < thisNote.tickOn) fixed + (thisNote.tickOn to 0.0)
        else fixed
    }

private fun List<Pair<Long, Double>>.appendStartPoint(thisNote: Note): List<Pair<Long, Double>> {
    val firstPoint = this.firstOrNull()
    return when {
        firstPoint == null -> listOf(thisNote.tickOn to 0.0)
        firstPoint.first > thisNote.tickOn -> listOf(thisNote.tickOn to firstPoint.second) + this
        else -> this
    }
}

private fun List<Pair<Long, Double>>.appendEndPoint(thisNote: Note): List<Pair<Long, Double>> {
    val lastPoint = this.lastOrNull()
    return when {
        lastPoint == null -> listOf(thisNote.tickOff to 0.0)
        lastPoint.first < thisNote.tickOff -> this + listOf(thisNote.tickOff to lastPoint.second)
        else -> this
    }
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
