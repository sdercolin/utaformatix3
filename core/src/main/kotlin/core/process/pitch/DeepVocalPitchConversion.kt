package core.process.pitch

import core.io.Dv
import core.model.Note
import core.model.Pitch
import core.model.Tempo
import core.process.RichNote
import core.process.interpolateCosineEaseInOut
import core.process.interpolateLinear
import kotlin.math.roundToInt

private const val SAMPLING_INTERVAL_TICK = 4L
private const val PORTAMENTO_LENGTH_MAX_SEC = 0.3125 // [porHead, porTail] == 100
private const val BEND_DOWN_LENGTH_FIXED_SEC = 0.09375
private const val BEND_LENGTH_MIN_SEC = 0.375 // benLen <= 50
private const val BEND_LENGTH_MAX_SEC = 0.6875 // benLen == 100
private const val BEND_VALUE_MAX = 3.0 // benDep == 100

data class DvSegmentPitchRawData(
    val tickOffset: Long, // only for import
    val data: List<Pair<Int, Int>>, // (tick, DV style cent)
)

data class DvNoteWithPitch(
    override val note: Note,
    val porHead: Int, // 0~100
    val porTail: Int, // 0~100
    val benLen: Int, // 0~100
    val benDep: Int, // 0~100
    val vibrato: List<Pair<Int, Int>>, // (ms, minus cent)
) : RichNote<DvNoteWithPitch> {
    override fun copyWithNote(note: Note) = copy(note = note)
}

fun pitchFromDvTrack(
    segments: List<DvSegmentPitchRawData>,
    notes: List<DvNoteWithPitch>,
    tempos: List<Tempo>,
) = segments
    .mergePointsFromSegments()
    .mergeSameTickPoints()
    ?.mergeSameValuePoints()
    ?.applyDefaultPitch(notes, tempos)
    ?.let { Pitch(data = it, isAbsolute = true) }

private fun List<DvSegmentPitchRawData>.mergePointsFromSegments() = flatMap { segment ->
    segment.data
        .asSequence()
        .mapNotNull { list ->
            val rawTick = list.first.takeIf { it >= 0 } ?: return@mapNotNull null
            val centValue = list.second
            val tick = rawTick + segment.tickOffset
            val value = centValue.takeUnless { it < 0 }?.toDouble()?.div(100)?.let(Dv::convertNoteKey)
            tick to value
        }
        .toList()
}

private fun List<Pair<Long, Double?>>.mergeSameTickPoints() = asSequence()
    .groupBy { it.first }
    .map { (tick, points) ->
        if (points.size > 1) {
            if (points.any { it.second == null }) {
                tick to null
            } else {
                val values = points.mapNotNull { it.second }
                tick to values.average()
            }
        } else {
            tick to points[0].second
        }
    }
    .toList()
    .sortedBy { it.first }
    .takeIf { points -> points.any { it.second != null } }

private fun List<Pair<Long, Double?>>.mergeSameValuePoints() = asSequence()
    .sortedBy { it.first }
    .fold(listOf<Pair<Long, Double?>>()) { acc, point ->
        val lastValue = acc.lastOrNull()?.second
        if (point.second != lastValue) acc + point else acc
    }

private fun List<Pair<Long, Double?>>.applyDefaultPitch(
    notes: List<DvNoteWithPitch>,
    tempos: List<Tempo>,
): List<Pair<Long, Double?>> {
    if (this.isEmpty()) return this
    if (notes.isEmpty()) return this

    val transformer = TickTimeTransformer(tempos)

    val base = getBasePitch(notes, transformer)
    val bendDiff = getBendPitch(notes, transformer)
    val vibratoDiff = getVibratoPitch(notes, transformer)

    val appendingLastPoint = if (last().first < notes.last().note.tickOff) {
        notes.last().note.tickOff to null
    } else null

    return (this + appendingLastPoint).filterNotNull()
        .fold(listOf()) { acc, point ->
            val lastPoint = acc.lastOrNull()
            val startTick = lastPoint?.first ?: 0
            val endTick = point.first

            if (lastPoint?.second == null) {
                val interpolatedPoints = (startTick until endTick step SAMPLING_INTERVAL_TICK)
                    .map { it to ((base[it] ?: 0.0) + (bendDiff[it] ?: 0.0) + (vibratoDiff[it] ?: 0.0)) }
                acc + interpolatedPoints + point
            } else {
                acc + point
            }
        }
}

