package io

import core.io.MusicXml
import core.io.UfData
import core.model.ImportParams
import helpers.assertMatchesSnapshot
import helpers.fixtureFile
import helpers.readFirstZipEntryText
import helpers.readZipEntryTexts
import helpers.toUfDataJson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.w3c.files.File
import kotlin.test.Test

@OptIn(DelicateCoroutinesApi::class)
class MusicXmlSnapshotTest {
    @Test
    fun testExportGolden() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = MusicXml.generate(source)
            // the export is a zip with one file per track; compare the entries as text
            assertMatchesSnapshot("musicxml_export.txt", result.blob.readZipEntryTexts())
        }

    @Test
    fun testReimportSnapshot() =
        GlobalScope.promise {
            val source = UfData.parse(fixtureFile("export_source.ufdata"), ImportParams())
            val result = MusicXml.generate(source)
            val trackContent = result.blob.readFirstZipEntryText()
            val reimported = MusicXml.parse(File(arrayOf(trackContent), "sample.musicxml"), ImportParams())
            assertMatchesSnapshot("musicxml_import.ufdata", reimported.toUfDataJson())
        }
}
