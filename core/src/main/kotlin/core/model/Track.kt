package core.model

data class Track(
    val id: Int,
    val name: String,
    val notes: List<Note>,
    val pitch: Pitch? = null,
)