private fun getBasePitch(
    notes: List<DvNoteWithPitch>,
    transformer: TickTimeTransformer,
) = (listOf(null) + notes + listOf(null)).zipWithNext().flatMap { (lastNote, thisNote) ->
    val result = mutableListOf<Pair<Long, Double>>()

    val portamento = if (lastNote != null && thisNote != null) {
        getPortamento(lastNote, transformer, thisNote)
    } else emptyList()
    result.addAll(portamento)

    if (lastNote != null) {
        val lastNoteTail = (
            lastNote.note.tickHalfStart until
                (portamento.firstOrNull()?.first ?: lastNote.note.tickOff)
            )
            .map { it to lastNote.note.key.toDouble() }
        result.addAll(lastNoteTail)
    }

    if (thisNote != null) {
        val start = if (lastNote == null) 0 else portamento.lastOrNull()?.first ?: thisNote.note.tickOn
        val thisNoteHead = (start until thisNote.note.tickHalfStart)
            .map { it to thisNote.note.key.toDouble() }
        result.addAll(thisNoteHead)
    }

    result
}.mergeSameTickPoints().let { requireNotNull(it) }.toMap()

private fun getBendPitch(
    notes: List<DvNoteWithPitch>,
    transformer: TickTimeTransformer,
) = notes.flatMap { note ->
    val startTick = note.note.tickOn
    val startSec = transformer.tickToSec(startTick)
    val valleySec = startSec + BEND_DOWN_LENGTH_FIXED_SEC
    val valleyTick = transformer.secToTick(valleySec)
        .coerceAtMost(note.note.tickOn + note.note.length / 2 - 1)

    val lengthSec = if (note.benLen <= 50) {
        BEND_LENGTH_MIN_SEC
    } else {
        (BEND_LENGTH_MAX_SEC - BEND_LENGTH_MIN_SEC) * (note.benLen - 50) / 50 + BEND_LENGTH_MIN_SEC
    }
    val endSec = startSec + lengthSec
    val endTick = transformer.secToTick(endSec)
        .coerceAtMost(note.note.tickOff - 1)

    val valleyValue = -BEND_VALUE_MAX * note.benDep / 100
    val valleyPoint = valleyTick to valleyValue

    val bendDown = listOf(startTick to 0.0, valleyPoint)
        .interpolateLinear(1L)
        .orEmpty()

    val bendUp = listOf(valleyPoint, endTick to 0.0)
        .interpolateCosineEaseInOut(1L)
        .orEmpty()
        .drop(1)

    bendDown + bendUp
}.mergeSameTickPoints().orEmpty().toMap()

private fun getPortamento(
    lastNote: DvNoteWithPitch,
    transformer: TickTimeTransformer,
    thisNote: DvNoteWithPitch,
): List<Pair<Long, Double>> {
    val tailLengthSec = PORTAMENTO_LENGTH_MAX_SEC * lastNote.porTail / 100
    val startSec = transformer.tickToSec(lastNote.note.tickOff) - tailLengthSec
    val startTick = transformer.secToTick(startSec).coerceAtLeast(lastNote.note.tickHalfStart)

    val headLengthSec = PORTAMENTO_LENGTH_MAX_SEC * thisNote.porHead / 100
    val endSec = transformer.tickToSec(thisNote.note.tickOn) + headLengthSec
    val endTick = transformer.secToTick(endSec).coerceAtMost(thisNote.note.tickHalfStart - 1)

    return listOf(startTick to lastNote.note.key.toDouble(), endTick to thisNote.note.key.toDouble())
        .interpolateCosineEaseInOut(1L)
        .orEmpty()
}

private fun getVibratoPitch(
    notes: List<DvNoteWithPitch>,
    transformer: TickTimeTransformer,
) = notes.flatMap { note ->
    val startTick = note.note.tickOn
    val startSec = transformer.tickToSec(startTick)
    note.vibrato.asSequence()
        .map { (mSec, minusCent) ->
            val tick = transformer.secToTick((startSec + mSec.toDouble() / 1000))
            val key = -minusCent.toDouble() / 100
            tick to key
        }
        .filter { it.first >= startTick && it.first < note.note.tickOff }
        .sortedBy { it.first }
        .toList()
        .interpolateLinear(1L)
        .orEmpty()
}.mergeSameTickPoints().orEmpty().toMap()

private val Note.tickHalfStart: Long get() = tickOn + (length + 1) / 2

fun Pitch.generateForDv(notes: List<Note>): DvSegmentPitchRawData? {
    if (notes.isEmpty()) return null
    val points: List<Pair<Long, Double?>> = getAbsoluteData(notes)
        ?.takeIf { it.isNotEmpty() }
        ?: return null

    val data = listOf(-1 to -1) + points.appendPoints()
        .map {
            val rawValue = it.second?.let(Dv::convertNoteKey)?.times(100)?.roundToInt() ?: -1
            it.first.toInt() to rawValue
        }

    return DvSegmentPitchRawData(0L, data)
}

private fun List<Pair<Long, Double?>>.appendPoints(): List<Pair<Long, Double?>> {
    val results = mutableListOf<Pair<Long, Double?>>()
    var lastValue: Double? = null
    for (point in this) {
        if (lastValue == null && point.second != null) {
            results.add(point.first to null)
        }
        if (lastValue != null && point.second == null) {
            results.add(point.first to lastValue)
        }
        results.add(point)
        lastValue = point.second
    }
    return results.toList()
}
