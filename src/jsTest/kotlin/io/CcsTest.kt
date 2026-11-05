package io

import core.io.Ccs
import core.model.ImportParams
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.w3c.files.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(DelicateCoroutinesApi::class)
class CcsTest {
    private fun ccsContent(firstNoteClock: Int) =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <Scenario>
        <Sequence Id="">
        <Scene Id="">
        <Units>
        <Unit Category="SingerSong" Group="g1">
        <Song>
        <Tempo><Sound Clock="0" Tempo="120" /></Tempo>
        <Beat><Time BeatType="4" Beats="4" Clock="0" /></Beat>
        <Score>
        <Note Clock="$firstNoteClock" Duration="960" Lyric="a" PitchOctave="4" PitchStep="0" />
        <Note Clock="${firstNoteClock + 960}" Duration="960" Lyric="i" PitchOctave="4" PitchStep="2" />
        </Score>
        </Song>
        </Unit>
        </Units>
        <Groups><Group Category="SingerSong" Id="g1" Name="Track" /></Groups>
        </Scene>
        </Sequence>
        </Scenario>
        """.trimIndent()

    private suspend fun parseNoteStarts(firstNoteClock: Int): List<Long> {
        val file = File(arrayOf(ccsContent(firstNoteClock)), "test.ccs")
        val project = Ccs.parse(file, ImportParams())
        val notes = project.tracks.first().notes
        return notes.map { it.tickOn }
    }

    @Test
    fun testParseWithLeadingMeasure() =
        GlobalScope.promise {
            assertEquals(listOf(0L, 480L), parseNoteStarts(3840))
        }

    @Test
    fun testParseWithNotesInFirstMeasure() =
        GlobalScope.promise {
            assertEquals(listOf(0L, 480L), parseNoteStarts(0))
        }
}
