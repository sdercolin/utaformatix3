package core.process.pitch

import core.model.DEFAULT_BPM
import core.model.Tempo
import core.process.interpolateCosineEaseInOut
import core.process.interpolateLinear
import core.util.runIf

private const val SAMPLING_INTERVAL_TICK = 4L
private const val SVP_VIBRATO_DEFAULT_START_SEC = 0.25
private const val SVP_VIBRATO_DEFAULT_EASE_IN_SEC = 0.2
private const val SVP_VIBRATO_DEFAULT_EASE_OUT_SEC = 0.2
private const val SVP_VIBRATO_DEFAULT_DEPTH_SEMITONE = 1.0
private const val SVP_VIBRATO_DEFAULT_FREQUENCY_HZ = 5.5
private const val SVP_VIBRATO_DEFAULT_PHASE_RAD = 0.0

data class SvpDefaultVibratoParameters(
    val vibratoStart: Double?, // sec
    val easeInLength: Double?, // sec
    val easeOutLength: Double?, // sec
    val depth: Double?, // semitone
    val frequency: Double?, // Hz
)

data class SvpNoteWithVibrato(
    val noteStartTick: Long, // tick
    val noteLengthTick: Long, // tick
    val vibratoStart: Double?, // sec
    val easeInLength: Double?, // sec
    val easeOutLength: Double?, // sec
    val depth: Double?, // semitone
    val frequency: Double?, // Hz
    val phase: Double?, // rad
) {
    val noteEndTick get() = noteStartTick + noteLengthTick
}

fun processSvpInputPitchData(
    points: List<Pair<Long, Double>>,
    mode: String?,
    notesWithVibrato: List<SvpNoteWithVibrato>,
    tempos: List<Tempo>,
    vibratoEnvPoints: List<Pair<Long, Double>>,
    vibratoEnvMode: String?,
    vibratoDefaultParameters: SvpDefaultVibratoParameters?,
) = points
    .merge()
    .interpolate(mode)
    .orEmpty()
    .appendVibrato(
        notesWithVibrato,
        vibratoDefaultParameters,
        tempos,
        vibratoEnvPoints.merge().interpolate(vibratoEnvMode).orEmpty().extendEveryTick(),
    )
    .removeRedundantPoints()

private fun List<Pair<Long, Double>>.merge() = groupBy { it.first }
    .mapValues { it.value.sumOf { (_, value) -> value } / it.value.count() }
    .toList()
    .sortedBy { it.first }

private fun List<Pair<Long, Double>>.interpolate(mode: String?) = when (mode) {
    "linear" -> this.interpolateLinear(SAMPLING_INTERVAL_TICK)
    "cosine" -> this.interpolateCosineEaseInOut(SAMPLING_INTERVAL_TICK)
    "cubic" -> this.interpolateCosineEaseInOut(SAMPLING_INTERVAL_TICK) // TODO: interpolateCubic
    else -> this.interpolateCosineEaseInOut(SAMPLING_INTERVAL_TICK)
}

private fun List<Pair<Long, Double>>.extendEveryTick() =
    fold(listOf<Pair<Long, Double>>()) { acc, point ->
        val lastPoint = acc.lastOrNull()
        if (lastPoint == null || lastPoint.second == 1.0) acc + point
        else {
            val inserted = (lastPoint.first until point.first)
                .map { it to lastPoint.second }
            acc + inserted + point
        }
    }.toMap()

private fun List<Pair<Long, Double>>.appendVibrato(
    notes: List<SvpNoteWithVibrato>,
    vibratoDefaultParameters: SvpDefaultVibratoParameters?,
    tempos: List<Tempo>,
    vibratoEnv: Map<Long, Double>,
): List<Pair<Long, Double>> {
    val transformer = TickTimeTransformer(tempos)

    return notes
        .fold<SvpNoteWithVibrato, List<Pair<LongRange, SvpNoteWithVibrato?>>>(listOf()) { acc, note ->
            val lastNoteEndTick = acc.lastOrNull()?.first?.last ?: 0L
            if (lastNoteEndTick < note.noteStartTick) {
                acc + ((lastNoteEndTick until note.noteStartTick) to null) +
                    ((note.noteStartTick until note.noteEndTick) to note)
            } else {
                acc + ((note.noteStartTick until note.noteEndTick) to note)
            }
        }
        .let { it + ((it.lastOrNull()?.first?.last ?: 0L) until Long.MAX_VALUE to null) }
        .flatMap { (range, note) ->
            this.filter { (tick, _) -> tick in range }
                .appendVibratoInNote(
                    note,
                    vibratoDefaultParameters,
                    transformer,
                    tempos,
                    vibratoEnv,
                )
        }
}

