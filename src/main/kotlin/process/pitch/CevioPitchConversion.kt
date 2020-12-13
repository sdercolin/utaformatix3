package process.pitch

import model.Note
import model.Pitch
import process.pitch.CevioTrackPitchData.Event
import kotlin.math.roundToLong

data class CevioTrackPitchData(
    val events: List<Event>
) {

    data class Event(
        val index: Long?, // raw value
        val repeat: Long?, // raw value
        val value: Double
    )
}

private const val TIME_UNIT_AS_TICKS = 4.8

fun pitchFromCevioTrack(data: CevioTrackPitchData): Pitch? {
    val convertedPoints = mutableListOf<Pair<Long, Double>>()
    var currentValue: Double? = null
    var nextPos = 0.0
    for (event in data.events) {
        val pos = event.index?.let { it * TIME_UNIT_AS_TICKS } ?: nextPos
        if (pos < nextPos) continue
        val length = event.repeat?.let { it * TIME_UNIT_AS_TICKS } ?: TIME_UNIT_AS_TICKS
        nextPos = pos + length
        val value = event.value.loggedFrequencyToKey()
        if (value != currentValue) {
            convertedPoints.add(pos.roundToLong() to value)
            currentValue = value
        }
    }
    return Pitch(convertedPoints, isAbsolute = true)
}

fun Pitch.generateForCevio(notes: List<Note>): CevioTrackPitchData? {
    val endTick = notes.lastOrNull()?.tickOff ?: return null
    val data = getAbsoluteData(notes)?.takeIf { it.isNotEmpty() } ?: return null
    var nextIndex: Long? = null
    val eventsWithFullParams = mutableListOf<Event>()
    for ((thisPoint, nextPoint) in data.zipWithNext() + (data.last() to null)) {
        val index = (thisPoint.first.toDouble() / TIME_UNIT_AS_TICKS).roundToLong()
        if (nextIndex != null && nextIndex > index) {
            val lastEvent = eventsWithFullParams.last()
            eventsWithFullParams.remove(lastEvent)
            val lastEventRepeat = index - lastEvent.index!!
            if (lastEventRepeat >= 1) {
                eventsWithFullParams.add(lastEvent.copy(repeat = lastEventRepeat))
            }
        }

        val repeat = if (nextPoint?.first == null) {
            endTick - index
        } else {
            (nextPoint.first.toDouble() / TIME_UNIT_AS_TICKS).roundToLong() - index
        }.coerceAtLeast(1)
        nextIndex = index + repeat
        val value = thisPoint.second?.keyToLoggedFrequency() ?: continue
        eventsWithFullParams.add(Event(index, repeat, value))
    }
    return CevioTrackPitchData(
        eventsWithFullParams
            .let(::mergeEventsIfPossible)
            .let(::removeRedundantIndex)
            .let(::removeRedundantRepeat)
    )
}

private fun mergeEventsIfPossible(eventsWithFullParams: List<Event>) =
    eventsWithFullParams.fold(listOf<Event>()) { accumulator, thisEvent ->
        val lastEvent = accumulator.lastOrNull() ?: return@fold listOf(thisEvent)
        val areAdjacent = lastEvent.index!! + lastEvent.repeat!! == thisEvent.index
        val areValuesSame = lastEvent.value == thisEvent.value
        if (areAdjacent && areValuesSame)
            accumulator.drop(1) + lastEvent.copy(repeat = lastEvent.repeat + thisEvent.repeat!!)
        else accumulator + thisEvent
    }

private fun removeRedundantIndex(eventsWithFullParams: List<Event>) =
    (listOf(null to eventsWithFullParams.first()) + eventsWithFullParams.zipWithNext())
        .map { (lastEvent, thisEvent) ->
            if (lastEvent == null) thisEvent
            else {
                val areAdjacent = lastEvent.index!! + lastEvent.repeat!! == thisEvent.index
                if (areAdjacent) thisEvent.copy(index = null) else thisEvent
            }
        }

private fun removeRedundantRepeat(events: List<Event>) =
    events.map { if (it.repeat == 1L) it.copy(repeat = null) else it }
