@file:Suppress("SpellCheckingInspection")

package core.io

import core.model.ExportResult
import core.model.FeatureConfig
import core.model.Format
import core.model.ImportParams
import core.model.Project
import core.model.contains
import org.w3c.files.File

object VocaloidMid {

    suspend fun parse(file: File, params: ImportParams): Project {
        return VsqLike.parse(file, format, params)
    }

    fun generate(project: Project, features: List<FeatureConfig>): ExportResult {
        return VsqLike.generate(project, features, format)
    }

    private val format = Format.VocaloidMid
}
