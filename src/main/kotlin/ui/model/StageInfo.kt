package ui.model

import model.ExportResult
import model.Format
import model.Project

sealed class StageInfo(
    val stage: Stage
) {
    object Import : StageInfo(Stage.Import)
    data class SelectOutputFormat(
        val project: Project
    ) : StageInfo(Stage.SelectOutputFormat)

    data class Configure(
        val project: Project,
        val outputFormat: Format
    ) : StageInfo(Stage.Configure)

    data class Export(
        val project: Project,
        val result: ExportResult,
        val outputFormat: Format
    ) : StageInfo(Stage.Export)

    data class ExtraPage(
        val url: String
    ) : StageInfo(Stage.ExtraPage)
}
