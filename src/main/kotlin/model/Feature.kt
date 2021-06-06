package model

enum class Feature(val isAvailable: (Project) -> Boolean) {
    ConvertPitch(
        isAvailable = { project ->
            project.tracks.any { it.pitch != null }
        }
    )
}
