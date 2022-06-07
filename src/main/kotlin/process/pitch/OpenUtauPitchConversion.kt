package process.pitch

import model.Note
import model.Pitch
import process.interpolateCosineEaseIn
import process.interpolateCosineEaseInOut
import process.interpolateCosineEaseOut
import process.interpolateLinear

private const val INPUT_SAMPLING_INTERVAL_TICK = 1L

data class OpenUtauNotePitchData(
    val points: List<Point>,
    val vibrato: UtauNoteVibratoParams
) {

    data class Point(
        val x: Double, // msec
        val y: Double, // 10 cents
        val shape: Shape
    )

    enum class Shape(val textValue: String) {
        EaseIn("i"),
        EaseOut("o"),
        EaseInOut("io"),
        Linear("l")
    }
}

data class OpenUtauPartPitchData(
    val points: List<Point>,
    val notes: List<OpenUtauNotePitchData>
) {
    data class Point(
        val x: Long, // tick
        val y: Int // cent
    )
}

fun pitchFromUstxPart(notes: List<Note>, pitchData: OpenUtauPartPitchData, bpm: Double): Pitch? {
    val notePitches = notes.zip(pitchData.notes)
    val pitchPoints = mutableListOf<Pair<Long, Double>>()
    for ((note, notePitch) in notePitches) {
        val pointsOfNote = mutableListOf<Pair<Long, Double>>()
        var lastPointShape = OpenUtauNotePitchData.Shape.EaseInOut
        for (rawPoint in notePitch.points) {
            val thisPoint = (note.tickOn + tickFromMilliSec(rawPoint.x, bpm)) to (rawPoint.y / 10)
            val lastPoint = pointsOfNote.lastOrNull()
            if (thisPoint.second != lastPoint?.second && lastPoint != null) {
                val interpolatedPointList = interpolate(lastPoint, thisPoint, lastPointShape)
                pointsOfNote.addAll(interpolatedPointList.drop(1))
            } else {
                pointsOfNote.add(thisPoint)
            }
            lastPointShape = rawPoint.shape
        }
        val pointsOfNoteWithVibrato =
            pointsOfNote.appendUtauNoteVibrato(notePitch.vibrato, note, bpm, INPUT_SAMPLING_INTERVAL_TICK)
    }
    return Pitch(pitchPoints, isAbsolute = false)
}

private fun interpolate(
    lastPoint: Pair<Long, Double>,
    thisPoint: Pair<Long, Double>,
    shape: OpenUtauNotePitchData.Shape
): List<Pair<Long, Double>> {
    val input = listOf(lastPoint, thisPoint)
    val output = when (shape) {
        OpenUtauNotePitchData.Shape.EaseIn -> input.interpolateCosineEaseIn(INPUT_SAMPLING_INTERVAL_TICK)
        OpenUtauNotePitchData.Shape.EaseOut -> input.interpolateCosineEaseOut(INPUT_SAMPLING_INTERVAL_TICK)
        OpenUtauNotePitchData.Shape.EaseInOut -> input.interpolateCosineEaseInOut(INPUT_SAMPLING_INTERVAL_TICK)
        OpenUtauNotePitchData.Shape.Linear -> input.interpolateLinear(INPUT_SAMPLING_INTERVAL_TICK)
    }
    return output.orEmpty()
}
