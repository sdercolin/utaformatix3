package process.pitch

import core.model.Tempo
import core.process.pitch.TickTimeTransformer
import kotlin.test.Test
import kotlin.test.assertEquals

class TickTimeTransformerTest {

    // when bpm = 125.0, 1 tick = 1 milli-second
    private val transformer = TickTimeTransformer(
        listOf(
            Tempo(0, 125.0),
            Tempo(10000, 250.0),
            Tempo(20000, 125.0),
        ),
    )

    @Test
    fun testTickToSec() {
        val ticks = listOf(0L, 5000L, 10000L, 15000L, 20000L, 30000L)
        val actual = ticks.map { transformer.tickToSec(it) }
        val expected = listOf(0.0, 5.0, 10.0, 12.5, 15.0, 25.0)
        assertEquals(expected, actual)
    }

    @Test
    fun testSecToTick() {
        val seconds = listOf(0.0, 5.0, 10.0, 12.5, 15.0, 25.0)
        val actual = seconds.map { transformer.secToTick(it) }
        val expected = listOf(0L, 5000L, 10000L, 15000L, 20000L, 30000L)
        assertEquals(expected, actual)
    }

    @Test
    fun testTickDistanceToSec() {
        val start = 8000L
        val end = 12000L
        val actual = transformer.tickDistanceToSec(start, end)
        val expected = 3.0
        assertEquals(expected, actual)
    }

    @Test
    fun testNegativeInput() {
        val second = -5.0
        val tick = -5000L
        println(transformer.secToTick(second))
        assertEquals(tick, transformer.secToTick(second))
        assertEquals(second, transformer.tickToSec(tick))
    }
}
