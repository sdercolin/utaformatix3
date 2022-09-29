package process.pitch

import model.Note
import model.Pitch
import model.Tempo
import process.pitch.CevioTrackPitchData.Event
import util.runIf
import kotlin.math.max
import kotlin.math.roundToLong

data class CevioTrackPitchData(
    val events: List<Event>,
    val tempos: List<Tempo>, // normalized
    val tickPrefix: Long, // normalized
) {

    data class Event(
        val index: Long?, // raw value
        val repeat: Long?, // raw value
        val value: Double,
    )
}

/**
 * Container of {CevioTrackPitchData.Event} with double values
 * Used during calculation
 */
private data class EventDouble(
    val index: Double?,
    val repeat: Double?,
    val value: Double?,
) {
    fun round() =
        if (this.value == null) null
        else Event(
            index = this.index?.roundToLong(),
            repeat = this.repeat?.roundToLong(),
            value = this.value,
        )

    companion object {
        fun from(event: Event) = EventDouble(
            index = event.index?.toDouble(),
            repeat = event.repeat?.toDouble(),
            value = event.value,
        )
    }
}

private const val TIME_UNIT_AS_TICKS_PER_BPM = 4.8 / 120
private const val MIN_DATA_LENGTH = 500
private const val TEMP_VALUE_AS_NULL = -1.0

fun pitchFromCevioTrack(data: CevioTrackPitchData): Pitch? {
    val convertedPoints = mutableListOf<Pair<Double, Double?>>()
    var currentValue: Double? = null

    val eventsNormalized = data
        .let(::appendEndingPoints)
        .let(::normalizeToTick)
        .let(::shapeEvents)

    var nextPos: Double? = null
    for (event in eventsNormalized) {
        val pos = event.index!! - data.tickPrefix
        val length = event.repeat!!
        val value = event.value?.loggedFrequencyToKey()
        if (value != currentValue || nextPos != pos) {
            convertedPoints.add(pos to value)
            currentValue = value
        }
        nextPos = pos + length
    }

    val lastMinusPosPoint = convertedPoints.lastOrNull { it.first < 0 && it.second != null }
    if (lastMinusPosPoint != null) {
        repeat(convertedPoints.indexOf(lastMinusPosPoint) + 1) {
            convertedPoints.removeAt(0)
        }
        val firstPositivePosPoint = convertedPoints.firstOrNull()
        if (firstPositivePosPoint != null && firstPositivePosPoint.first > 0) {
            convertedPoints.add(0.0 to lastMinusPosPoint.second)
        }
    }
    return Pitch(convertedPoints.map { it.first.roundToLong() to it.second }, isAbsolute = true)
        .takeIf { it.data.isNotEmpty() }
}

private fun appendEndingPoints(data: CevioTrackPitchData): CevioTrackPitchData {
    val result = mutableListOf<Event>()
    var nextPos: Long? = null
    for (event in data.events) {
        val pos = requireNotNull(event.index ?: nextPos)
        val length = event.repeat ?: 1
        if (nextPos != null && nextPos < pos) {
            result.add(Event(nextPos, null, TEMP_VALUE_AS_NULL))
        }
        result.add(Event(pos, length, event.value))
        nextPos = pos + length
    }
    if (nextPos != null) {
        result.add(Event(nextPos, null, TEMP_VALUE_AS_NULL))
    }
    return data.copy(events = result)
}

private fun normalizeToTick(data: CevioTrackPitchData): List<EventDouble> {
    val tempos = data.tempos
        .map { it.copy(tickPosition = it.tickPosition + data.tickPrefix) }
        .expand()
    val events = data.events.map { EventDouble.from(it) }
    val eventsNormalized = mutableListOf<EventDouble>()
    var currentTempoIndex = 0
    var nextPos = 0.0
    var nextTickPos = 0.0
    for (event in events) {
        val pos = event.index ?: nextPos
        val tickPos = if (event.index == null) nextTickPos
        else {
            while (tempos.getOrNull(currentTempoIndex + 1)?.let { it.first <= event.index } == true) {
                currentTempoIndex++
            }
            val ticksInTimeUnit = TIME_UNIT_AS_TICKS_PER_BPM * tempos[currentTempoIndex].third
            tempos[currentTempoIndex].second + (event.index - tempos[currentTempoIndex].first) * ticksInTimeUnit
        }
        val repeat = event.repeat ?: 1.0
        var remainingRepeat = repeat
        var repeatInTicks = 0.0
        while (tempos.getOrNull(currentTempoIndex + 1)?.let { it.first < pos + repeat } == true) {
            repeatInTicks += tempos[currentTempoIndex + 1].second - max(tempos[currentTempoIndex].second, tickPos)
            remainingRepeat -= tempos[currentTempoIndex + 1].first - max(tempos[currentTempoIndex].first, pos)
            currentTempoIndex++
        }
        repeatInTicks += remainingRepeat * TIME_UNIT_AS_TICKS_PER_BPM * tempos[currentTempoIndex].third
        nextPos = pos + repeat
        nextTickPos = tickPos + repeatInTicks
        eventsNormalized.add(EventDouble(index = tickPos, repeat = repeatInTicks, value = event.value))
    }
    return eventsNormalized.map {
        it.copy(value = if (it.value == TEMP_VALUE_AS_NULL) null else it.value)
    }
}

