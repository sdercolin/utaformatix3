package io

import core.io.Ust
import core.model.ImportParams
import helpers.assertMatchesSnapshot
import helpers.fixtureFile
import helpers.toUfDataJson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.test.Test

@OptIn(DelicateCoroutinesApi::class)
class UstSnapshotTest {
    @Test
    fun testImportSnapshot() =
        GlobalScope.promise {
            val project = Ust.parse(listOf(fixtureFile("sample.ust")), ImportParams())
            assertMatchesSnapshot("ust_import.ufdata", project.toUfDataJson())
        }
}
