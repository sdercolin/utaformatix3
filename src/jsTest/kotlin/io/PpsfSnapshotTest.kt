package io

import core.io.Ppsf
import core.model.ImportParams
import helpers.assertMatchesSnapshot
import helpers.fixtureText
import helpers.toUfDataJson
import helpers.zippedFile
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.test.Test

@OptIn(DelicateCoroutinesApi::class)
class PpsfSnapshotTest {
    // ppsf is import-only; the fixture is the inner json, zipped in-memory like a real .ppsf
    @Test
    fun testImportSnapshot() =
        GlobalScope.promise {
            val file = zippedFile("sample.ppsf", "ppsf.json", fixtureText("ppsf_inner.txt"))
            val project = Ppsf.parse(file, ImportParams())
            assertMatchesSnapshot("ppsf_import.ufdata", project.toUfDataJson())
        }
}
