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
import kotlin.test.assertTrue

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

    private val svpWithZeroVibratoEase =
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
            "dispColor": "ff7db235",
            "dispOrder": 0,
            "renderEnabled": true,
            "mixer": {"gainDecibel": 0.0, "pan": 0.0, "mute": false, "solo": false, "display": true},
            "mainGroup": {
              "name": "main",
              "uuid": "00000000-0000-0000-0000-000000000000",
              "parameters": {
                "pitchDelta": {"mode": "cubic", "points": [0, 0.0, 1411200000, 0.0]},
                "vibratoEnv": {"mode": "cubic", "points": []}
              },
              "notes": [{
                "onset": 0,
                "duration": 1411200000,
                "lyrics": "la",
                "phonemes": "",
                "pitch": 60,
                "detune": 0,
                "attributes": {"tF0VbrStart": 0.0, "tF0VbrLeft": 0.0, "tF0VbrRight": 0.0}
              }]
            },
            "mainRef": {
              "groupID": "00000000-0000-0000-0000-000000000000",
              "blickOffset": 0,
              "pitchOffset": 0,
              "isInstrumental": false
            },
            "groups": []
          }]
        }
        """.trimIndent()

    @Test
    fun testZeroVibratoEaseLengthDoesNotProduceNaN() =
        GlobalScope.promise {
            val file = File(arrayOf(svpWithZeroVibratoEase), "test.svp")
            val project = Svp.parse(file, ImportParams())
            val pitch = project.tracks.first().pitch
            assertTrue(pitch != null && pitch.data.isNotEmpty(), "empty: $pitch")
            assertTrue(pitch.data.none { it.second?.isNaN() == true }, "nan: ${pitch.data.take(5)}")
        }
}
