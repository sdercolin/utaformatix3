package ui.model

import model.ExportResult
import model.Format
import model.Project
import ui.strings.Strings

sealed class StageInfo(
    val stage: Stage,
) {
    object Import : StageInfo(Stage.Import)
    data class SelectOutputFormat(
        val projects: List<Project>,
    ) : StageInfo(Stage.SelectOutputFormat)

    data class Configure(
        val projects: List<Project>,
        val outputFormat: Format,
    ) : StageInfo(Stage.Configure)

    data class Export(
        val results: List<ExportResult>,
        val outputFormat: Format,
    ) : StageInfo(Stage.Export)

    data class ExtraPage(
        val urlKey: Strings,
    ) : StageInfo(Stage.ExtraPage)
}
