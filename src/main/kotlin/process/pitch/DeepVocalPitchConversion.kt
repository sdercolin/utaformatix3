package process.pitch

import io.Dv
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
