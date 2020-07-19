package model

data class Note(
    val id: Int,
    val key: Int,
    val lyric: String,
    val tickOn: Long,
    val tickOff: Long
) {
    val length get() = tickOff - tickOn
}
