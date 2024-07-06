package core.process.pitch

import core.exception.NotesOverlappingException
import core.model.KEY_CENTER_C
import core.model.LOG_FRQ_CENTER_C
import core.model.LOG_FRQ_DIFF_ONE_KEY
import core.model.Note
import core.model.Pitch
import core.util.runIf

fun Double.loggedFrequencyToKey() = KEY_CENTER_C + (this - LOG_FRQ_CENTER_C) / LOG_FRQ_DIFF_ONE_KEY
fun Double.keyToLoggedFrequency() = (this - KEY_CENTER_C) * LOG_FRQ_DIFF_ONE_KEY + LOG_FRQ_CENTER_C

fun Pitch.getAbsoluteData(notes: List<Note>): List<Pair<Long, Double?>>? = convertRelativity(notes, toAbsolute = true)
fun Pitch.getRelativeData(notes: List<Note>, borderAppendRadius: Long = 0L): List<Pair<Long, Double>>? =
    convertRelativity(notes, toAbsolute = false, borderAppendRadius = borderAppendRadius)
        ?.mapNotNull { pair -> pair.second?.let { pair.first to it } }

private fun Pitch.convertRelativity(
    notes: List<Note>,
    toAbsolute: Boolean,
    borderAppendRadius: Long = 0L,
): List<Pair<Long, Double?>>? =
    when {
        isAbsolute && toAbsolute -> this.data
        !isAbsolute && !toAbsolute -> this.data
        notes.isEmpty() -> null
        else -> {
            val borders = notes.getBorders()
            var index = 0
            var currentNoteKey = notes.first().key
            var nextBorder = borders.firstOrNull() ?: Long.MAX_VALUE
            data.map { (pos, value) ->
                while (pos >= nextBorder) {
                    index++
                    nextBorder = borders.getOrNull(index) ?: Long.MAX_VALUE
                    currentNoteKey = notes[index].key
                }
                val convertedValue =
                    if (value != null) {
                        if (isAbsolute) value - currentNoteKey
                        else value.takeUnless { it == 0.0 }?.let { it + currentNoteKey }
                    } else 0.0
                pos to convertedValue
            }.runIf(!toAbsolute) {
                appendPointsAtBorders(notes, radius = borderAppendRadius)
            }
        }
    }

private fun List<Note>.getBorders(): List<Long> {
    val borders = mutableListOf<Long>()
    var pos = -1L
    for (note in this) {
        if (pos < 0) {
            pos = note.tickOff
            continue
        }
        when {
            pos == note.tickOn -> borders.add(pos)
            pos < note.tickOn -> borders.add((note.tickOn + pos) / 2)
            else -> throw NotesOverlappingException()
        }
        pos = note.tickOff
    }
    return borders.toList()
}

private fun List<Pair<Long, Double?>>.appendPointsAtBorders(
    notes: List<Note>,
    radius: Long,
): List<Pair<Long, Double?>> {
    if (radius <= 0) return this
    val result = this.toMutableList()
    notes.zipWithNext()
        .forEach { (lastNote, thisNote) ->
            if (thisNote.tickOn - lastNote.tickOff > radius) return@forEach
            val firstPointAtThisNoteIndex =
                result.indexOfFirst { it.first >= thisNote.tickOn }.takeIf { it >= 0 } ?: return@forEach
            val firstPointAtThisNote = result[firstPointAtThisNoteIndex]
            if (firstPointAtThisNote.first == thisNote.tickOn ||
                firstPointAtThisNote.first - thisNote.tickOn > radius
            ) return@forEach
            val postValue = firstPointAtThisNote.second ?: return@forEach
            val newPointTick = thisNote.tickOn - radius
            val newPoint = newPointTick to postValue
            result.add(firstPointAtThisNoteIndex, newPoint)
            result.removeAll { it.first in newPointTick until thisNote.tickOn && it != newPoint }
        }
    return result
}

/**
 * Append points in our pitch data to be used in some formats e.g. svp, ustx
 * where points are interpolated
 * In our data format, [(0,0), (3,6)] means [(0,0), (1,0), (2,0), (3,6)]
 * But it will be processed to [(0,0), (1,2), (2,4), (3,6)] in those cases
 * Therefore we have to append points to make it like [(0,0), (2,0), (3,6)]
 */
fun appendPitchPointsForInterpolation(points: List<Pair<Long, Double>>, intervalTick: Long) =
    listOfNotNull(points.firstOrNull()) +
        points.zipWithNext()
            .flatMap { (lastPoint, thisPoint) ->
                val tickDiff = thisPoint.first - lastPoint.first
                val newPoint = when {
                    tickDiff < intervalTick -> null
                    tickDiff < 2 * intervalTick ->
                        ((thisPoint.first + lastPoint.first) / 2) to lastPoint.second
                    else ->
                        thisPoint.first - intervalTick to lastPoint.second
                }
                listOfNotNull(newPoint, thisPoint)
            }

/**
 * Reduce a series of adjacent points with same value to two points
 */
fun List<Pair<Long, Double>>.reduceRepeatedPitchPoints(): List<Pair<Long, Double>> {
    val toBeRemoved = mutableSetOf<Pair<Long, Double>>()
    var currentRepeatedValue: Double? = null
    var prevPoint: Pair<Long, Double>? = null
    for (point in this) {
        if (prevPoint == null) {
            prevPoint = point
            continue
        }
        if (currentRepeatedValue == null) {
            if (prevPoint.second == point.second) {
                currentRepeatedValue = point.second
            }
            prevPoint = point
            continue
        }
        if (currentRepeatedValue == point.second) {
            toBeRemoved.add(prevPoint)
        } else {
            currentRepeatedValue = null
        }

        prevPoint = point
    }
    return minus(toBeRemoved)
}
