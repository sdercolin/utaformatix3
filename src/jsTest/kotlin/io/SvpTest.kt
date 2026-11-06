package io

import core.io.Svp
import core.model.ImportParams
import helpers.fixtureFile
import helpers.fixtureText
import helpers.toUfDataJson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.w3c.files.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(DelicateCoroutinesApi::class)
class SvpTest {
    @Test
    fun testParseConcatenatedProjectsWithoutSeparator() =
        GlobalScope.promise {
            val content = fixtureText("sample.svp").trim()
            val olderContent = content.replaceFirst("\"version\":113", "\"version\":100")
            val file = File(arrayOf("$content$olderContent\u0000$olderContent\u0000"), "sample.svp")
            val expected = Svp.parse(fixtureFile("sample.svp"), ImportParams())
            val actual = Svp.parse(file, ImportParams())
            assertEquals(expected.toUfDataJson(), actual.toUfDataJson())
        }
}
