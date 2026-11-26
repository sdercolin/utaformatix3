package io

import core.external.JsZip
import core.model.ExportResult
import core.model.FeatureConfig
import core.model.Format
import core.model.ImportParams
import helpers.toFile
import helpers.zippedFile
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import org.w3c.files.Blob
import org.w3c.files.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(DelicateCoroutinesApi::class)
class VprxConversionTest {
    private val params = ImportParams()
    private val pitchFeatures = listOf(FeatureConfig.ConvertPitch)

    @Test
    fun vocaloid7CanExportToEveryRegisteredOutput() =
        GlobalScope.promise {
            val source = Format.Vprx.parser(listOf(vocaloid7Fixture()), params)
            val expected = source.tracks.flatMap { it.notes }.map { Triple(it.tickOn, it.tickOff, it.key) }
            for (format in Format.exportable) {
                val result = format.generator(source, pitchFeatures)
                assertTrue(result.blob.size.toLong() > 0L, "Nonempty ${format.displayName} output")
                val file = result.singleProjectFile()
                assertTrue(file.name.endsWith(".${format.extension}"), format.displayName)
                val readBack = format.parser(listOf(file), params)
                val notes = readBack.tracks.flatMap { it.notes }.map { Triple(it.tickOn, it.tickOff, it.key) }
                assertEquals(expected, notes, "Notes in ${format.displayName} output")
            }
        }

    private suspend fun ExportResult.singleProjectFile(): File {
        if (!fileName.endsWith(".zip")) return blob.toFile(fileName)
        val zip = JsZip().loadAsync(blob).await()
        val entry = js("Object").keys(zip.asDynamic().files).unsafeCast<Array<String>>().single()
        val contents = checkNotNull(zip.file(entry)).async("blob").await() as Blob
        return contents.toFile(entry)
    }

    private suspend fun vocaloid7Fixture() =
        zippedFile(
            "compatibility.vprx",
            "Project/sequence.json",
            """
            {"tracks":[{"type":2,"name":"Voice","parts":[{"pos":0,"notes":[
              {"pos":0,"duration":240,"number":60,"lyric":"a","directPitches":[
                {"pos":0,"value":0},{"pos":120,"value":35},{"pos":200,"value":"ZeroPitch"}
              ]},
              {"pos":240,"duration":240,"number":62,"lyric":"i","directPitches":[
                {"pos":0,"value":0},{"pos":100,"value":-25},{"pos":200,"value":"ZeroPitch"}
              ]}
            ]}]}]}
            """.trimIndent(),
        )
}
