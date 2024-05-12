package core.model

import org.w3c.files.Blob

class ExportResult(
    val blob: Blob,
    val fileName: String,
    val notifications: List<ExportNotification>,
)