private fun List<Pair<Long, Double>>.appendVibratoInNote(
    note: SvpNoteWithVibrato?,
    defaultParameters: SvpDefaultVibratoParameters?,
    tickTimeTransformer: TickTimeTransformer,
    tempos: List<Tempo>,
    vibratoEnv: Map<Long, Double>,
): List<Pair<Long, Double>> {
    // Note with minus position is skipped, but with raise an error after import, see Project.requireValid()
    note?.takeIf { it.noteStartTick >= 0L } ?: return this

    val noteStart = tickTimeTransformer.tickToSec(note.noteStartTick)
    val noteEnd = tickTimeTransformer.tickToSec(note.noteEndTick)

    val vibratoStart =
        (note.vibratoStart ?: defaultParameters?.vibratoStart ?: SVP_VIBRATO_DEFAULT_START_SEC) + noteStart
    val vibratoStartTick = tickTimeTransformer.secToTick(vibratoStart)
    val easeInLength = note.easeInLength ?: defaultParameters?.easeInLength ?: SVP_VIBRATO_DEFAULT_EASE_IN_SEC
    val easeOutLength = note.easeOutLength ?: defaultParameters?.easeOutLength ?: SVP_VIBRATO_DEFAULT_EASE_OUT_SEC
    val depth = (note.depth ?: defaultParameters?.depth ?: SVP_VIBRATO_DEFAULT_DEPTH_SEMITONE) * 0.5
    if (depth == 0.0) return this
    val phase = note.phase ?: SVP_VIBRATO_DEFAULT_PHASE_RAD
    val frequency = note.frequency ?: defaultParameters?.frequency ?: SVP_VIBRATO_DEFAULT_FREQUENCY_HZ

    val secPerTick =
        (tempos.lastOrNull { it.tickPosition <= note.noteStartTick }?.bpm ?: DEFAULT_BPM).bpmToSecPerTick()

    val vibrato = { tick: Long ->
        val sec = tickTimeTransformer.tickToSec(tick)
        if (sec < vibratoStart) 0.0
        else {
            val easeInFactor = ((sec - vibratoStart) / easeInLength).coerceIn(0.0..1.0)
                .takeIf { !it.isNaN() } ?: 1.0
            val easeOutFactor = ((noteEnd - sec) / easeOutLength).coerceIn(0.0..1.0)
                .takeIf { !it.isNaN() } ?: 1.0
            val rad = 2 * kotlin.math.PI * frequency * secPerTick * (tick - vibratoStartTick) + phase
            val envelope = vibratoEnv[tick] ?: 1.0
            envelope * depth * easeInFactor * easeOutFactor * kotlin.math.sin(rad)
        }
    }

    return this
        .asSequence()
        .ifEmpty { sequenceOf(note.noteStartTick to 0.0, note.noteEndTick to 0.0) }
        .runIf({ last().first != note.noteEndTick }) {
            this + (note.noteEndTick to last().second)
        }
        .fold(listOf<Pair<Long, Double>>()) { acc, inputPoint ->
            val lastPoint = acc.lastOrNull()
            val newPoint = inputPoint.let { it.first to (it.second + vibrato(it.first)) }
            if (lastPoint == null) {
                acc + newPoint
            } else {
                val interpolatedIndexes = ((lastPoint.first + 1) until inputPoint.first)
                    .filter { (it - lastPoint.first) % SAMPLING_INTERVAL_TICK == 0L }
                val interpolatedPoints = interpolatedIndexes.map { it to (lastPoint.second + vibrato(it)) }
                acc + interpolatedPoints + newPoint
            }
        }
        .toList()
}

private fun List<Pair<Long, Double>>.removeRedundantPoints() =
    fold(listOf<Pair<Long, Double>>()) { acc, point ->
        val previousValue = acc.lastOrNull()?.second
        if (point.second != previousValue) acc + point else acc
    }

fun List<Pair<Long, Double>>.appendPitchPointsForSvpOutput() =
    appendPitchPointsForInterpolation(this, SAMPLING_INTERVAL_TICK)
        .reduceRepeatedPitchPoints()
