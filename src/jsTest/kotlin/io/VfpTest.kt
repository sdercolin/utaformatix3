package io

import core.external.JsZip
import core.model.ExportNotification
import core.model.FeatureConfig
import core.model.Format
import core.model.ImportParams
import core.model.Note
import core.model.Pitch
import core.model.Project
import core.model.Tempo
import core.model.TimeSignature
import core.model.Track
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.files.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(DelicateCoroutinesApi::class)
class VfpTest {
    @Test
    fun testRoundTrip() =
        GlobalScope.promise {
            val original = buildProject()
            val exported = core.io.Vfp.generate(original, listOf(FeatureConfig.ConvertPitch))
            assertEquals("Round Trip.vfp", exported.fileName)

            val reimported = core.io.Vfp.parse(File(arrayOf(exported.blob), exported.fileName), ImportParams())
            assertEquals(Format.Vfp, reimported.format)
            assertEquals("Round Trip", reimported.name)
            assertEquals(135.0, reimported.tempos.single().bpm)
            assertEquals(3, reimported.timeSignatures.single().numerator)
            assertEquals(4, reimported.timeSignatures.single().denominator)
            assertEquals(2, reimported.tracks.size)

            original.tracks.zip(reimported.tracks).forEach { (expected, actual) ->
                assertEquals(expected.name, actual.name)
                assertEquals(
                    expected.notes.map { Triple(it.tickOn, it.tickOff, it.key) },
                    actual.notes.map { Triple(it.tickOn, it.tickOff, it.key) },
                )
                assertEquals(expected.notes.map { it.lyric }, actual.notes.map { it.lyric })
            }
            assertEquals(
                original.tracks
                    .first()
                    .pitch
                    ?.data,
                reimported.tracks
                    .first()
                    .pitch
                    ?.data,
            )
            assertTrue(
                reimported.tracks
                    .first()
                    .pitch!!
                    .isAbsolute,
            )
            assertNull(reimported.tracks.last().pitch)
        }

