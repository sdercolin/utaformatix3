package core.model

data class TimeSignature(
    val measurePosition: Int,
    val numerator: Int,
    val denominator: Int,
) {
    val displayValue get() = "$numerator/$denominator"
    val ticksInMeasure get() = TICKS_IN_FULL_NOTE * numerator / denominator

    companion object {
        val default get() = TimeSignature(0, DEFAULT_METER_HIGH, DEFAULT_METER_LOW)
    }
}
