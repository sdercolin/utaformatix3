package io

import core.io.UfData
import core.io.VocaloidMid
import core.io.Vsq
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
class VsqLikeSnapshotTest {
    @Test
    fun testVsqExportGolden() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = Vsq.generate(source, listOf(FeatureConfig.ConvertPitch))
            assertMatchesSnapshot("vsq_export_base64.txt", result.blob.readBase64())
        }

    @Test
    fun testVsqReimportSnapshot() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = Vsq.generate(source, listOf(FeatureConfig.ConvertPitch))
            val reimported = Vsq.parse(result.blob.toFile("sample.vsq"), ImportParams())
            assertMatchesSnapshot("vsq_import.ufdata", reimported.toUfDataJson())
        }

    @Test
    fun testVocaloidMidExportGolden() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = VocaloidMid.generate(source, listOf(FeatureConfig.ConvertPitch))
            assertMatchesSnapshot("vocaloid_mid_export_base64.txt", result.blob.readBase64())
        }

    @Test
    fun testVocaloidMidReimportSnapshot() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = VocaloidMid.generate(source, listOf(FeatureConfig.ConvertPitch))
            val reimported = VocaloidMid.parse(result.blob.toFile("sample.mid"), ImportParams())
            assertMatchesSnapshot("vocaloid_mid_import.ufdata", reimported.toUfDataJson())
        }
}
