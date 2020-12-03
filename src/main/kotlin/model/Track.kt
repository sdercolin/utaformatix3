package model

data class Track(
    val id: Int,
    val name: String,
    val notes: List<Note>,
    // X - Tick; Y - Offset in semitone; Only contains changing point
    val pitchData: List<Pair<Long, Double>>? = null
)
