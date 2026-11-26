package io

import core.external.JsZip
import core.model.ExportResult
import core.model.FeatureConfig
import core.model.Format
import core.model.ImportParams
import core.model.Pitch
import core.model.Tempo
import core.model.TimeSignature
import helpers.toFile
import helpers.zippedFile
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.w3c.files.Blob
import org.w3c.files.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(DelicateCoroutinesApi::class)
class VprxConversionTest {
    private val params = ImportParams()
    private val pitchFeatures = listOf(FeatureConfig.ConvertPitch)

    @Test
    fun exportedVprxPreservesNotesTempoMeterAndSavedPitch() =
        GlobalScope.promise {
            val original = Format.Vprx.parser(listOf(vocaloid7Fixture()), params)
            val source =
                original.copy(
                    tempos = listOf(Tempo(0, 174.25), Tempo(240, 120.0)),
                    timeSignatures = listOf(TimeSignature(0, 4, 4), TimeSignature(1, 3, 4)),
                )
            val generated = Format.Vprx.generator(source, pitchFeatures)
            val readBack = Format.Vprx.parser(listOf(generated.singleProjectFile()), params)
            assertEquals(source.tempos, readBack.tempos)
            assertEquals(source.timeSignatures, readBack.timeSignatures)
            assertEquals(source.tracks, readBack.tracks)

            val notesOnly = Format.Vprx.generator(source, emptyList())
            val notesOnlyTrack =
                Format.Vprx
                    .parser(listOf(notesOnly.singleProjectFile()), params)
                    .tracks
                    .single()
            assertEquals(source.tracks.single().notes, notesOnlyTrack.notes)
            assertNull(notesOnlyTrack.pitch)
        }

    @Test
    fun relativePitchHoldsAndReturnsToEachNoteKey() =
        GlobalScope.promise {
            val original = Format.Vprx.parser(listOf(vocaloid7Fixture()), params)
            val track = original.tracks.single()
            val source =
                original.copy(
                    format = Format.UfData,
                    tracks = listOf(track.copy(pitch = Pitch(listOf(0L to 1.0, 300L to 0.0, 400L to null), false))),
                )
            val generated = Format.Vprx.generator(source, pitchFeatures)
            val readBack = Format.Vprx.parser(listOf(generated.singleProjectFile()), params)
            val pitch = checkNotNull(readBack.tracks.single().pitch).data.toMap()
            assertEquals(61.0, pitch[239L])
            assertEquals(63.0, pitch[240L])
            assertEquals(63.0, pitch[299L])
            assertEquals(62.0, pitch[300L])
            assertEquals(62.0, pitch[399L])
            assertTrue(400L in pitch)
            assertNull(pitch[400L])
        }

    @Test
    fun preservesNativePartBoundariesAndStrokeEndsOutsideTheNote() =
        GlobalScope.promise {
            val file =
                zippedFile(
                    "parts.vprx",
                    "Project/sequence.json",
                    """
                    {"tracks":[{"type":2,"name":"Voice","parts":[
                      {"pos":240,"controllers":[{"name":"breathiness","events":[]}],"notes":[
                        {"pos":0,"duration":120,"number":60,"lyric":"a","isMuted":true,"directPitches":[
                          {"pos":0,"value":50},{"pos":120,"value":"ZeroPitch"}
                        ]}
                      ]},
                      {"pos":720,"duration":480,"notes":[{"pos":0,"duration":120,"number":62,"lyric":"i"}]}
                    ]}]}
                    """.trimIndent(),
                )
            val original = Format.Vprx.parser(listOf(file), params)
            val track = original.tracks.single()
            val source = original.copy(tracks = listOf(track.copy(notes = track.notes.map { it.copy(lyric = "u") })))
            val output = Format.Vprx.generator(source, pitchFeatures)
            val zip = JsZip().loadAsync(output.blob).await()
            val sequence = checkNotNull(zip.file("Project/sequence.json")).async("string").await() as String
            val parts =
                Json
                    .parseToJsonElement(sequence)
                    .jsonObject
                    .getValue("tracks")
                    .jsonArray
                    .single()
                    .jsonObject
                    .getValue("parts")
                    .jsonArray
                    .map { it.jsonObject }
            assertEquals(listOf(240L, 720L), parts.map { it.getValue("pos").jsonPrimitive.long })
            assertEquals(listOf(120L, 480L), parts.map { it.getValue("duration").jsonPrimitive.long })
            assertNull(parts.first()["controllers"])
            val note =
                parts
                    .first()
                    .getValue("notes")
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("u", note.getValue("lyric").jsonPrimitive.content)
            assertEquals(false, note.getValue("isMuted").jsonPrimitive.boolean)
            val reset =
                note
                    .getValue("directPitches")
                    .jsonArray
                    .last()
                    .jsonObject
            assertEquals(120L, reset.getValue("pos").jsonPrimitive.long)
            assertEquals("ZeroPitch", reset.getValue("value").jsonPrimitive.content)
        }

    @Test
    fun exportsFromTheSharedProjectWhenTheOriginalFileCannotBeRead() =
        GlobalScope.promise {
            val original = Format.Vprx.parser(listOf(vocaloid7Fixture()), params)
            val source = original.copy(inputFiles = listOf(zippedFile("unavailable.vprx", "unrelated.json", "{}")))
            val output = Format.Vprx.generator(source, pitchFeatures)
            val readBack = Format.Vprx.parser(listOf(output.singleProjectFile()), params)
            assertEquals(source.tracks, readBack.tracks)
        }

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
