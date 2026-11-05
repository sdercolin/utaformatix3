package io

import core.io.UfData
import core.io.Vsqx
import core.model.FeatureConfig
import core.model.ImportParams
import helpers.assertMatchesSnapshot
import helpers.fixtureFile
import helpers.readText
import helpers.toFile
import helpers.toUfDataJson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.test.Test

@OptIn(DelicateCoroutinesApi::class)
class VsqxSnapshotTest {
    @Test
    fun testExportGolden() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = Vsqx.generate(source, listOf(FeatureConfig.ConvertPitch))
            assertMatchesSnapshot("vsqx_export.vsqx", result.blob.readText())
        }

    @Test
    fun testReimportSnapshot() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = Vsqx.generate(source, listOf(FeatureConfig.ConvertPitch))
            val reimported = Vsqx.parse(result.blob.toFile("sample.vsqx"), ImportParams())
            assertMatchesSnapshot("vsqx_import.ufdata", reimported.toUfDataJson())
        }
}
