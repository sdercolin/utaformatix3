package process.pitch

import model.Note
import model.Pitch
import model.Tempo
import process.pitch.CevioTrackPitchData.Event
import kotlin.math.max
import kotlin.math.roundToLong

data class CevioTrackPitchData(
    val events: List<Event>,
    val tempos: List<Tempo>, // normalized
    val tickPrefix: Long // normalized
) {

    data class Event(
        val index: Long?, // raw value
        val repeat: Long?, // raw value
        val value: Double
    )
}

private const val TIME_UNIT_AS_TICKS_PER_BPM = 4.8 / 120
private const val MIN_DATA_LENGTH = 500

fun pitchFromCevioTrack(data: CevioTrackPitchData): Pitch? {
    val convertedPoints = mutableListOf<Pair<Long, Double?>>()
    var currentValue: Double? = null

    val eventsNormalized = data
        .let(::normalizeToTick)
        .let(::shapeEvents)

    var nextPos: Long? = null
    for (event in eventsNormalized) {
        val pos = event.index!! - data.tickPrefix
        val length = event.repeat!!
        val value = event.value.loggedFrequencyToKey()
        if (value != currentValue || nextPos != pos) {
            if (nextPos != null && nextPos < pos) {
                convertedPoints.add(nextPos to null)
            }
            convertedPoints.add(pos to value)
            currentValue = value
        }
        nextPos = pos + length
    }
    if (nextPos != null) {
        convertedPoints.add(nextPos to null)
    }
    val lastMinusPosPoint = convertedPoints.lastOrNull { it.first < 0 && it.second != null }
    if (lastMinusPosPoint != null) {
        repeat(convertedPoints.indexOf(lastMinusPosPoint) + 1) {
            convertedPoints.removeAt(0)
        }
        val firstPositivePosPoint = convertedPoints.firstOrNull()
        if (firstPositivePosPoint != null && firstPositivePosPoint.first > 0) {
            convertedPoints.add(0L to lastMinusPosPoint.second)
        }
    }
    return Pitch(convertedPoints, isAbsolute = true).takeIf { it.data.isNotEmpty() }
}

private fun normalizeToTick(data: CevioTrackPitchData): List<Event> {
    val tempos = data.tempos
        .map { it.copy(tickPosition = it.tickPosition + data.tickPrefix) }
        .expand()
    val eventsNormalized = mutableListOf<Event>()
    var currentTempoIndex = 0
    var nextPos = 0L
    var nextTickPos = 0L
    for (event in data.events) {
        val pos = event.index ?: nextPos
        val tickPos = if (event.index == null) nextTickPos
        else {
            while (tempos.getOrNull(currentTempoIndex + 1)?.let { it.first <= event.index } == true) {
                currentTempoIndex++
            }
            val ticksInTimeUnit = TIME_UNIT_AS_TICKS_PER_BPM * tempos[currentTempoIndex].third
            tempos[currentTempoIndex].second +
                    ((event.index - tempos[currentTempoIndex].first) * ticksInTimeUnit).toLong()
        }
        val repeat = event.repeat ?: 1
        var remainingRepeat = repeat
        var repeatInTicks = 0L
        while (tempos.getOrNull(currentTempoIndex + 1)?.let { it.first < pos + repeat } == true) {
            repeatInTicks += tempos[currentTempoIndex + 1].second - max(tempos[currentTempoIndex].second, tickPos)
            remainingRepeat -= tempos[currentTempoIndex + 1].first - max(tempos[currentTempoIndex].first, pos)
            currentTempoIndex++
        }
        repeatInTicks += (remainingRepeat * TIME_UNIT_AS_TICKS_PER_BPM * tempos[currentTempoIndex].third).toLong()
        nextPos = pos + repeat
        nextTickPos = tickPos + repeatInTicks
        eventsNormalized.add(Event(index = tickPos, repeat = repeatInTicks, value = event.value))
    }
    return eventsNormalized
}

private fun List<Tempo>.expand() = fold(listOf<Triple<Long, Long, Double>>()) { accumulator, element ->
    if (accumulator.isEmpty()) listOf(Triple(0L, 0L, element.bpm))
    else {
        val (lastPos, lastTickPos, lastBpm) = accumulator.last()
        val ticksInTimeUnit = TIME_UNIT_AS_TICKS_PER_BPM * lastBpm
        val newPos = lastPos + ((element.tickPosition - lastTickPos).toDouble() / ticksInTimeUnit).toLong()
        accumulator + Triple(newPos, element.tickPosition, element.bpm)
    }
}

