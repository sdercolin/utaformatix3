package core.model

import kotlinx.serialization.Serializable

@Serializable
data class ImportParams(
    val simpleImport: Boolean = false,
    val multipleMode: Boolean = false,
    val defaultLyric: String = "あ",
)
