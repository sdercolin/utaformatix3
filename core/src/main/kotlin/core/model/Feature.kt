package core.model

/**
 * Features that are applied during format-specific generation.
 *
 * @property isAvailable Whether this feature is available for the project.
 */
enum class Feature(val isAvailable: (Project) -> Boolean) {
    ConvertPitch(
        isAvailable = { project ->
            project.tracks.any { it.pitch != null }
        },
    ),
    SplitProject(
        isAvailable = { true },
    ),
    ConvertPhonemes(
        isAvailable = { project ->
            project.tracks.any { track -> track.notes.any { note -> note.phoneme != null } }
        },
    )
}

sealed class FeatureConfig(val type: Feature) {
    object ConvertPitch : FeatureConfig(Feature.ConvertPitch)
    data class SplitProject(
        val maxTrackCount: Int,
    ) : FeatureConfig(Feature.SplitProject) {

        companion object {
            fun getDefault(format: Format): SplitProject = when (format) {
                Format.Svp -> SplitProject(3)
                else -> SplitProject(1)
            }
        }
    }
}

fun List<FeatureConfig>.contains(feature: Feature) = any { it.type == feature }
