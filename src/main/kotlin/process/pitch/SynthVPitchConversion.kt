package process.pitch

private const val SAMPLING_INTERVAL_TICK = 4L

fun processSvpInputPitchData(points: List<Pair<Long, Double>>, mode: String) =
    points.merge().interpolate(mode).orEmpty()

private fun List<Pair<Long, Double>>.merge() = groupBy { it.first }
    .mapValues { it.value.sumByDouble { (_, value) -> value } / it.value.count() }
    .toList()
    .sortedBy { it.first }

private fun List<Pair<Long, Double>>.interpolate(mode: String) = when (mode) {
    "linear" -> this.interpolateLinear()
    "cosine" -> this.interpolateLinear() // TODO: interpolateCosine
    "cubic" -> this.interpolateLinear() // TODO: interpolateCubic
    else -> this.interpolateLinear()
}

private fun List<Pair<Long, Double>>.interpolateLinear() =
    this.takeIf { it.isNotEmpty() }
        ?.zipWithNext()
        ?.flatMap { (start, end) ->
            val indexes = ((start.first + 1) until end.first)
                .filter { (it - start.first) % SAMPLING_INTERVAL_TICK == 0L }
            val points = indexes.map { x ->
                val (x0, y0) = start
                val (x1, y1) = end
                val y = y0 + (x - x0) * (y1 - y0) / (x1 - x0)
                x to y
            }
            listOf(start) + points
        }
        ?.plus(this.last())
