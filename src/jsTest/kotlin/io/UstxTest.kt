package io

import core.io.Svp
import core.io.Ustx
import core.model.ImportParams
import core.process.phonemes.PhonemesMappingRequest
import core.process.phonemes.mapPhonemes
import helpers.readText
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.w3c.files.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(DelicateCoroutinesApi::class)
class UstxTest {
    private fun ustxContent(version: String) =
        """
        name: New Project
        comment: ''
        output_dir: Vocal
        cache_dir: UCache
        ustx_version: $version
        bpm: 120
        beat_per_bar: 4
        beat_unit: 4
        resolution: 480
        expressions: {}
        tracks:
        - phonemizer: OpenUtau.Api.DefaultPhonemizer
          mute: false
          solo: false
          volume: 0
          track_name: Track 1
        voice_parts:
        - name: New Part
          comment: ''
          track_no: 0
          position: 0
          notes:
          - position: 0
            duration: 480
            tone: 60
            lyric: あ
            pitch:
              data:
              - {x: -40, y: 0, shape: io}
              snap_first: true
            vibrato: {length: 0, period: 175, depth: 25, in: 10, out: 10, shift: 0, drift: 0}
        """.trimIndent()

    private fun parse(version: String) =
        GlobalScope.promise {
            val file = File(arrayOf(ustxContent(version)), "test.ustx")
            val project = Ustx.parse(file, ImportParams())
            assertEquals(1, project.tracks.size)
            val notes = project.tracks.first().notes
            assertEquals(1, notes.size)
            assertEquals("あ", notes.first().lyric)
        }

    // older OpenUtau writes the version as a number
    @Test
    fun testParseNumberVersion() = parse("0.6")

    // newer OpenUtau writes it as a string (issue #191)
    @Test
    fun testParseStringVersion() = parse("'0.9.1'")

    @Test
    fun testParseWithoutOptionalFields() =
        GlobalScope.promise {
            val content =
                """
                name: New Project
                ustx_version: '0.9.1'
                tracks:
                - track_name: Track 1
                voice_parts:
                - name: New Part
                  track_no: 0
                  position: 0
                  notes:
                  - position: 0
                    duration: 480
                    tone: 60
                    lyric: あ
                    pitch:
                      data:
                      - {x: -40, y: 0, shape: io}
                      snap_first: true
                    vibrato: {length: 0, period: 175, depth: 25, in: 10, out: 10, shift: 0, drift: 0}
                """.trimIndent()
            val file = File(arrayOf(content), "test.ustx")
            val project = Ustx.parse(file, ImportParams())
            assertEquals(1, project.tracks.size)
            val notes = project.tracks.first().notes
            assertEquals("あ", notes.first().lyric)
        }

    private fun svpNote(
        onset: Long,
        lyrics: String,
        phonemes: String,
    ) = """{"onset": $onset, "duration": 705600000, "lyrics": "$lyrics", "phonemes": "$phonemes", "pitch": 60}"""

    private val svpWithPhoneticHints =
        """
        {
          "version": 153,
          "time": {
            "meter": [{"index": 0, "numerator": 4, "denominator": 4}],
            "tempo": [{"position": 0, "bpm": 120.0}]
          },
          "library": [],
          "tracks": [{
            "name": "Track",
            "dispOrder": 0,
            "mainGroup": {
              "name": "main",
              "uuid": "00000000-0000-0000-0000-000000000000",
              "parameters": {"pitchDelta": {"mode": "cubic", "points": []}},
              "notes": [
                ${svpNote(0, "butter", "b ah t er")},
                ${svpNote(705600000, "+", "")},
                ${svpNote(1411200000, ".b ah t er", "")},
                ${svpNote(2116800000, "+", "")},
                ${svpNote(2822400000, "la", "")}
              ]
            },
            "mainRef": {"groupID": "00000000-0000-0000-0000-000000000000", "blickOffset": 0, "pitchOffset": 0},
            "groups": []
          }]
        }
        """.trimIndent()

    private suspend fun exportedLyricsFromSvp(request: PhonemesMappingRequest?): List<String> {
        val project = Svp.parse(File(arrayOf(svpWithPhoneticHints), "test.svp"), ImportParams())
        val text = Ustx.generate(project.mapPhonemes(request), listOf()).blob.readText()
        return Regex("lyric: (.*)").findAll(text).map { it.groupValues[1].trim('\'', '"') }.toList()
    }

    @Test
    fun testGenerateSynthVPhoneticHintsWithPhonemesConversion() =
        GlobalScope.promise {
            assertEquals(
                listOf("butter [b ah t er]", "+", "[b ah t er]", "+", "la"),
                exportedLyricsFromSvp(PhonemesMappingRequest("")),
            )
        }

    @Test
    fun testGenerateSynthVPhoneticHintsWithoutPhonemesConversion() =
        GlobalScope.promise {
            assertEquals(
                listOf("butter", "+", ".b ah t er", "+", "la"),
                exportedLyricsFromSvp(null),
            )
        }
}
