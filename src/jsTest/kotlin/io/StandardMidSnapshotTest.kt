package io

import core.io.StandardMid
import core.io.UfData
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
class StandardMidSnapshotTest {
    @Test
    fun testExportGolden() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = StandardMid.generate(source)
            assertMatchesSnapshot("standard_mid_export_base64.txt", result.blob.readBase64())
        }

    @Test
    fun testReimportSnapshot() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = StandardMid.generate(source)
            val reimported = StandardMid.parse(result.blob.toFile("sample.mid"), ImportParams())
            assertMatchesSnapshot("standard_mid_import.ufdata", reimported.toUfDataJson())
        }
}
