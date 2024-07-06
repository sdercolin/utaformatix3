package core.model

class TickCounter(
    private val tickRate: Double = 1.0,
    private val ticksInFullNote: Long = TICKS_IN_FULL_NOTE.toLong(),
) {

    var tick = 0L
        private set

    val outputTick get() = (tick * tickRate).toLong()

    var measure = 0
        private set

    var numerator = DEFAULT_METER_HIGH
        private set

    var denominator = DEFAULT_METER_LOW
        private set

    val ticksInMeasure get() = ticksInFullNote * numerator / denominator

    fun goToTick(newTick: Long, newNumerator: Int? = null, newDenominator: Int? = null) {
        val normalizedNewTick = newTick / tickRate
        val tickDiff = normalizedNewTick - tick
        val measureDiff = tickDiff / ticksInMeasure
        measure += measureDiff.toInt()
        tick = normalizedNewTick.toLong()
        numerator = newNumerator ?: numerator
        denominator = newDenominator ?: denominator
    }

    fun goToMeasure(timeSignature: TimeSignature) =
        goToMeasure(timeSignature.measurePosition, timeSignature.numerator, timeSignature.denominator)

    fun goToMeasure(newMeasure: Int, newNumerator: Int? = null, newDenominator: Int? = null) {
        val measureDiff = newMeasure - measure
        val tickDiff = measureDiff * ticksInMeasure
        tick += tickDiff
        measure = newMeasure
        newNumerator?.let { numerator = it }
        newDenominator?.let { denominator = it }
    }
}
