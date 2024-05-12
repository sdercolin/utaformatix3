package core.process.pitch

import core.model.Note
import core.model.Pitch
import core.process.pitch.VocaloidPartPitchData.Event
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

data class VocaloidPartPitchData(
    val startPos: Long,
    val pit: List<Event>,
    val pbs: List<Event>,
) {

    data class Event(
        val pos: Long,
        val value: Int,
    ) {
        companion object {
            fun fromPair(pair: Pair<Long, Int>) = Event(pair.first, pair.second)
        }
    }
}

private const val PITCH_MAX_VALUE = 8191
private const val DEFAULT_PITCH_BEND_SENSITIVITY = 2
private const val MIN_BREAK_LENGTH_BETWEEN_PITCH_SECTIONS = 480L
private const val BORDER_APPEND_RADIUS = 5L

fun pitchFromVocaloidParts(dataByParts: List<VocaloidPartPitchData>): Pitch? {
    val pitchRawDataByPart = dataByParts.map { part ->
        val pit = part.pit
        val pbs = part.pbs
        val pitMultipliedByPbs = mutableMapOf<Long, Int>()
        var pitIndex = 0
        var pbsCurrentValue = DEFAULT_PITCH_BEND_SENSITIVITY
        for (pbsEvent in pbs) {
            for (i in pitIndex..pit.lastIndex) {
                val pitEvent = pit[i]
                if (pitEvent.pos < pbsEvent.pos) {
                    pitMultipliedByPbs[pitEvent.pos] = pitEvent.value * pbsCurrentValue
                    if (i == pit.lastIndex) pitIndex = i
                } else {
                    pitIndex = i
                    break
                }
            }
            pbsCurrentValue = pbsEvent.value
        }
        if (pitIndex < pit.lastIndex) {
            for (i in pitIndex..pit.lastIndex) {
                val pitEvent = pit[i]
                pitMultipliedByPbs[pitEvent.pos] = pitEvent.value * pbsCurrentValue
            }
        }
        pitMultipliedByPbs.mapKeys { it.key + part.startPos }
    }
    val pitchRawData = pitchRawDataByPart.map { it.toList() }
        .fold(listOf<Pair<Long, Int>>()) { accumulator, element ->
            val firstPos = element.firstOrNull()?.first
            if (firstPos == null) accumulator
            else {
                val firstInvalidIndexInPrevious =
                    accumulator.indexOfFirst { it.first >= firstPos }.takeIf { it >= 0 }
                if (firstInvalidIndexInPrevious == null) accumulator + element
                else accumulator.take(firstInvalidIndexInPrevious) + element
            }
        }
    val data = pitchRawData.map { (pos, value) ->
        pos to value.toDouble() / PITCH_MAX_VALUE
    }
    return Pitch(data, isAbsolute = false).takeIf { it.data.isNotEmpty() }
}

fun Pitch.generateForVocaloid(notes: List<Note>): VocaloidPartPitchData? {
    val data = this.getRelativeData(notes, borderAppendRadius = BORDER_APPEND_RADIUS) ?: return null
    val pitchSectioned = mutableListOf<MutableList<Pair<Long, Double>>>()
    var currentPos = 0L
    for (pitchEvent in data) {
        when {
            pitchSectioned.isEmpty() -> pitchSectioned.add(mutableListOf(pitchEvent))
            pitchEvent.first - currentPos >= MIN_BREAK_LENGTH_BETWEEN_PITCH_SECTIONS -> {
                pitchSectioned.add(mutableListOf(pitchEvent))
            }
            else -> {
                pitchSectioned.last().add(pitchEvent)
            }
        }
        currentPos = pitchEvent.first
    }
    val pit = mutableListOf<Event>()
    val pbs = mutableListOf<Event>()
    for (section in pitchSectioned) {
        val maxAbsValue = section.maxOfOrNull { abs(it.second) } ?: 0.0
        var pbsForThisSection = ceil(abs(maxAbsValue)).toInt()
        if (pbsForThisSection > DEFAULT_PITCH_BEND_SENSITIVITY) {
            pbs.add(Event(section.first().first, pbsForThisSection))
            pbs.add(
                Event(
                    section.last().first + MIN_BREAK_LENGTH_BETWEEN_PITCH_SECTIONS / 2,
                    DEFAULT_PITCH_BEND_SENSITIVITY,
                ),
            )
        } else {
            pbsForThisSection = DEFAULT_PITCH_BEND_SENSITIVITY
        }
        section.forEach { (pitchPos, pitchValue) ->
            pit.add(
                Event(
                    pitchPos,
                    (pitchValue * PITCH_MAX_VALUE / pbsForThisSection).roundToInt()
                        .coerceIn(-PITCH_MAX_VALUE, PITCH_MAX_VALUE),
                ),
            )
        }
    }
    return VocaloidPartPitchData(
        startPos = 0,
        pit = pit,
        pbs = pbs,
    )
}
