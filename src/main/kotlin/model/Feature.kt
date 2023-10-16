package model

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
    )
}

sealed class FeatureConfig(val type: Feature) {
    object ConvertPitch : FeatureConfig(Feature.ConvertPitch)
    data class SplitProject(
        val maxTrackCount: Int,
    ) : FeatureConfig(Feature.SplitProject)
}

fun List<FeatureConfig>.contains(feature: Feature) = any { it.type == feature }
