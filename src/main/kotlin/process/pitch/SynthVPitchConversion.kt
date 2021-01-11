package process.pitch

import process.interpolateLinear

private const val SAMPLING_INTERVAL_TICK = 4L

fun processSvpInputPitchData(points: List<Pair<Long, Double>>, mode: String) =
    points.merge().interpolate(mode).orEmpty()

private fun List<Pair<Long, Double>>.merge() = groupBy { it.first }
    .mapValues { it.value.sumByDouble { (_, value) -> value } / it.value.count() }
    .toList()
    .sortedBy { it.first }

private fun List<Pair<Long, Double>>.interpolate(mode: String) = when (mode) {
    "linear" -> this.interpolateLinear(SAMPLING_INTERVAL_TICK)
    "cosine" -> this.interpolateLinear(SAMPLING_INTERVAL_TICK) // TODO: interpolateCosine
    "cubic" -> this.interpolateLinear(SAMPLING_INTERVAL_TICK) // TODO: interpolateCubic
    else -> this.interpolateLinear(SAMPLING_INTERVAL_TICK)
}

fun appendPitchPointsForSvpOutput(points: List<Pair<Long, Double>>) =
    listOfNotNull(points.firstOrNull()) +
            points.zipWithNext()
                .flatMap { (lastPoint, thisPoint) ->
                    val tickDiff = thisPoint.first - lastPoint.first
                    val newPoint = when {
                        tickDiff < SAMPLING_INTERVAL_TICK -> null
                        tickDiff < 2 * SAMPLING_INTERVAL_TICK ->
                            ((thisPoint.first + lastPoint.first) / 2) to lastPoint.second
                        else ->
                            thisPoint.first - SAMPLING_INTERVAL_TICK to lastPoint.second
                    }
                    listOfNotNull(newPoint, thisPoint)
                }
