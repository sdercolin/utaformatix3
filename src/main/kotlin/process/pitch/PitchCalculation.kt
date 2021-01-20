package process.pitch

import exception.NotesOverlappingException
import model.KEY_CENTER_C
import model.LOG_FRQ_CENTER_C
import model.LOG_FRQ_DIFF_ONE_KEY
import model.Note
import model.Pitch

fun Double.loggedFrequencyToKey() = KEY_CENTER_C + (this - LOG_FRQ_CENTER_C) / LOG_FRQ_DIFF_ONE_KEY
fun Double.keyToLoggedFrequency() = (this - KEY_CENTER_C) * LOG_FRQ_DIFF_ONE_KEY + LOG_FRQ_CENTER_C

fun Pitch.getAbsoluteData(notes: List<Note>): List<Pair<Long, Double?>>? = convertRelativity(notes, toAbsolute = true)
fun Pitch.getRelativeData(notes: List<Note>, borderAppendRadius: Long = 0L): List<Pair<Long, Double>>? =
    convertRelativity(notes, toAbsolute = false, borderAppendRadius = borderAppendRadius)
        ?.mapNotNull { pair -> pair.second?.let { pair.first to it } }

private fun Pitch.convertRelativity(
    notes: List<Note>,
    toAbsolute: Boolean,
    borderAppendRadius: Long = 0L
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
                        else value.takeUnless { it == 0.0 }?.let { it + currentNoteKey } ?: 0.0
                    } else 0.0
                pos to convertedValue
            }.let {
                if (!toAbsolute) it.appendPointsAtBorders(notes, radius = borderAppendRadius)
                else it
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
    radius: Long
): List<Pair<Long, Double?>> {
    if (radius <= 0) return this
    val result = this.toMutableList()
    notes.zipWithNext()
        .forEach { (lastNote, thisNote) ->
            if (thisNote.tickOn - lastNote.tickOff > radius) return@forEach
            val firstPointAtThisNoteIndex =
                result.indexOfFirst { it.first >= thisNote.tickOn }.takeIf { it >= 0 } ?: return@forEach
            val firstPointAtThisNote = result[firstPointAtThisNoteIndex]
            console.log("firstPointAtThisNote = $firstPointAtThisNote")
            if (firstPointAtThisNote.first == thisNote.tickOn ||
                firstPointAtThisNote.first - thisNote.tickOn > radius
            ) return@forEach
            val postValue = firstPointAtThisNote.second ?: return@forEach
            val newPointTick = thisNote.tickOn - radius
            val newPoint = newPointTick to postValue
            console.log("newPoint = $newPoint")
            result.add(firstPointAtThisNoteIndex, newPoint)
            result.removeAll { it.first in newPointTick until thisNote.tickOn && it != newPoint }
        }
    return result
}
