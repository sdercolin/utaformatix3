package io

import core.io.Svp
import core.io.UfData
import core.model.FeatureConfig
import core.model.ImportParams
import helpers.assertMatchesSnapshot
import helpers.fixtureFile
import helpers.normalizeUuids
import helpers.readText
import helpers.toUfDataJson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.test.Test

@OptIn(DelicateCoroutinesApi::class)
class SvpSnapshotTest {
    @Test
    fun testImportSnapshot() =
        GlobalScope.promise {
            val project = Svp.parse(fixtureFile("sample.svp"), ImportParams())
            assertMatchesSnapshot("svp_import.ufdata", project.toUfDataJson())
        }

    @Test
    fun testExportGolden() =
        GlobalScope.promise {
            val project = UfData.parse(fixtureFile("svp_export_source.ufdata"), ImportParams())
            val result = Svp.generate(project, listOf(FeatureConfig.ConvertPitch))
            assertMatchesSnapshot("svp_export.svp", result.blob.readText().normalizeUuids())
        }
}
