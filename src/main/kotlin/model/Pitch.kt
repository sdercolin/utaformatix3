package model

/**
 * This class contains the pitch data defined by UtaFormatix
 * @param data The pitch data, as a list of point (tick, semitone), lasting until the next point
 * @param isAbsolute True if the semitone value is absolute, otherwise it's relative to the note key
 */
data class Pitch(
    val data: List<Pair<Long, Double?>>,
    val isAbsolute: Boolean
)
