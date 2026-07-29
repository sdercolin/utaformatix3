package process.pitch

import core.model.Tempo
import core.process.pitch.SvpNoteWithVibrato
import core.process.pitch.processSvpInputPitchData
import kotlin.js.Date
import kotlin.math.sin
import kotlin.test.Test

/**
 * Not a correctness test: measures the SVP pitch import pipeline on a synthetic project
 * (a few minutes of notes with vibrato, a densely drawn pitch curve and a vibrato envelope)
 * and prints the elapsed time, so performance regressions show up in test logs.
 */
class SvpPitchBenchmarkTest {
    @Test
    fun benchmarkProcessSvpInputPitchData() {
        val noteCount = 120
        val noteLengthTick = 480L
        val totalTicks = noteCount * noteLengthTick
        val notes =
            (0 until noteCount).map {
                SvpNoteWithVibrato(
                    noteStartTick = it * noteLengthTick,
                    noteLengthTick = noteLengthTick,
                    vibratoStart = null,
                    easeInLength = null,
                    easeOutLength = null,
                    depth = null,
                    frequency = null,
                    phase = null,
                )
            }
        val points = (0L until totalTicks step 8).map { it to sin(it * 0.01) * 30 }
        val vibratoEnvPoints = (0L until totalTicks step 100).map { it to 0.9 }
        val tempos = listOf(Tempo(0L, 120.0), Tempo(totalTicks / 2, 140.0))

        val start = Date.now()
        val result =
            processSvpInputPitchData(
                points = points,
                mode = "cubic",
                notesWithVibrato = notes,
                tempos = tempos,
                vibratoEnvPoints = vibratoEnvPoints,
                vibratoEnvMode = "cubic",
                vibratoDefaultParameters = null,
            )
        val elapsed = Date.now() - start
        println(
            "BENCHMARK processSvpInputPitchData: " +
                "${points.size} input points -> ${result.size} points in $elapsed ms",
        )
    }
}
