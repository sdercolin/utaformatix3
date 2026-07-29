package io

import core.io.UfData
import core.io.Ustx
import core.model.FeatureConfig
import core.model.ImportParams
import helpers.assertMatchesSnapshot
import helpers.fixtureFile
import helpers.readText
import helpers.toUfDataJson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.test.Test

@OptIn(DelicateCoroutinesApi::class)
class UstxSnapshotTest {
    @Test
    fun testImportSnapshot() =
        GlobalScope.promise {
            val project = Ustx.parse(fixtureFile("sample.ustx"), ImportParams())
            assertMatchesSnapshot("ustx_import.ufdata", project.toUfDataJson())
        }

    @Test
    fun testExportGolden() =
        GlobalScope.promise {
            val project = UfData.parse(fixtureFile("ustx_export_source.ufdata"), ImportParams())
            val result = Ustx.generate(project, listOf(FeatureConfig.ConvertPitch))
            assertMatchesSnapshot("ustx_export.ustx", result.blob.readText())
        }
}
