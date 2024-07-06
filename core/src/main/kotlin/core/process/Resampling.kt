package core.process

fun List<Pair<Long, Double?>>.resampled(
    interval: Long,
    interpolateMethod: (
        prev: Pair<Long, Double?>?,
        next: Pair<Long, Double?>?,
        pos: Long,
    ) -> Double?,
): List<Pair<Long, Double?>> {
    val result: MutableList<Pair<Long, Double?>> = mutableListOf()
    val leftBound = this.minOfOrNull { it.first } ?: 0
    val rightBound = this.maxOfOrNull { it.first } ?: 0
    for (current in leftBound..rightBound step interval) {
        val prev = this.lastOrNull { it.first <= current }
        val next = this.firstOrNull { it.first >= current }
        result.add(Pair(current, interpolateMethod(prev, next, current)))
    }
    return result.toList()
}

/**
 * A shorthand for pitch represented in dot.
 * Its interpolateMethod simply copy the value from prev, or next if prev not exists.
 */
fun List<Pair<Long, Double?>>.dotResampled(interval: Long) = resampled(interval) { prev, next, _ ->
    prev?.second ?: next?.second
}
