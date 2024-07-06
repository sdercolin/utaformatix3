package core.model

/**
 * This class contains the pitch data defined by UtaFormatix Data Format.
 * @see [com.sdercolin.utaformatix.data.Pitch]
 */
data class Pitch(
    val data: List<Pair<Long, Double?>>,
    val isAbsolute: Boolean,
)
