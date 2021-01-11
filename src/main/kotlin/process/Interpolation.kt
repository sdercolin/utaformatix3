package process

fun List<Pair<Long, Double>>.interpolateLinear(samplingIntervalTick: Long) =
    this.takeIf { it.isNotEmpty() }
        ?.zipWithNext()
        ?.flatMap { (start, end) ->
            val indexes = ((start.first + 1) until end.first)
                .filter { (it - start.first) % samplingIntervalTick == 0L }
            val points = indexes.map { x ->
                val (x0, y0) = start
                val (x1, y1) = end
                val y = y0 + (x - x0) * (y1 - y0) / (x1 - x0)
                x to y
            }
            listOf(start) + points
        }
        ?.plus(this.last())
