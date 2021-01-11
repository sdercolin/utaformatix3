package process.pitch

data class UtauTrackPitchData(
    val notes: List<UtauNotePitchData>
)

data class UtauNotePitchData(
    val bpm: Double?,
    val start: Double, // msec
    val startShift: Double, // 10 cents
    val widths: List<Double>, // msec
    val shifts: List<Double>, // 10 cents
    val types: List<String> // (blank)/s/r/j
)
