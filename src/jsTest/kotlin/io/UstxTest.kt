package io

import core.io.Ustx
import core.model.ImportParams
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
            assertEquals("あ", project.tracks.first().notes.first().lyric)
        }
}