private fun List<Tempo>.expand() = fold(listOf<Triple<Double, Double, Double>>()) { accumulator, element ->
    if (accumulator.isEmpty()) listOf(Triple(0.0, 0.0, element.bpm))
    else {
        val (lastPos, lastTickPos, lastBpm) = accumulator.last()
        val ticksInTimeUnit = TIME_UNIT_AS_TICKS_PER_BPM * lastBpm
        val newPos = lastPos + (element.tickPosition - lastTickPos) / ticksInTimeUnit
        accumulator + Triple(newPos, element.tickPosition.toDouble(), element.bpm)
    }
}

private fun shapeEvents(eventsWithFullParams: List<EventDouble>): List<EventDouble> {
    return eventsWithFullParams.filter { it.repeat!! > 0 }
        .fold(listOf()) { accumulator, event ->
            val last = accumulator.lastOrNull()
            if (last == null) listOf(event)
            else {
                if (last.index == event.index) accumulator.dropLast(1) + event
                else accumulator + event
            }
        }
}

fun Pitch.generateForCevio(notes: List<Note>, tempos: List<Tempo>, tickPrefix: Long): CevioTrackPitchData? {
    val endTick = notes.lastOrNull()?.tickOff ?: return null
    val data = getAbsoluteData(notes)
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    var nextIndex: Long? = null
    val eventsWithFullParams = mutableListOf<EventDouble>()
    for ((thisPoint, nextPoint) in data.zipWithNext() + (data.last() to null)) {
        val index = thisPoint.first
        if (nextIndex != null && nextIndex > index) {
            val lastEvent = eventsWithFullParams.lastOrNull()
            if (lastEvent != null) {
                eventsWithFullParams.remove(lastEvent)
                val lastEventRepeat = index - lastEvent.index!!
                if (lastEventRepeat >= 1) {
                    eventsWithFullParams.add(lastEvent.copy(repeat = lastEventRepeat))
                }
            }
        }

        val repeat = if (nextPoint?.first == null) {
            endTick - index
        } else {
            nextPoint.first - index
        }.coerceAtLeast(1)
        nextIndex = index + repeat
        val value = thisPoint.second?.keyToLoggedFrequency() ?: continue
        eventsWithFullParams.add(EventDouble(index.toDouble(), repeat.toDouble(), value)) // in ticks temporarily
    }
    val areEventsConnectedToNext = eventsWithFullParams.plus(null).zipWithNext().map {
        val thisEvent = it.first!!
        val nextEvent = it.second ?: return@map false
        val repeat = thisEvent.repeat ?: 1.0
        thisEvent.index!! + repeat >= nextEvent.index!!
    }
    val events = denormalizeFromTick(eventsWithFullParams, tempos, tickPrefix)
        .restoreConnection(areEventsConnectedToNext)
        .let(::mergeEventsIfPossible)
        .let(::removeRedundantIndex)
        .let(::removeRedundantRepeat)
        .takeIf { it.isNotEmpty() } ?: return null
    return CevioTrackPitchData(
        events,
        listOf(), // not used
        tickPrefix,
    )
}

fun CevioTrackPitchData.getLength(): Long {
    val lastEventWithIndex = this.events.findLast { it.index != null }!!
    var length = lastEventWithIndex.index!!
    for (index in this.events.indexOf(lastEventWithIndex)..this.events.lastIndex) {
        length += this.events[index].repeat ?: 1L
    }
    return length + MIN_DATA_LENGTH
}

