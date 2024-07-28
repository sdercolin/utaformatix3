package core.process.pitch

import core.model.Note
import core.model.Pitch
import core.model.Tempo
import core.process.interpolateCosineEaseIn
import core.process.interpolateCosineEaseInOut
import core.process.interpolateCosineEaseOut
import core.process.interpolateLinear
import kotlin.math.roundToInt

private const val SAMPLING_INTERVAL_TICK = 5L

// Not smaller than the sampling interval of the pitch data used in pitch generation targeting any format.
// This is used for placing two key points far enough to ensure they are not merged by the resampling process.
private const val SAFE_SAMPLING_INTERVAL_TICK = 5L

data class OpenUtauNotePitchData(
    val points: List<Point>,
    val vibrato: UtauNoteVibratoParams,
) {

    data class Point(
        val x: Double, // milliSec
        val y: Double, // 10 cents
        val shape: Shape,
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
    val notes: List<OpenUtauNotePitchData>,
) {
    data class Point(
        val x: Long, // tick
        val y: Int, // cent
    )
}

fun pitchFromUstxPart(notes: List<Note>, pitchData: OpenUtauPartPitchData, tempos: List<Tempo>): Pitch? {
    // Extract pitch points from notes
    val notePointsList = mutableListOf<List<Pair<Long, Double>>>()
    val tickTimeTransformer = TickTimeTransformer(tempos)
    var lastKeyPos = -SAFE_SAMPLING_INTERVAL_TICK
    for ((note, notePitch) in notes.zip(pitchData.notes)) {
        val points = mutableListOf<Pair<Long, Double>>()
        var lastPointShape = OpenUtauNotePitchData.Shape.EaseInOut
        val noteStartInMillis = tickTimeTransformer.tickToMilliSec(note.tickOn)
        val keyPointPositions = mutableListOf<Long>()
        for (rawPoint in notePitch.points) {
            val x = tickTimeTransformer.milliSecToTick(noteStartInMillis + rawPoint.x)
                .coerceAtLeast(lastKeyPos + SAFE_SAMPLING_INTERVAL_TICK)
            lastKeyPos = x
            keyPointPositions.add(x)
            val y = rawPoint.y / 10
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
            .appendUtauNoteVibrato(notePitch.vibrato, note, tickTimeTransformer, SAMPLING_INTERVAL_TICK)
        val pointsWithVibrato = pointsBefore + pointsInNoteWithVibrato + pointsAfter
        val pointsResampled = pointsWithVibrato.resampled(SAMPLING_INTERVAL_TICK, keyPointPositions)
        notePointsList.add(pointsResampled)
    }

    // Divide note with points into sections,
    // where adjacent notes are put into the same section
    var currentSection = mutableListOf<Pair<Note, List<Pair<Long, Double>>>>()
    val notePitchSections = mutableListOf(currentSection)
    for (noteWithPoints in notes.zip(notePointsList)) {
        if (currentSection.isEmpty()) {
            currentSection.add(noteWithPoints)
            continue
        }
        val lastNote = currentSection.last().first
        if (lastNote.tickOff < noteWithPoints.first.tickOn) {
            // not adjacent
            currentSection = mutableListOf(noteWithPoints)
            notePitchSections.add(currentSection)
        } else {
            // adjacent
            currentSection.add(noteWithPoints)
        }
    }

    // Merge points in notes by section
    var sectionBorder = 0L
    val allPointsFromNote = mutableListOf<Pair<Long, Double>>()
    for (section in notePitchSections) {
        if (section.isEmpty()) continue

        var lastNote: Note? = null
        val pointsByNote = mutableListOf<List<Pair<Long, Double>>>()
        for (pair in section) {
            val note = pair.first
            var points = pair.second

            // Adjust y
            // If before this note's start point, use the previous note as base
            val prevNote = lastNote
            points = points.map { (x, y) ->
                val baseY = if (prevNote != null && x < note.tickOn) prevNote.key - note.key else 0
                x to (y - baseY)
            }

            pointsByNote.add(points)
            lastNote = note
        }
        val nextSectionBorder = section.last().first.tickOff

        // Merge points from all notes in the section
        val pointsInSection = pointsByNote
            .reduce { acc, list -> acc + list }
            .groupBy { it.first }
            .filter { it.key in sectionBorder..nextSectionBorder }
            .map { (tick, points) ->
                tick to points.sumOf { it.second }
            }

        allPointsFromNote.addAll(pointsInSection)
        sectionBorder = nextSectionBorder
    }

    // Extract curve points
    val curvePoints = pitchData.points
        .map { it.x to (it.y.toDouble() / 100) }
        .resampled(SAMPLING_INTERVAL_TICK)

    // Merge points from all notes and curve
    val pitchPoints = (allPointsFromNote + curvePoints)
        .groupBy { it.first }
        .map { (tick, points) ->
            tick to points.sumOf { it.second }
        }
        .sortedBy { it.first }
        .filter { it.first >= 0 }

    return if (pitchPoints.isEmpty()) null else Pitch(pitchPoints, isAbsolute = false)
}

fun mergePitchFromUstxParts(first: Pitch?, second: Pitch?): Pitch? {
    if (first == null) return second
    if (second == null) return first
    val data = (first.data + second.data)
        .mapNotNull { point -> point.second?.let { point.first to it } }
        .groupBy { it.first }
        .map { (tick, points) ->
            tick to points.sumOf { it.second }
        }
        .sortedBy { it.first }
    return first.copy(data = data)
}

fun Pitch?.reduceRepeatedPitchPointsFromUstxTrack(): Pitch? {
    this ?: return null
    return copy(data = data.map { it.first to it.second!! }.reduceRepeatedPitchPoints())
}

fun Pitch?.toOpenUtauPitchData(notes: List<Note>): List<Pair<Long, Double>> {
    val data = this?.getRelativeData(notes) ?: return listOf()
    return data.map { it.first to (it.second * 100).roundToInt().toDouble() }
        .appendPitchPointsForOpenUtauOutput()
        .reduceRepeatedPitchPoints()
}

private fun interpolate(
    lastPoint: Pair<Long, Double>,
    thisPoint: Pair<Long, Double>,
    shape: OpenUtauNotePitchData.Shape,
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
                add(start to 0.0)
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
            lastTick < end -> add(end to 0.0)
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

private fun List<Pair<Long, Double>>.resampled(
    interval: Long,
    keyPointPositions: List<Long> = emptyList(),
): List<Pair<Long, Double>> =
    groupBy { it.first / interval * interval }
        .map { (mergedTick, points) ->
            val keyPoint = points.find { it.first in keyPointPositions }
            if (keyPoint != null) {
                mergedTick to keyPoint.second
            } else {
                mergedTick to points.map { it.second }.average()
            }
        }
        .sortedBy { it.first }
        .fold(listOf()) { acc, point ->
            val lastPoint = acc.lastOrNull()
            if (lastPoint == null) {
                listOf(point)
            } else {
                val interpolated = listOf(lastPoint, point).interpolateLinear(interval).orEmpty()
                acc + interpolated.drop(1)
            }
        }

fun List<Pair<Long, Double>>.appendPitchPointsForOpenUtauOutput() =
    appendPitchPointsForInterpolation(this, SAMPLING_INTERVAL_TICK)
