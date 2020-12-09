package model

data class Track(
    val id: Int,
    val name: String,
    val notes: List<Note>,
    val pitch: Pitch? = null
)

// X - Tick; Y - Offset in semitone; Only contains changing point
typealias Pitch = List<Pair<Long, Double>>
