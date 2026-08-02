package process.pitch

import core.model.Note
import core.model.Pitch
import core.process.pitch.pitchToUtauMode1Track
import helpers.assertMatchesSnapshot
import kotlin.js.Date
import kotlin.math.sin
import kotlin.test.Test

class UtauMode1PitchConversionTest {
    private fun makeNotes(count: Int) =
        (0 until count).map {
            Note(
                id = it,
                key = 60 + it % 5,
                lyric = "あ",
                tickOn = it * 480L,
                tickOff = (it + 1) * 480L,
            )
        }

    private fun makePitch(totalTicks: Long) =
        Pitch(
            data = (0L until totalTicks step 5L).map { it to sin(it * 0.01) * 2 + 61 },
            isAbsolute = true,
        )

    @Test
    fun testPitchToUtauMode1Track() {
        val notes = makeNotes(4)
        val pitch = makePitch(4 * 480L)
        val result = pitchToUtauMode1Track(pitch, notes)
        val text =
            result!!.notes.joinToString("\n") { notePitch ->
                notePitch?.pitchPoints?.joinToString(",") ?: "null"
            }
        assertMatchesSnapshot("utau_mode1_pitch_export.txt", text)
    }

    @Test
    fun benchmarkPitchToUtauMode1Track() {
        val noteCount = 500
        val notes = makeNotes(noteCount)
        val pitch = makePitch(noteCount * 480L)
        val start = Date.now()
        val result = pitchToUtauMode1Track(pitch, notes)
        val elapsed = Date.now() - start
        val pointCount = result!!.notes.sumOf { it?.pitchPoints?.size ?: 0 }
        println(
            "BENCHMARK pitchToUtauMode1Track: " +
                "${pitch.data.size} points, $noteCount notes -> $pointCount points in $elapsed ms",
        )
    }
}
