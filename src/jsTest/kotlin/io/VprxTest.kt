package io

import core.model.FeatureConfig
import core.model.Format
import core.model.ImportParams
import helpers.readText
import helpers.toFile
import helpers.zippedFile
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(DelicateCoroutinesApi::class)
class VprxTest {
    @Test
    fun importsVocaloid7NotesAndDirectPitch() =
        GlobalScope.promise {
            val file =
                zippedFile(
                    "sample.vprx",
                    "Project/sequence.json",
                    """
                    {
                      "title": "Test project",
                      "masterTrack": {
                        "tempo": {"global": {"isEnabled": false, "value": 12000}, "events": [{"pos": 0, "value": 17400}]},
                        "timeSig": {"events": [{"bar": 0, "numer": 3, "denom": 4}]}
                      },
                      "tracks": [
                        {"type": 1, "name": "Audio", "parts": [{"pos": 0, "duration": null, "wav": {"name": "test.wav"}}]},
                        {"type": 2, "name": "Voice", "parts": [{"pos": 480, "notes": [{
                          "pos": 120, "duration": 240, "number": 60, "lyric": "la", "phoneme": "l a",
                          "directPitches": [{"pos": -1, "value": 10.0}, {"pos": 12, "value": 25.5},
                                            {"pos": 36, "value": "ZeroPitch"}]
                        }]}]}
                      ]
                    }
                    """.trimIndent(),
                )
            val format = Format.importable.first { it.match(listOf(file)) }
            val project = format.parser(listOf(file), ImportParams())
            val track = project.tracks.single()
            val note = track.notes.single()

            assertEquals(Format.Vprx, project.format)
            assertFalse(format in Format.exportable)
            assertEquals("Test project", project.name)
            assertEquals(174.0, project.tempos.single().bpm)
            assertEquals(3, project.timeSignatures.single().numerator)
            assertEquals("Voice", track.name)
            assertEquals(0, track.id)
            assertEquals(600L, note.tickOn)
            assertEquals(840L, note.tickOff)
            assertEquals(60, note.key)
            assertEquals("la", note.lyric)
            assertEquals("l a", note.phoneme)
            assertEquals(listOf(599L to 60.1, 612L to 60.255, 636L to null), track.pitch?.data)
            assertTrue(track.pitch?.isAbsolute == true)

            val simple = format.parser(listOf(file), ImportParams(simpleImport = true))
            assertEquals(track.notes, simple.tracks.single().notes)
            assertNull(simple.tracks.single().pitch)
        }

    @Test
    fun mergesNoteRelativeCopiesOfTheSamePitchTransition() =
        GlobalScope.promise {
            val file =
                zippedFile(
                    "transitions.vprx",
                    "Project/sequence.json",
                    """
                    {"tracks":[
                      {"type":2,"parts":[{"pos":0,"notes":[
                        {"pos":0,"duration":120,"number":65,"directPitches":[
                          {"pos":110,"value":699.5},{"pos":128,"value":625}
                        ]},
                        {"pos":120,"duration":120,"number":72,"directPitches":[
                          {"pos":-10,"value":-0.5},{"pos":8,"value":4},{"pos":25,"value":"ZeroPitch"}
                        ]}
                      ]}]}
                    ]}
                    """.trimIndent(),
                )
            val project = Format.Vprx.parser(listOf(file), ImportParams())
            val pitch = checkNotNull(project.tracks.single().pitch)
            assertEquals(listOf(110L to 71.995, 128L to 72.04, 145L to null), pitch.data)

            val result = Format.Svp.generator(project, listOf(FeatureConfig.ConvertPitch))
            val json = result.blob.readText()
            val track = JSON.parse<dynamic>(json).tracks[0]
            val data = track.mainGroup.parameters.pitchDelta.points as Array<Double>
            val offsets =
                data
                    .asList()
                    .chunked(2)
                    .associate { (tick, cents) -> (tick / 1470000).toLong() to cents }
            val before = 65 + offsets.getValue(119L) / 100
            val after = 72 + offsets.getValue(120L) / 100
            assertEquals(72.0175, before, absoluteTolerance = 0.00001)
            assertEquals(72.02, after, absoluteTolerance = 0.00001)
        }

    @Test
    fun importsGlobalTempoAndAStandardTrackPitchBend() =
        GlobalScope.promise {
            val file =
                zippedFile(
                    "standard.vprx",
                    "Project/sequence.json",
                    """
                    {
                      "masterTrack":{"tempo":{"global":{"isEnabled":true,"value":13750},"events":[{"pos":0,"value":9000}]}},
                      "tracks":[{"type":0,"parts":[{"pos":240,"notes":[{"pos":0,"duration":120,"number":60}],"controllers":[
                        {"name":"pitchBend","events":[{"pos":24,"value":8191}]},
                        {"name":"pitchBendSens","events":[{"pos":24,"value":4},{"pos":48,"value":12}]}
                      ]}]}]
                    }
                    """.trimIndent(),
                )
            val project = Format.Vprx.parser(listOf(file), ImportParams())

            assertEquals(137.5, project.tempos.single().bpm)
            assertEquals(0L, project.tempos.single().tickPosition)
            val pitch = checkNotNull(project.tracks.single().pitch)
            assertFalse(pitch.isAbsolute)
            assertEquals(listOf(264L to 4.0), pitch.data)
        }

    @Test
    fun anIsolatedBendUsesTheVprxPitchPath() =
        GlobalScope.promise {
            val file =
                zippedFile(
                    "bend.vprx",
                    "Project/sequence.json",
                    """
                    {"tracks":[{"type":0,"parts":[{"pos":0,"notes":[{"pos":0,"duration":120,"number":60}],"controllers":[
                      {"name":"pitchBend","events":[{"pos":24,"value":8191}]}
                    ]}]}]}
                    """.trimIndent(),
                )
            val vprx = Format.Vprx.parser(listOf(file), ImportParams())
            val vpr = Format.Vpr.parser(listOf(file.toFile("bend.vpr")), ImportParams())
            val pitch = checkNotNull(vprx.tracks.single().pitch)

            assertEquals(listOf(24L to 2.0), pitch.data)
            assertNull(vpr.tracks.single().pitch)
        }
}
