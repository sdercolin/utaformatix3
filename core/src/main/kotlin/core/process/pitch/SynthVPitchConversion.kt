package core.process.pitch

import core.model.DEFAULT_BPM
import core.model.Tempo
import core.process.interpolateCosineEaseInOut
import core.process.interpolateLinear

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
        vibratoEnvPoints
            .merge()
            .interpolate(vibratoEnvMode)
            .orEmpty()
            .extendEveryTick(),
    ).removeRedundantPoints()

private fun List<Pair<Long, Double>>.merge() =
    groupBy { it.first }
        .mapValues { it.value.sumOf { (_, value) -> value } / it.value.count() }
        .toList()
        .sortedBy { it.first }

private fun List<Pair<Long, Double>>.interpolate(mode: String?) =
    when (mode) {
        "linear" -> this.interpolateLinear(SAMPLING_INTERVAL_TICK)
        "cosine" -> this.interpolateCosineEaseInOut(SAMPLING_INTERVAL_TICK)
        "cubic" -> this.interpolateCosineEaseInOut(SAMPLING_INTERVAL_TICK) // TODO: interpolateCubic
        else -> this.interpolateCosineEaseInOut(SAMPLING_INTERVAL_TICK)
    }

private fun List<Pair<Long, Double>>.extendEveryTick() =
    fold(listOf<Pair<Long, Double>>()) { acc, point ->
        val lastPoint = acc.lastOrNull()
        if (lastPoint == null || lastPoint.second == 1.0) {
            acc + point
        } else {
            val inserted =
                (lastPoint.first until point.first)
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
    val pitchPoints = this

    // Build a list of ranges paired with the corresponding note (or null for gaps)
    val rangesList = mutableListOf<Pair<LongRange, SvpNoteWithVibrato?>>()
    var lastTick = 0L
    for (note in notes) {
        if (lastTick < note.noteStartTick) {
            rangesList.add((lastTick until note.noteStartTick) to null)
        }
        rangesList.add((note.noteStartTick until note.noteEndTick) to note)
        lastTick = note.noteEndTick
    }
    // Extend to cover any remaining pitch points
    rangesList.add((lastTick until Long.MAX_VALUE) to null)

    // Merge pitch points with the computed ranges using a single pass over the sorted pitch points
    val result = mutableListOf<Pair<Long, Double>>()
    var pitchIndex = 0
    for ((range, note) in rangesList) {
        // Advance the index to the first point in or beyond the range
        while (pitchIndex < pitchPoints.size && pitchPoints[pitchIndex].first < range.first) {
            pitchIndex++
        }
        val startIndex = pitchIndex
        // Find all points that fall within the range
        while (pitchIndex < pitchPoints.size && pitchPoints[pitchIndex].first in range) {
            pitchIndex++
        }
        if (startIndex < pitchIndex) {
            val subset = pitchPoints.subList(startIndex, pitchIndex)
            result.addAll(
                subset.appendVibratoInNote(
                    note,
                    vibratoDefaultParameters,
                    transformer,
                    tempos,
                    vibratoEnv,
                ),
            )
        }
    }
    return result
}

private fun List<Pair<Long, Double>>.appendVibratoInNote(
    note: SvpNoteWithVibrato?,
    defaultParameters: SvpDefaultVibratoParameters?,
    tickTimeTransformer: TickTimeTransformer,
    tempos: List<Tempo>,
    vibratoEnv: Map<Long, Double>,
): List<Pair<Long, Double>> {
    // Skip if note is null or invalid.
    note?.takeIf { it.noteStartTick >= 0L } ?: return this

    val noteStartSec = tickTimeTransformer.tickToSec(note.noteStartTick)
    val noteEndSec = tickTimeTransformer.tickToSec(note.noteEndTick)

    // Determine when vibrato should start (in seconds) and convert back to tick.
    val vibratoStartSec =
        (note.vibratoStart ?: defaultParameters?.vibratoStart ?: SVP_VIBRATO_DEFAULT_START_SEC) + noteStartSec
    val vibratoStartTick = tickTimeTransformer.secToTick(vibratoStartSec)
    val easeInLength = note.easeInLength ?: defaultParameters?.easeInLength ?: SVP_VIBRATO_DEFAULT_EASE_IN_SEC
    val easeOutLength = note.easeOutLength ?: defaultParameters?.easeOutLength ?: SVP_VIBRATO_DEFAULT_EASE_OUT_SEC
    val depth = (note.depth ?: defaultParameters?.depth ?: SVP_VIBRATO_DEFAULT_DEPTH_SEMITONE) * 0.5
    if (depth == 0.0) return this
    val phase = note.phase ?: SVP_VIBRATO_DEFAULT_PHASE_RAD
    val frequency = note.frequency ?: defaultParameters?.frequency ?: SVP_VIBRATO_DEFAULT_FREQUENCY_HZ

    val secPerTick = (tempos.lastOrNull { it.tickPosition <= note.noteStartTick }?.bpm ?: DEFAULT_BPM).bpmToSecPerTick()

    // Define the vibrato function to compute the vibrato offset for a given tick.
    fun vibrato(tick: Long): Double {
        val sec = tickTimeTransformer.tickToSec(tick)
        if (sec < vibratoStartSec) return 0.0
        val easeInFactor = ((sec - vibratoStartSec) / easeInLength).coerceIn(0.0, 1.0)
        val easeOutFactor = ((noteEndSec - sec) / easeOutLength).coerceIn(0.0, 1.0)
        val rad = 2 * kotlin.math.PI * frequency * secPerTick * (tick - vibratoStartTick) + phase
        val envelope = vibratoEnv[tick] ?: 1.0
        return envelope * depth * easeInFactor * easeOutFactor * kotlin.math.sin(rad)
    }

    // Prepare the base pitch points. If the list is empty, create default points for the note.
    val basePoints: List<Pair<Long, Double>> =
        this.ifEmpty {
            listOf(note.noteStartTick to 0.0, note.noteEndTick to 0.0)
        }

    // Ensure we have a point at the note's end tick.
    val points: List<Pair<Long, Double>> =
        if (basePoints.last().first != note.noteEndTick) {
            basePoints + listOf(note.noteEndTick to basePoints.last().second)
        } else {
            basePoints
        }

    // Use a mutable list to accumulate the result without repeatedly concatenating lists.
    val result = mutableListOf<Pair<Long, Double>>()
    var prev: Pair<Long, Double>? = null
    for (point in points) {
        if (prev == null) {
            // Add the first point with its vibrato adjustment.
            result.add(point.first to (point.second + vibrato(point.first)))
        } else {
            // For points between the previous and current, interpolate at fixed SAMPLING_INTERVAL_TICK steps.
            var tick = prev.first + SAMPLING_INTERVAL_TICK
            while (tick < point.first) {
                result.add(tick to (prev.second + vibrato(tick)))
                tick += SAMPLING_INTERVAL_TICK
            }
            // Add the current point.
            result.add(point.first to (point.second + vibrato(point.first)))
        }
        prev = point
    }
    return result
}

private fun List<Pair<Long, Double>>.removeRedundantPoints() =
    fold(listOf<Pair<Long, Double>>()) { acc, point ->
        val previousValue = acc.lastOrNull()?.second
        if (point.second != previousValue) acc + point else acc
    }

fun List<Pair<Long, Double>>.appendPitchPointsForSvpOutput() =
    appendPitchPointsForInterpolation(this, SAMPLING_INTERVAL_TICK)
        .reduceRepeatedPitchPoints()
