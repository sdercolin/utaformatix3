package io

import core.io.UfData
import core.io.Vpr
import core.model.FeatureConfig
import core.model.ImportParams
import helpers.assertMatchesSnapshot
import helpers.fixtureFile
import helpers.readZipEntryTexts
import helpers.toFile
import helpers.toUfDataJson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.test.Test

@OptIn(DelicateCoroutinesApi::class)
class VprSnapshotTest {
    @Test
    fun testExportGolden() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = Vpr.generate(source, listOf(FeatureConfig.ConvertPitch))
            // vpr is a zip; compare the entries as text to avoid zip metadata nondeterminism
            assertMatchesSnapshot("vpr_export.txt", result.blob.readZipEntryTexts())
        }

    @Test
    fun testReimportSnapshot() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = Vpr.generate(source, listOf(FeatureConfig.ConvertPitch))
            val reimported = Vpr.parse(result.blob.toFile("sample.vpr"), ImportParams())
            assertMatchesSnapshot("vpr_import.ufdata", reimported.toUfDataJson())
        }
}
