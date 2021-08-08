package process.pitch

import io.Dv
import kotlin.math.roundToInt
import model.Note
import model.Pitch

data class DvSegmentPitchData(
    val tickOffset: Long,
    val data: List<Pair<Int, Int>>
)

fun pitchFromDvSegments(dataBySegments: List<DvSegmentPitchData>) = dataBySegments
    .mergePointsFromSegments()
    .mergeSameTickPoints()
    ?.mergeSameValuePoints()
    ?.let { Pitch(data = it, isAbsolute = true) }

private fun List<DvSegmentPitchData>.mergePointsFromSegments() = flatMap { segment ->
    segment.data
        .asSequence()
        .mapNotNull { list ->
            val rawTick = list.first.takeIf { it >= 0 } ?: return@mapNotNull null
            val centValue = list.second
            val tick = rawTick + segment.tickOffset
            val value = centValue.takeUnless { it < 0 }?.toDouble()?.div(100)?.let(Dv::convertNoteKey)
            tick to value
        }
        .toList()
}

private fun List<Pair<Long, Double?>>.mergeSameTickPoints() = asSequence()
    .groupBy { it.first }
    .map { (tick, points) ->
        if (points.size > 1) {
            if (points.any { it.second == null }) {
                tick to null
            } else {
                val values = points.mapNotNull { it.second }
                tick to values.average()
            }
        } else {
            tick to points[0].second
        }
    }
    .toList()
    .sortedBy { it.first }
    .takeIf { points -> points.any { it.second != null } }

private fun List<Pair<Long, Double?>>.mergeSameValuePoints() = asSequence()
    .fold(listOf<Pair<Long, Double?>>()) { acc, point ->
        val lastValue = acc.lastOrNull()?.second
        if (point.second != lastValue) acc + point else acc
    }

fun Pitch.generateForDv(notes: List<Note>): DvSegmentPitchData? {
    if (notes.isEmpty()) return null
    val points: List<Pair<Long, Double?>> = getAbsoluteData(notes)
        ?.takeIf { it.isNotEmpty() }
        ?: return null

    val data = listOf(-1 to -1) + points.appendPoints()
        .map {
            val rawValue = it.second?.let(Dv::convertNoteKey)?.times(100)?.roundToInt() ?: -1
            it.first.toInt() to rawValue
        }

    return DvSegmentPitchData(0L, data)
}

private fun List<Pair<Long, Double?>>.appendPoints(): List<Pair<Long, Double?>> {
    val results = mutableListOf<Pair<Long, Double?>>()
    var lastValue: Double? = null
    for (point in this) {
        if (lastValue == null && point.second != null) {
            results.add(point.first to null)
        }
        if (lastValue != null && point.second == null) {
            results.add(point.first to lastValue)
        }
        results.add(point)
        lastValue = point.second
    }
    return results.toList()
}
