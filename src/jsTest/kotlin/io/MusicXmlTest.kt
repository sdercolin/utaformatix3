package io

import core.io.MusicXml
import core.model.ImportParams
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.w3c.files.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(DelicateCoroutinesApi::class)
class MusicXmlTest {
    private fun note(
        step: String,
        ties: String,
    ) = "<note><pitch><step>$step</step><octave>4</octave></pitch><duration>1</duration>$ties" +
        "<lyric><text>a</text></lyric></note>"

    @Test
    fun testParseNoteTiedAcrossThreeNotes() =
        GlobalScope.promise {
            val content =
                listOf(
                    """<?xml version="1.0" encoding="UTF-8"?>""",
                    """<score-partwise version="3.1">""",
                    """<part-list><score-part id="P1"><part-name>P1</part-name></score-part></part-list>""",
                    """<part id="P1"><measure number="1">""",
                    """<attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes>""",
                    note("C", """<tie type="start"/>"""),
                    note("C", """<tie type="stop"/><tie type="start"/>"""),
                    note("C", """<tie type="stop"/>"""),
                    note("D", ""),
                    "</measure></part></score-partwise>",
                ).joinToString("\n")
            val file = File(arrayOf(content), "test.musicxml")
            val project = MusicXml.parse(file, ImportParams())
            val notes = project.tracks.first().notes
            assertEquals(listOf(0L to 1440L, 1440L to 1920L), notes.map { it.tickOn to it.tickOff })
        }
}
