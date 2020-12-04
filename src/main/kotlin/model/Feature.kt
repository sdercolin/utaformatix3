package model

enum class Feature(val isAvailable: (Project) -> Boolean) {
    CONVERT_PITCH(
        isAvailable = { project ->
            project.tracks.any { it.pitchData != null }
        }
    )
}
