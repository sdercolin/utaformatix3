package io

import core.io.Dv
import core.io.UfData
import core.model.FeatureConfig
import core.model.ImportParams
import helpers.assertMatchesSnapshot
import helpers.fixtureFile
import helpers.readBase64
import helpers.toFile
import helpers.toUfDataJson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.test.Test

@OptIn(DelicateCoroutinesApi::class)
class DvSnapshotTest {
    @Test
    fun testExportGolden() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = Dv.generate(source, listOf(FeatureConfig.ConvertPitch))
            assertMatchesSnapshot("dv_export_base64.txt", result.blob.readBase64())
        }

    @Test
    fun testReimportSnapshot() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = Dv.generate(source, listOf(FeatureConfig.ConvertPitch))
            val reimported = Dv.parse(result.blob.toFile("sample.dv"), ImportParams())
            assertMatchesSnapshot("dv_import.ufdata", reimported.toUfDataJson())
        }
}