private fun denormalizeFromTick(
    eventsWithFullParams: List<EventDouble>,
    temposInTicks: List<Tempo>,
    tickPrefix: Long,
): List<Event> {
    val tempos = temposInTicks
        .map { it.runIf(it.tickPosition != 0L) { copy(tickPosition = it.tickPosition + tickPrefix) } }
        .expand()
    val events = eventsWithFullParams.map { it.copy(index = it.index?.plus(tickPrefix)) }

    var currentTempoIndex = 0
    return events.mapNotNull { event ->
        val tickPos = event.index!!
        while (tempos.getOrNull(currentTempoIndex + 1)?.let { it.second <= tickPos } == true) {
            currentTempoIndex++
        }
        val ticksInTimeUnit = TIME_UNIT_AS_TICKS_PER_BPM * tempos[currentTempoIndex].third
        val pos =
            tempos[currentTempoIndex].first + ((tickPos - tempos[currentTempoIndex].second) / ticksInTimeUnit)
        val repeatInTicks = event.repeat!!
        var remainingRepeatInTicks = repeatInTicks
        var repeat = 0.0
        while (tempos.getOrNull(currentTempoIndex + 1)?.let { it.second < tickPos + repeatInTicks } == true) {
            repeat += tempos[currentTempoIndex + 1].first - max(tempos[currentTempoIndex].first, pos)
            remainingRepeatInTicks -= tempos[currentTempoIndex + 1].second - max(
                tempos[currentTempoIndex].second,
                tickPos,
            )
            currentTempoIndex++
        }
        repeat += (remainingRepeatInTicks / (TIME_UNIT_AS_TICKS_PER_BPM * tempos[currentTempoIndex].third))
        EventDouble(
            index = pos,
            repeat = repeat.coerceAtLeast(1.0),
            value = event.value,
        ).round()
    }
}

private fun List<Event>.restoreConnection(connected: List<Boolean>): List<Event> {
    return this.plus(null)
        .zipWithNext()
        .mapIndexed { index, pair ->
            val (thisEvent, nextEvent) = pair
            requireNotNull(thisEvent)
            if (nextEvent == null) return@mapIndexed thisEvent
            if (connected[index]) {
                thisEvent.copy(repeat = nextEvent.index!! - thisEvent.index!!)
            } else {
                thisEvent
            }
        }
}

private fun mergeEventsIfPossible(eventsWithFullParams: List<Event>) =
    eventsWithFullParams.fold(listOf<Event>()) { accEvents, thisEvent ->
        val lastEvent = accEvents.lastOrNull() ?: return@fold listOf(thisEvent)
        val areOverlapped = lastEvent.index!! + lastEvent.repeat!! > thisEvent.index!!
        if (areOverlapped) {
            val lastEventAsPoints = (lastEvent.index until (lastEvent.index + lastEvent.repeat))
                .map { it to requireNotNull(lastEvent.value) }
            val thisEventAsPoints = (thisEvent.index until (thisEvent.index + thisEvent.repeat!!))
                .map { it to requireNotNull(thisEvent.value) }
            val mergedPoints = (lastEventAsPoints + thisEventAsPoints)
                .groupBy { it.first }
                .map { (key, value) ->
                    key to (value.sumOf { it.second } / value.count())
                }
                .sortedBy { it.first }
            val mergedEvents = mergedPoints
                .fold(listOf<Event>()) { acc, element ->
                    val last = acc.lastOrNull()
                    when {
                        last == null -> listOf(Event(element.first, 1, element.second))
                        lastEvent.value == element.second ->
                            acc.dropLast(1) + last.copy(repeat = (last.repeat ?: 1) + 1)
                        else -> acc + Event(element.first, 1, element.second)
                    }
                }
            accEvents.dropLast(1) + mergedEvents
        } else {
            val areAdjacent = lastEvent.index + lastEvent.repeat == thisEvent.index
            val areValuesSame = lastEvent.value == thisEvent.value
            if (areAdjacent && areValuesSame) {
                accEvents.dropLast(1) + lastEvent.copy(repeat = lastEvent.repeat + thisEvent.repeat!!)
            } else accEvents + thisEvent
        }
    }

private fun removeRedundantIndex(eventsWithFullParams: List<Event>) =
    if (eventsWithFullParams.isEmpty()) {
        eventsWithFullParams
    } else {
        (listOf(null to eventsWithFullParams.first()) + eventsWithFullParams.zipWithNext())
            .map { (lastEvent, thisEvent) ->
                if (lastEvent == null) thisEvent
                else {
                    val areAdjacent = lastEvent.index!! + lastEvent.repeat!! == thisEvent.index
                    if (areAdjacent) thisEvent.copy(index = null) else thisEvent
                }
            }
    }

private fun removeRedundantRepeat(events: List<Event>) =
    events.map { it.runIf(it.repeat == 1L) { copy(repeat = null) } }