    @Test
    fun testGeneratedContent() =
        GlobalScope.promise {
            val content =
                core.io.Vfp
                    .generate(buildProject(), listOf())
                    .readProjectJson()

            assertEquals("Round Trip", content["projectName"]!!.jsonPrimitive.content)
            assertEquals(135.0, content["tempo"]!!.jsonPrimitive.double)
            assertEquals(listOf(3, 4), content["timeSignature"]!!.jsonArray.map { it.jsonPrimitive.int })
            // The template's own format version is kept.
            assertNotNull(content["version"]!!.jsonPrimitive.contentOrNull)

            val trackKeys = content["trackOrder"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertEquals(2, trackKeys.size)
            val track = content["trackBank"]!!.jsonObject[trackKeys.first()]!!.jsonObject
            assertEquals("vocal", track["type"]!!.jsonPrimitive.content)
            assertEquals("Lead", track["name"]!!.jsonPrimitive.content)

            // Each track carries its own copy of the template's device chain, under its own keys.
            val deviceKeys = track["deviceOrder"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertTrue(deviceKeys.isNotEmpty())
            assertEquals(deviceKeys, track["deviceBank"]!!.jsonObject.keys.toList())
            assertTrue(deviceKeys.all { it.startsWith(trackKeys.first()) })

            val clip = track.bank("clip").single()
            assertEquals("vocal", clip["type"]!!.jsonPrimitive.content)
            assertEquals(0.0, clip["startQuarter"]!!.jsonPrimitive.double)
            assertEquals(0.0, clip["offsetQuarter"]!!.jsonPrimitive.double)
            assertEquals("Normal.A", clip["metadata"]!!.jsonObject["style"]!!.jsonPrimitive.content)

            val notes = clip.notes()
            assertEquals(3, notes.size)
            assertEquals(0, notes[0]["ticks"]!!.jsonPrimitive.int)
            assertEquals(480, notes[0]["durationTicks"]!!.jsonPrimitive.int)
            assertEquals(60, notes[0]["midi"]!!.jsonPrimitive.int)
            assertEquals("la", notes[0]["name"]!!.jsonPrimitive.content)
            assertEquals("la", notes[0]["syllable"]!!.jsonPrimitive.content)
            // 480 ticks at 135 bpm is one quarter note, 60 / 135 seconds.
            assertEquals(60.0 / 135.0, notes[0]["duration"]!!.jsonPrimitive.double)
            assertEquals(60.0 / 135.0, notes[1]["time"]!!.jsonPrimitive.double)

            // A romanized lyric that already parses is used as the pronunciation as it is.
            assertEquals("kkyeo", notes[1]["syllable"]!!.jsonPrimitive.content)
            // Kana is converted, and the lyric itself stays in the name.
            assertEquals("あ", notes[2]["name"]!!.jsonPrimitive.content)
            assertEquals("a", notes[2]["syllable"]!!.jsonPrimitive.content)
        }

    @Test
    fun testPitchIsOnlyExportedWithTheFeature() =
        GlobalScope.promise {
            val withoutPitch =
                core.io.Vfp
                    .generate(buildProject(), listOf())
                    .readProjectJson()
            assertTrue(withoutPitch.firstClip().pitchPoints().isEmpty())

            val content =
                core.io.Vfp
                    .generate(buildProject(), listOf(FeatureConfig.ConvertPitch))
                    .readProjectJson()
            val points = content.firstClip().pitchPoints()
            assertEquals(3, points.size)
            assertEquals(0.0, points[0]["quarter"]!!.jsonPrimitive.double)
            assertEquals(60.0, points[0]["pitch"]!!.jsonPrimitive.double)
            assertEquals(0.5, points[1]["quarter"]!!.jsonPrimitive.double)
            // The first point of a segment starts it, the rest continue it.
            assertEquals(false, points[0]["connected"]!!.jsonPrimitive.boolean)
            assertTrue(points.drop(1).all { it["connected"]!!.jsonPrimitive.boolean })
        }

    @Test
    fun testClipsAreSplitAtRests() =
        GlobalScope.promise {
            // 3/4 here, so a bar is 1440 ticks: the gap after the second note exceeds one.
            val notes =
                listOf(
                    Note(id = 0, key = 60, lyric = "か", tickOn = 480, tickOff = 960),
                    Note(id = 1, key = 62, lyric = "き", tickOn = 960, tickOff = 1440),
                    Note(id = 2, key = 64, lyric = "く", tickOn = 4320, tickOff = 4800),
                )
            val clips = clipsOf(notes)
            assertEquals(2, clips.size)

            // Each clip starts on the bar line at or before its first note, and ends on one after it.
            assertEquals(0.0, clips[0]["startQuarter"]!!.jsonPrimitive.double)
            assertEquals(3.0, clips[0]["length"]!!.jsonPrimitive.double)
            assertEquals(9.0, clips[1]["startQuarter"]!!.jsonPrimitive.double)
            assertEquals(3.0, clips[1]["length"]!!.jsonPrimitive.double)

            // Note positions are relative to the clip they ended up in.
            assertEquals(listOf(480, 960), clips[0].notes().map { it["ticks"]!!.jsonPrimitive.int })
            assertEquals(listOf(0), clips[1].notes().map { it["ticks"]!!.jsonPrimitive.int })
            // And so is the clip's own end marker.
            assertEquals(1440, clips[0]["endOfTrackTicks"]!!.jsonPrimitive.int)
            assertEquals(480, clips[1]["endOfTrackTicks"]!!.jsonPrimitive.int)
            // The editor labels a clip with the lyrics it holds.
            assertEquals("か き", clips[0]["name"]!!.jsonPrimitive.content)
        }

    @Test
    fun testClipsAreCappedAtTheBarLimit() =
        GlobalScope.promise {
            // 3/4, so a bar is 3 quarters and MAX_CLIP_BARS, being ten of them, is 30 quarters.
            val notes =
                (0 until 40).map { index ->
                    Note(id = index, key = 60, lyric = "ら", tickOn = index * 480L, tickOff = (index + 1) * 480L)
                }
            val clips = clipsOf(notes)
            assertTrue(clips.size > 1)
            assertTrue(clips.all { it["length"]!!.jsonPrimitive.double <= 30.0 })
            // Splitting a phrase must not lose or duplicate a note.
            assertEquals(notes.size, clips.sumOf { it.notes().size })
        }

    @Test
    fun testClipsNeverOverlap() =
        GlobalScope.promise {
            // Contiguous notes running well past the cap: the cuts fall where no bar line is free.
            val notes =
                (0 until 60).map { index ->
                    Note(id = index, key = 60, lyric = "ら", tickOn = index * 500L, tickOff = (index + 1) * 500L)
                }
            val clips = clipsOf(notes)
            assertTrue(clips.size > 2)
            val spans =
                clips.map {
                    val start = it["startQuarter"]!!.jsonPrimitive.double
                    start to start + it["length"]!!.jsonPrimitive.double
                }
            spans.zipWithNext().forEach { (earlier, later) ->
                assertTrue(earlier.second <= later.first, "clip $earlier overlaps $later")
            }
            // No note may be left hanging outside the clip that holds it.
            clips.forEach { clip ->
                val lengthTicks = clip["length"]!!.jsonPrimitive.double * 480
                clip.notes().forEach { note ->
                    val tick = note["ticks"]!!.jsonPrimitive.int
                    assertTrue(tick >= 0)
                    assertTrue(tick + note["durationTicks"]!!.jsonPrimitive.int <= lengthTicks)
                }
            }
            assertEquals(notes.size, clips.sumOf { it.notes().size })
        }

    @Test
    fun testCappedClipIsCutAtTheWidestRest() =
        GlobalScope.promise {
            // A run with no rest wide enough to end a phrase, but one wide enough to prefer.
            val starts = (0..10).map { it * 480L } + (0 until 19).map { 6240L + it * 480L }
            val notes =
                starts.mapIndexed { index, tickOn ->
                    Note(id = index, key = 60, lyric = "ら", tickOn = tickOn, tickOff = tickOn + 480)
                }
            val clips = clipsOf(notes)
            // The cut lands after the eleventh note, where the rest is, not at the cap itself.
            assertEquals(11, clips.first().notes().size)
            assertEquals(notes.size, clips.sumOf { it.notes().size })
        }

    @Test
    fun testKoreanSyllables() =
        GlobalScope.promise {
            // Onset, nucleus and coda, each in the editor's own romanization.
            assertEquals(listOf("ga", "na", "da"), syllablesOf("가나다"))
            assertEquals(listOf("han", "geul"), syllablesOf("한글"))
            // A silent onset leaves nothing behind.
            assertEquals(listOf("a", "i", "o"), syllablesOf("아이오"))
            // ㄹ opens a syllable as r and closes one as l.
            assertEquals(listOf("ra", "dal"), syllablesOf("라달"))
            // Except after a coda ㄹ, where it is l as well.
            assertEquals(listOf("sal", "lang"), syllablesOf("살랑"))
            // Codas are levelled to the seven sounds a syllable can end on.
            assertEquals(listOf("bap", "bat", "bak"), syllablesOf("밥밭밖"))
            assertEquals(listOf("kkot", "nat", "it"), syllablesOf("꽃낫있"))
            // Compound vowels keep the glide the editor writes them with.
            assertEquals(listOf("gyah", "wae", "eui"), syllablesOf("겨왜의"))
            // The lyric itself is still what the note is named.
            assertEquals(listOf("사", "랑"), namesOf("사랑"))
        }

    @Test
    fun testKoreanTablesAreComplete() =
        GlobalScope.promise {
            // Every syllable in the block has to come out as something the editor can parse.
            val lyrics = (0xAC00..0xD7A3 step 97).map { it.toChar().toString() }
            val syllables = syllablesOf(lyrics)
            assertEquals(lyrics.size, syllables.size)
            assertTrue(syllables.none { it == "la" }, "unparsed: ${lyrics.zip(syllables).filter { it.second == "la" }}")
        }

    @Test
    fun testMoraicNWrittenAsRomaji() =
        GlobalScope.promise {
            // A lyric left as romaji still has to be spelled by what follows it.
            assertEquals(listOf("ng", "ka"), syllablesOf(listOf("n", "か")))
            assertEquals(listOf("m", "pa"), syllablesOf(listOf("m", "ぱ")))
            // A slur may name its own vowel instead of borrowing the one before it.
            assertEquals(listOf("ko", "a"), syllablesOf(listOf("こ", "-a")))
            assertEquals(listOf("-", "-"), namesOf(listOf("ー", "-a")))
        }

    @Test
    fun testOnlyFirstTempoAndTimeSignatureSurvive() =
        GlobalScope.promise {
            val project =
                buildProject().copy(
                    tempos = listOf(Tempo(0, 135.0), Tempo(960, 90.0)),
                    timeSignatures = listOf(TimeSignature(0, 3, 4), TimeSignature(4, 4, 4)),
                )
            val exported = core.io.Vfp.generate(project, listOf())
            assertTrue(exported.notifications.contains(ExportNotification.TempoIgnored))
            assertTrue(exported.notifications.contains(ExportNotification.TimeSignatureIgnored))

            val content = exported.readProjectJson()
            assertEquals(135.0, content["tempo"]!!.jsonPrimitive.double)
            assertEquals(listOf(3, 4), content["timeSignature"]!!.jsonArray.map { it.jsonPrimitive.int })
        }

    @Test
    fun testJapaneseSyllables() =
        GlobalScope.promise {
            // The vowel u becomes eu, and katakana is read like its hiragana.
            assertEquals(
                listOf("a", "ka", "shi", "tseu", "keu", "eu", "kyeu"),
                // A contracted mora such as キュ is one note's lyric, not two.
                syllablesOf(listOf("あ", "か", "し", "つ", "く", "う", "キュ")),
            )
            // A small tsu takes over the consonant that opens the next mora.
            assertEquals(listOf("t", "ta"), syllablesOf("った"))
            assertEquals(listOf("k", "ka"), syllablesOf("っか"))
            assertEquals(listOf("s", "sa"), syllablesOf("っさ"))
            assertEquals(listOf("ch", "cha"), syllablesOf(listOf("っ", "ちゃ")))
            // The moraic n is spelled by what follows it.
            assertEquals(listOf("ng", "ka"), syllablesOf("んか"))
            assertEquals(listOf("n", "ta"), syllablesOf("んた"))
            assertEquals(listOf("m", "pa"), syllablesOf("んぱ"))
            assertEquals(listOf("n", "reu"), syllablesOf("んる"))
            // Nothing follows, so it falls back to the velar spelling.
            assertEquals(listOf("ka", "ng"), syllablesOf("かん"))
            // A long vowel mark is a slur carrying over the vowel before it.
            assertEquals(listOf("ka", "a"), syllablesOf("かー"))
            assertEquals(listOf("kyeu", "eu"), syllablesOf(listOf("キュ", "ー")))
            // And it is named the way the editor names a slur, not with the kana.
            assertEquals(listOf("か", "-"), namesOf("かー"))
            // A lyric that is neither kana nor a parseable syllable gets the editor's default.
            assertEquals(listOf("la", "la"), syllablesOf(listOf("漢", "?")))
        }

    private suspend fun syllablesOf(lyrics: String) = syllablesOf(lyrics.map { it.toString() })

    private suspend fun syllablesOf(lyrics: List<String>) =
        notesOf(lyrics).map { it["syllable"]!!.jsonPrimitive.content }

    private suspend fun namesOf(lyrics: String) = namesOf(lyrics.map { it.toString() })

    private suspend fun namesOf(lyrics: List<String>) = notesOf(lyrics).map { it["name"]!!.jsonPrimitive.content }

    private suspend fun notesOf(lyrics: List<String>): List<JsonObject> {
        val notes =
            lyrics.mapIndexed { index, lyric ->
                Note(
                    id = index,
                    key = 60,
                    lyric = lyric,
                    tickOn = index * 480L,
                    tickOff = (index + 1) * 480L,
                )
            }
        // Long runs of notes are split across clips, so read them back in clip order.
        return clipsOf(notes).flatMap { it.bank("note") }
    }

    private suspend fun clipsOf(notes: List<Note>): List<JsonObject> {
        val project = buildProject().copy(tracks = listOf(Track(id = 0, name = "Lead", notes = notes)))
        return core.io.Vfp
            .generate(project, listOf())
            .readProjectJson()
            .bank("track")
            .single()
            .bank("clip")
    }

    private fun buildProject(): Project {
        val notes =
            listOf(
                Note(id = 0, key = 60, lyric = "la", tickOn = 0, tickOff = 480),
                Note(id = 1, key = 62, lyric = "kkyeo", tickOn = 480, tickOff = 960),
                Note(id = 2, key = 64, lyric = "あ", tickOn = 1440, tickOff = 1920),
            )
        return Project(
            format = Format.Vfp,
            inputFiles = listOf(),
            name = "Round Trip",
            tracks =
                listOf(
                    Track(
                        id = 0,
                        name = "Lead",
                        notes = notes,
                        pitch = Pitch(data = listOf(0L to 60.0, 240L to 61.5, 480L to 62.0), isAbsolute = true),
                    ),
                    Track(id = 1, name = "Harmony", notes = notes),
                ),
            timeSignatures = listOf(TimeSignature(0, 3, 4)),
            tempos = listOf(Tempo(0, 135.0)),
            measurePrefix = 0,
            importWarnings = listOf(),
        )
    }

    private suspend fun core.model.ExportResult.readProjectJson(): JsonObject {
        val zip = JsZip().loadAsync(blob).await()
        val text = assertNotNull(zip.file("project.json")).async("string").await() as String
        return Json.parseToJsonElement(text).jsonObject
    }

    /** Reads a `<name>Bank` in the order its `<name>Order` gives, the way the format stores both. */
    private fun JsonObject.bank(name: String) =
        this["${name}Order"]!!.jsonArray.map { key ->
            this["${name}Bank"]!!.jsonObject[key.jsonPrimitive.content]!!.jsonObject
        }

    private fun JsonObject.firstClip() = bank("track").first().bank("clip").single()

    private fun JsonObject.notes() = bank("note")

    private fun JsonObject.pitchPoints() = bank("pitchPoint")
}
