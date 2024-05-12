package core.process

fun List<Pair<Long, Double>>.interpolateLinear(samplingIntervalTick: Long) =
    this.interpolate(samplingIntervalTick) { start, end, indexes ->
        val (x0, y0) = start
        val (x1, y1) = end
        indexes.map { x ->
            val y = y0 + (x - x0) * (y1 - y0) / (x1 - x0)
            x to y
        }
    }

fun List<Pair<Long, Double>>.interpolateCosineEaseInOut(samplingIntervalTick: Long) =
    this.interpolate(samplingIntervalTick) { start, end, indexes ->
        val (x0, y0) = start
        val (x1, y1) = end
        val xOffset = x0
        val yOffset = (y0 + y1) / 2
        val aFreq = kotlin.math.PI / (x1 - x0)
        val amp = (y0 - y1) / 2
        indexes.map { x ->
            val y = amp * kotlin.math.cos(aFreq * (x - xOffset)) + yOffset
            x to y
        }
    }

fun List<Pair<Long, Double>>.interpolateCosineEaseIn(samplingIntervalTick: Long) =
    this.interpolate(samplingIntervalTick) { start, end, indexes ->
        val (x0, y0) = start
        val (x1, y1) = end
        val xOffset = x0
        val yOffset = y1
        val aFreq = kotlin.math.PI / (x1 - x0) / 2
        val amp = y0 - y1
        indexes.map { x ->
            val y = amp * kotlin.math.cos(aFreq * (x - xOffset)) + yOffset
            x to y
        }
    }

fun List<Pair<Long, Double>>.interpolateCosineEaseOut(samplingIntervalTick: Long) =
    this.interpolate(samplingIntervalTick) { start, end, indexes ->
        val (x0, y0) = start
        val (x1, y1) = end
        val xOffset = x0
        val yOffset = y0
        val aFreq = kotlin.math.PI / (x1 - x0) / 2
        val amp = y0 - y1
        val phase = kotlin.math.PI / 2
        indexes.map { x ->
            val y = amp * kotlin.math.cos(aFreq * (x - xOffset) + phase) + yOffset
            x to y
        }
    }

private fun List<Pair<Long, Double>>.interpolate(
    samplingIntervalTick: Long,
    mapping: (start: Pair<Long, Double>, end: Pair<Long, Double>, input: List<Long>) -> List<Pair<Long, Double>>,
) = this.takeIf { it.isNotEmpty() }
    ?.zipWithNext()
    ?.flatMap { (start, end) ->
        val indexes = ((start.first + 1) until end.first)
            .filter { (it - start.first) % samplingIntervalTick == 0L }
        val points = mapping(start, end, indexes)
        listOf(start) + points
    }
    ?.plus(this.last())
