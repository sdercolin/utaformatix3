package core.model

data class Tempo(
    val tickPosition: Long,
    val bpm: Double,
) {
    companion object {
        val default get() = Tempo(0, DEFAULT_BPM)
    }
}
