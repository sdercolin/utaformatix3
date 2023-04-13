@file:Suppress("SpellCheckingInspection")

package io

import model.ExportResult
import model.Feature
import model.Format
import model.ImportParams
import model.Project
import org.w3c.files.File

object VocaloidMid {

    suspend fun parse(file: File, params: ImportParams): Project {
        return VsqLike.parse(file, format, params)
    }

    fun generate(project: Project, features: List<Feature>): ExportResult {
        return VsqLike.generate(project, features, format)
    }

    private val format = Format.VocaloidMid
}
