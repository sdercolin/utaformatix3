package process.pitch

import model.Note
import model.Pitch
import process.interpolateCosineEaseIn
import process.interpolateCosineEaseInOut
import process.interpolateCosineEaseOut
import process.interpolateLinear

private const val SAMPLING_INTERVAL_TICK = 5L

data class OpenUtauNotePitchData(
    val points: List<Point>,
    val vibrato: UtauNoteVibratoParams
) {

    data class Point(
        val x: Double, // msec
        val y: Double, // 10 cents
        val shape: Shape
    )

    enum class Shape(val textValue: String) {
        EaseIn("i"),
        EaseOut("o"),
        EaseInOut("io"),
        Linear("l")
    }
}

data class OpenUtauPartPitchData(
    val points: List<Point>,
    val notes: List<OpenUtauNotePitchData>
) {
    data class Point(
        val x: Long, // tick
        val y: Int // cent
    )
}

fun pitchFromUstxPart(notes: List<Note>, pitchData: OpenUtauPartPitchData, bpm: Double): Pitch? {
    val notePointsList = mutableListOf<List<Pair<Long, Double>>>()
    var lastNote: Note? = null
    for ((note, notePitch) in notes.zip(pitchData.notes)) {
        val points = mutableListOf<Pair<Long, Double>>()
        var lastPointShape = OpenUtauNotePitchData.Shape.EaseInOut
        for (rawPoint in notePitch.points) {
            val x = note.tickOn + tickFromMilliSec(rawPoint.x, bpm)
            val baseKey = if (lastNote != null && x < lastNote.tickOff) lastNote.key else note.key
            val y = rawPoint.y / 10 - baseKey
            val thisPoint = x to y
            val lastPoint = points.lastOrNull()
            if (thisPoint.second != lastPoint?.second && lastPoint != null) {
                val interpolatedPointList = interpolate(lastPoint, thisPoint, lastPointShape)
                points.addAll(interpolatedPointList.drop(1))
            } else {
                points.add(thisPoint)
            }
            lastPointShape = rawPoint.shape
        }
        points.appendStartAndEndPoint(note)
        val (pointsBefore, pointsNotBefore) = points.partition { it.first < note.tickOn }
        val (pointsAfter, pointsIn) = pointsNotBefore.partition { it.first > note.tickOff }
        val pointsInNoteWithVibrato = pointsIn
            .appendUtauNoteVibrato(notePitch.vibrato, note, bpm, SAMPLING_INTERVAL_TICK)
        val pointsWithVibrato = pointsBefore + pointsInNoteWithVibrato + pointsAfter
        notePointsList.add(pointsWithVibrato.resampled(SAMPLING_INTERVAL_TICK))
        lastNote = note
    }

    // Merge points from all notes
    val pitchPoints = notePointsList.reduce { acc, list -> acc + list }
        .groupBy { it.first }
        .map { (tick, points) ->
            tick to points.sumByDouble { it.second }
        }
        .sortedBy { it.first }

    return Pitch(pitchPoints, isAbsolute = false)
}

private fun interpolate(
    lastPoint: Pair<Long, Double>,
    thisPoint: Pair<Long, Double>,
    shape: OpenUtauNotePitchData.Shape
): List<Pair<Long, Double>> {
    val input = listOf(lastPoint, thisPoint)
    val output = when (shape) {
        OpenUtauNotePitchData.Shape.EaseIn -> input.interpolateCosineEaseIn(SAMPLING_INTERVAL_TICK)
        OpenUtauNotePitchData.Shape.EaseOut -> input.interpolateCosineEaseOut(SAMPLING_INTERVAL_TICK)
        OpenUtauNotePitchData.Shape.EaseInOut -> input.interpolateCosineEaseInOut(SAMPLING_INTERVAL_TICK)
        OpenUtauNotePitchData.Shape.Linear -> input.interpolateLinear(SAMPLING_INTERVAL_TICK)
    }
    return output.orEmpty()
}

private fun MutableList<Pair<Long, Double>>.appendStartAndEndPoint(note: Note) {
    val start = note.tickOn
    val end = note.tickOff
    val hasStartPoint = any { it.first == start }
    val hasEndPoint = any { it.first == end }
    if (count() <= 1) {
        // Impossible? according to OpenUtau's restriction
        if (!hasStartPoint) add(0, start to (firstOrNull()?.second ?: 0.0))
        if (!hasEndPoint) add(end to (firstOrNull()?.second ?: 0.0))
        return
    }
    // Have at least two points
    val firstTick = first().first
    val lastTick = last().first

    // Ensure start point
    if (!hasStartPoint) {
        when {
            firstTick > start -> {
                // Same as first point
                add(0, start to first().second)
            }
            lastTick < start -> {
                // Same as note
                add(0, start to 0.0)
            }
            else -> {
                // In this case, firstTick < start, lastTick > end
                // So we have the following two points non-null
                val lastPointBefore = last { it.first < start }
                val firstPointAfter = first { it.first > start }

                // Linear interpolation
                val k = (firstPointAfter.second - lastPointBefore.second) /
                        (firstPointAfter.first - lastPointBefore.first)
                val y = lastPointBefore.second + (start - lastPointBefore.first) * k
                add(indexOf(firstPointAfter), start to y)
            }
        }
    }

    // Ensure end point
    if (!hasEndPoint) {
        when {
            firstTick > end -> add(0, end to first().second)
            lastTick < end -> add(0, end to 0.0)
            else -> {
                val lastPointBefore = last { it.first < end }
                val firstPointAfter = first { it.first > end }
                val k = (firstPointAfter.second - lastPointBefore.second) /
                        (firstPointAfter.first - lastPointBefore.first)
                val y = lastPointBefore.second + (end - lastPointBefore.first) * k
                add(indexOf(firstPointAfter), end to y)
            }
        }
    }
}

private fun List<Pair<Long, Double>>.resampled(interval: Long): List<Pair<Long, Double>> =
    groupBy { it.first / interval }
        .map { (mergedTick, points) ->
            mergedTick to points.map { it.second }.average()
        }
