package process.pitch

import kotlin.test.Test
import kotlin.test.assertEquals

class PitchCalculationTest {

    @Test
    fun testAppendPitchPointsForInterpolation() {
    }

    @Test
    fun testReduceRepeatedPitchPoints() {

        val input = listOf(0, 0, 0, 1, 1, 1, 2, 2, 1, 3, 2, 3, 3, 3, 3, 3, 4, 5, 5, 5)
            .mapIndexed { index: Int, i: Int -> index.toLong() to i.toDouble() }

        val expected = listOf(0, null, 0, 1, null, 1, 2, 2, 1, 3, 2, 3, null, null, null, 3, 4, 5, null, 5)
        val reduced = input.reduceRepeatedPitchPoints()
        val actual = input.indices.map { i ->
            reduced.find { it.first.toInt() == i }?.second?.toInt()
        }

        assertEquals(expected, actual)
    }
}
