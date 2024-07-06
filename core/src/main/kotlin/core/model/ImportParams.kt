package core.model

import kotlinx.serialization.Serializable

@OptIn(ExperimentalJsExport::class)
@Serializable
@JsExport
data class ImportParams(
    val simpleImport: Boolean = false,
    val multipleMode: Boolean = false,
    val defaultLyric: String = "あ",
)