private fun shapeEvents(eventsWithFullParams: List<Event>): List<Event> {
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
    val eventsWithFullParams = mutableListOf<Event>()
    for ((thisPoint, nextPoint) in data.zipWithNext() + (data.last() to null)) {
        val index = thisPoint.first
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
            nextPoint.first - index
        }.coerceAtLeast(1)
        nextIndex = index + repeat
        val value = thisPoint.second?.keyToLoggedFrequency() ?: continue
        eventsWithFullParams.add(Event(index, repeat, value)) // in ticks temporarily
    }
    return CevioTrackPitchData(
        denormalizeFromTick(eventsWithFullParams, tempos, tickPrefix)
            .let(::mergeEventsIfPossible)
            .let(::removeRedundantIndex)
            .let(::removeRedundantRepeat),
        listOf(), // not used
        tickPrefix
    )
}

fun CevioTrackPitchData.getLength(): Long {
    val lastEventWithIndex = this.events.findLast { it.index != null }!!
    var length = lastEventWithIndex.index!!
    for (index in this.events.indexOf(lastEventWithIndex)..this.events.lastIndex) {
        length += this.events[index].repeat ?: 1
    }
    return length + MIN_DATA_LENGTH
}

private fun denormalizeFromTick(
    eventsWithFullParams: List<Event>,
    temposInTicks: List<Tempo>,
    tickPrefix: Long
): List<Event> {
    val tempos = temposInTicks
        .map { if (it.tickPosition == 0L) it else it.copy(tickPosition = it.tickPosition + tickPrefix) }
        .expand()
    val events = eventsWithFullParams.map { it.copy(index = it.index?.plus(tickPrefix)) }
    val result = mutableListOf<Event>()
    var currentTempoIndex = 0
    for (event in events) {
        val tickPos = event.index!!
        while (tempos.getOrNull(currentTempoIndex + 1)?.let { it.second <= tickPos } == true) {
            currentTempoIndex++
        }
        val ticksInTimeUnit = TIME_UNIT_AS_TICKS_PER_BPM * tempos[currentTempoIndex].third
        val pos =
            tempos[currentTempoIndex].first + ((tickPos - tempos[currentTempoIndex].second) / ticksInTimeUnit).roundToLong()
        val repeatInTicks = event.repeat!!
        var remainingRepeatInTicks = repeatInTicks
        var repeat = 0L
        while (tempos.getOrNull(currentTempoIndex + 1)?.let { it.second < tickPos + repeatInTicks } == true) {
            repeat += tempos[currentTempoIndex + 1].first - max(tempos[currentTempoIndex].first, pos)
            remainingRepeatInTicks -= tempos[currentTempoIndex + 1].second - max(
                tempos[currentTempoIndex].second,
                tickPos
            )
            currentTempoIndex++
        }
        repeat += (remainingRepeatInTicks / (TIME_UNIT_AS_TICKS_PER_BPM * tempos[currentTempoIndex].third)).roundToLong()
        result.add(Event(index = pos, repeat = repeat.coerceAtLeast(1), value = event.value))
    }
    return result
}

private fun mergeEventsIfPossible(eventsWithFullParams: List<Event>) =
    eventsWithFullParams.fold(listOf<Event>()) { accEvents, thisEvent ->
        val lastEvent = accEvents.lastOrNull() ?: return@fold listOf(thisEvent)
        val areOverlapped = lastEvent.index!! + lastEvent.repeat!! > thisEvent.index!!
        if (areOverlapped) {
            val lastEventAsPoints = (lastEvent.index until (lastEvent.index + lastEvent.repeat))
                .map { it to lastEvent.value }
            val thisEventAsPoints = (thisEvent.index until (thisEvent.index + thisEvent.repeat!!))
                .map { it to thisEvent.value }
            val mergedPoints = (lastEventAsPoints + thisEventAsPoints)
                .groupBy { it.first }
                .map { (key, value) ->
                    key to (value.sumByDouble { it.second } / value.count())
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
            if (areAdjacent && areValuesSame)
                accEvents.dropLast(1) + lastEvent.copy(repeat = lastEvent.repeat + thisEvent.repeat!!)
            else accEvents + thisEvent
        }
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
