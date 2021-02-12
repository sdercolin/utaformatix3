package process.pitch

import model.TICKS_IN_BEAT
import model.Tempo
import process.interpolateCosineEaseInOut
import process.interpolateLinear

private const val SAMPLING_INTERVAL_TICK = 4L
private const val SVP_VIBRATO_DEFAULT_START_SEC = 0.25
private const val SVP_VIBRATO_DEFAULT_EASE_IN_SEC = 0.2
private const val SVP_VIBRATO_DEFAULT_EASE_OUT_SEC = 0.2
private const val SVP_VIBRATO_DEFAULT_DEPTH_SEMITONE = 1.0
private const val SVP_VIBRATO_DEFAULT_FREQUENCY_HZ = 5.5
private const val SVP_VIBRATO_DEFAULT_PHASE_RAD = 0.0

data class SvpNoteWithVibrato(
    val noteStartTick: Long, // tick
    val noteLengthTick: Long, // tick
    val vibratoStart: Double?, // sec
    val easeInLength: Double?, // sec
    val easeOutLength: Double?, // sec
    val depth: Double?, // semitone
    val frequency: Double?, // Hz
    val phase: Double? // rad
) {
    val noteEndTick get() = noteStartTick + noteLengthTick
}

fun processSvpInputPitchData(
    points: List<Pair<Long, Double>>,
    mode: String?,
    notesWithVibrato: List<SvpNoteWithVibrato>,
    tempos: List<Tempo>,
    vibratoEnvPoints: List<Pair<Long, Double>>,
    vibratoEnvMode: String?
) = points
    .merge()
    .interpolate(mode)
    .orEmpty()
    .appendVibrato(
        notesWithVibrato,
        tempos,
        vibratoEnvPoints.merge().interpolate(vibratoEnvMode).orEmpty().extendEveryTick()
    )
    .removeRedundantPoints()

private fun List<Pair<Long, Double>>.merge() = groupBy { it.first }
    .mapValues { it.value.sumByDouble { (_, value) -> value } / it.value.count() }
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
    tempos: List<Tempo>,
    vibratoEnv: Map<Long, Double>
): List<Pair<Long, Double>> {
    // a piecewise linear transformation from tick to sec
    // simplify the calculation here for every usage to reduce cost
    val timeTransformationParameters = (tempos.zipWithNext() + (tempos.last() to null))
        .fold(listOf<Triple<LongRange, Double, Double>>()) { acc, (thisTempo, nextTempo) ->
            val range = thisTempo.tickPosition until (nextTempo?.tickPosition ?: Long.MAX_VALUE)
            val rate = getTickToTimeRate(thisTempo.bpm)
            val thisResult = if (acc.isEmpty()) {
                Triple(range, 0.0, rate)
            } else {
                val (lastRange, lastOffset, lastRate) = acc.last()
                val offset = lastOffset + (lastRange.last - lastRange.first) * lastRate
                Triple(range, offset, rate)
            }
            acc + thisResult
        }
        .map {
            object {
                val range = it.first
                val offset = it.second
                val rate = it.third
            }
        }
    val tickToSecTransformation: (Long) -> Double = { tick: Long ->
        timeTransformationParameters
            .first { tick in it.range }
            .let { it.offset + (tick - it.range.first) * it.rate }
    }
    val secToTickTransformation: (Double) -> Long = { sec: Double ->
        timeTransformationParameters
            .last { it.offset <= sec }
            .let { ((sec - it.offset) / it.rate).toLong() + it.range.first }
    }

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
                .appendVibratoInNote(note, tickToSecTransformation, secToTickTransformation, tempos, vibratoEnv)
        }
}

private fun List<Pair<Long, Double>>.appendVibratoInNote(
    note: SvpNoteWithVibrato?,
    tickToSecTransformation: (Long) -> Double,
    secToTickTransformation: (Double) -> Long,
    tempos: List<Tempo>,
    vibratoEnv: Map<Long, Double>
): List<Pair<Long, Double>> {
    note ?: return this

    val noteStart = tickToSecTransformation(note.noteStartTick)
    val noteEnd = tickToSecTransformation(note.noteEndTick)

    val vibratoStart = (note.vibratoStart ?: SVP_VIBRATO_DEFAULT_START_SEC) + noteStart
    val vibratoStartTick = secToTickTransformation(vibratoStart)
    val easeInLength = note.easeInLength ?: SVP_VIBRATO_DEFAULT_EASE_IN_SEC
    val easeOutLength = note.easeOutLength ?: SVP_VIBRATO_DEFAULT_EASE_OUT_SEC
    val depth = (note.depth ?: SVP_VIBRATO_DEFAULT_DEPTH_SEMITONE) * 0.5
    if (depth == 0.0) return this
    val phase = note.phase ?: SVP_VIBRATO_DEFAULT_PHASE_RAD
    val frequency = note.frequency ?: SVP_VIBRATO_DEFAULT_FREQUENCY_HZ

    val tickToTimeRate = getTickToTimeRate(tempos.last { it.tickPosition <= note.noteStartTick }.bpm)

    val vibrato = { tick: Long ->
        val sec = tickToSecTransformation(tick)
        if (sec < vibratoStart) 0.0
        else {
            val easeInFactor = ((sec - vibratoStart) / easeInLength).coerceIn(0.0..1.0)
                .takeIf { !it.isNaN() } ?: 1.0
            val easeOutFactor = ((noteEnd - sec) / easeOutLength).coerceIn(0.0..1.0)
                .takeIf { !it.isNaN() } ?: 1.0
            val rad = 2 * kotlin.math.PI * frequency * tickToTimeRate * (tick - vibratoStartTick) + phase
            val envelope = vibratoEnv[tick] ?: 1.0
            envelope * depth * easeInFactor * easeOutFactor * kotlin.math.sin(rad)
        }
    }

    return this
        .asSequence()
        .ifEmpty { sequenceOf(note.noteStartTick to 0.0, note.noteEndTick to 0.0) }
        .let {
            if (it.last().first != note.noteEndTick) it + (note.noteEndTick to it.last().second)
            else it
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

private fun getTickToTimeRate(bpm: Double) = 60.0 / TICKS_IN_BEAT / bpm

private fun List<Pair<Long, Double>>.removeRedundantPoints() =
    fold(listOf<Pair<Long, Double>>()) { acc, point ->
        val previousValue = acc.lastOrNull()?.second
        if (point.second != previousValue) acc + point else acc
    }

fun appendPitchPointsForSvpOutput(points: List<Pair<Long, Double>>) =
    listOfNotNull(points.firstOrNull()) +
            points.zipWithNext()
                .flatMap { (lastPoint, thisPoint) ->
                    val tickDiff = thisPoint.first - lastPoint.first
                    val newPoint = when {
                        tickDiff < SAMPLING_INTERVAL_TICK -> null
                        tickDiff < 2 * SAMPLING_INTERVAL_TICK ->
                            ((thisPoint.first + lastPoint.first) / 2) to lastPoint.second
                        else ->
                            thisPoint.first - SAMPLING_INTERVAL_TICK to lastPoint.second
                    }
                    listOfNotNull(newPoint, thisPoint)
                }
