package process

import model.Pitch

fun List<Pair<Long, Double?>>.resampled(
    sampleRate: Long,
    interpolateMethod: (
        prev: Pair<Long, Double?>?,
        next: Pair<Long, Double?>?,
        pos: Long
    ) -> Double?
): MutableList<Pair<Long, Double?>> {
    val result: MutableList<Pair<Long, Double?>> = mutableListOf()
    val length = this.map { it.first }.maxOrNull() ?: 0
    for (current in 0..length step sampleRate) {
        val prev = this.lastOrNull { it.first <= current }
        val next = this.firstOrNull { it.first >= current }
        result.add(Pair(current, interpolateMethod(prev, next, current)))
    }
    return result
}

// A shorthand for pitch represented in dot.
// Its interpolateMethod simply copy the value from prev, or next if prev not exists, or 0.0 if neither of them exists.
fun Pitch.dotResampled(sampleRate: Long) = Pitch(data.resampled(sampleRate) { prev, next, _ ->
    prev?.second ?: next?.second ?: 0.0
}, isAbsolute)
