package io

import core.io.Tssln
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
class TsslnSnapshotTest {
    @Test
    fun testExportGolden() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = Tssln.generate(source)
            assertMatchesSnapshot("tssln_export_base64.txt", result.blob.readBase64())
        }

    @Test
    fun testReimportSnapshot() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = Tssln.generate(source)
            val reimported = Tssln.parse(result.blob.toFile("sample.tssln"), ImportParams())
            assertMatchesSnapshot("tssln_import.ufdata", reimported.toUfDataJson())
        }
}
