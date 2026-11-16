package io

import core.external.JsZip
import core.external.JsZipOption
import core.io.UfData
import core.io.Vpr
import core.model.FeatureConfig
import core.model.ImportParams
import helpers.fixtureFile
import helpers.toFile
import helpers.toUfDataJson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import org.w3c.files.Blob
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(DelicateCoroutinesApi::class)
class VprTest {
    @Test
    fun testParseIgnoresNotesBeforePartStart() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val blob = Vpr.generate(source, listOf(FeatureConfig.ConvertPitch)).blob
            val expected = Vpr.parse(blob.toFile("sample.vpr"), ImportParams())

            val zip = JsZip().loadAsync(blob).await()
            val path = "Project\\sequence.json"
            val json = JSON.parse<dynamic>(zip.file(path)!!.async("string").await() as String)
            val notes = json.tracks[0].parts[0].notes
            val hiddenNote = JSON.parse<dynamic>(JSON.stringify(notes[0]))
            hiddenNote.pos = -42090
            notes.unshift(hiddenNote)
            zip.file(path, JSON.stringify(json))
            val option = JsZipOption().also { it.type = "blob" }
            val modified = zip.generateAsync(option).await() as Blob

            val actual = Vpr.parse(modified.toFile("sample.vpr"), ImportParams())
            assertEquals(expected.toUfDataJson(), actual.toUfDataJson())
        }
}
