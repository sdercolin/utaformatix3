package process.pitch

data class OpenUtauNotePitchData(
    val points: List<Point>,
    val vibrato: Vibrato
) {

    data class Point(
        val x: Double, // msec
        val y: Double, // 10 cents
        val shape: Shape
    )

    enum class Shape(val textValue: String) {
        EaseIn("i"),
        EaseOut("o"),
        EaseInOut("io"),
        Linear("l")
    }

    data class Vibrato(
        val length: Double, // percentage of the note's length
        val period: Double, // msec
        val depth: Double, // cent
        val fadeIn: Double, // percentage of the vibrato's length
        val fadeOut: Double, // percentage of the vibrato's length
        val phaseShift: Double, // percentage of period
        val shift: Double // percentage of depth
    )
}

data class OpenUtauPartPitchData(
    val points: List<Point>
) {
    data class Point(
        val x: Long, // tick
        val y: Int // cent
    )
}
