package io

import core.io.S5p
import core.io.UfData
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
class S5pSnapshotTest {
    @Test
    fun testExportGolden() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = S5p.generate(source, listOf(FeatureConfig.ConvertPitch))
            assertMatchesSnapshot("s5p_export.s5p", result.blob.readText())
        }

    @Test
    fun testReimportSnapshot() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = S5p.generate(source, listOf(FeatureConfig.ConvertPitch))
            val reimported = S5p.parse(result.blob.toFile("sample.s5p"), ImportParams())
            assertMatchesSnapshot("s5p_import.ufdata", reimported.toUfDataJson())
        }
}
