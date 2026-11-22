package io

import core.io.UfData
import core.io.Vfp
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
class VfpSnapshotTest {
    @Test
    fun testExportGolden() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = Vfp.generate(source, listOf(FeatureConfig.ConvertPitch))
            // vfp is a zip; compare the entries as text to avoid zip metadata nondeterminism
            assertMatchesSnapshot("vfp_export.txt", result.blob.readZipEntryTexts())
        }

    @Test
    fun testReimportSnapshot() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = Vfp.generate(source, listOf(FeatureConfig.ConvertPitch))
            val reimported = Vfp.parse(result.blob.toFile("sample.vfp"), ImportParams())
            assertMatchesSnapshot("vfp_import.ufdata", reimported.toUfDataJson())
        }
}
