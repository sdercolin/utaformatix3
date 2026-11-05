package io

import core.io.Ccs
import core.io.UfData
import core.model.FeatureConfig
import core.model.ImportParams
import helpers.assertMatchesSnapshot
import helpers.fixtureFile
import helpers.normalizeUuids
import helpers.readText
import helpers.toFile
import helpers.toUfDataJson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.test.Test

@OptIn(DelicateCoroutinesApi::class)
class CcsSnapshotTest {
    @Test
    fun testExportGolden() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = Ccs.generate(source, listOf(FeatureConfig.ConvertPitch))
            assertMatchesSnapshot("ccs_export.ccs", result.blob.readText().normalizeUuids())
        }

    @Test
    fun testReimportSnapshot() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = Ccs.generate(source, listOf(FeatureConfig.ConvertPitch))
            val reimported = Ccs.parse(result.blob.toFile("sample.ccs"), ImportParams())
            assertMatchesSnapshot("ccs_import.ufdata", reimported.toUfDataJson())
        }
}
