@file:Suppress("SpellCheckingInspection")

package io

import model.ExportResult
import model.FeatureConfig
import model.Format
import model.ImportParams
import model.Project
import model.contains
import org.w3c.files.File

object Vsq {

    suspend fun parse(file: File, params: ImportParams): Project {
        return VsqLike.parse(file, format, params)
    }

    fun generate(project: Project, features: List<FeatureConfig>): ExportResult {
        return VsqLike.generate(project, features, format)
    }

    private val format = Format.Vsq
}
