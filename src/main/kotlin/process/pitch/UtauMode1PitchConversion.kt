package process.pitch

import io.UstMode1
import model.Note
import model.Pitch
import process.interpolateLinear

data class UtauMode1TrackPitchData(
    val notes: List<UtauMode1NotePitchData?>
)

/** This class contains Utau Pitch Data in Mode1.
 * However, it ignores some parameters which are contained in the ust(mode1) file in purpose:
 *
 * PBType: it indicates which data format this file uses. It will always be treated as "5"
 * since UTAU only uses "OldData" in very early versions.
 *
 * PBStart: it indicates when the pitch data begins, and it will be negative when pitch data
 * begins from PreUtterance of the note, otherwise, it will be 0. Since Only old versions of utau
 * produce this kind of data, and it will not save this value in the ust (this field is introduced
 * in version 0.4.0), presuming it as 0 will make things much easier.
 *
 * Please notice that UTAU save its pitch data by cent, not semitone. This class will keep this behaviour.
 */
data class UtauMode1NotePitchData(
    val pitchData: Pitch?
)

fun pitchFromUtauMode1Track(pitchData: UtauMode1TrackPitchData?, notes: List<Note>): Pitch? {
    pitchData ?: return null
    val notePitches = notes.zip(pitchData.notes)
    val pitchPoints = mutableListOf<Pair<Long, Double>>()
    for ((note, notePitch) in notePitches) {
        notePitch?.pitchData?.data?.let { data ->
            pitchPoints.addAll(data.map {
                Pair(note.tickOn + it.first, it.second?.div(100) ?: return null)
            })
        }
    }
    return Pitch(pitchPoints, false).getAbsoluteData(notes)?.let { Pitch(it, true) }
}

// some value gained by some not precise experiments. Used as a "allowance" when cut pitches into separate note.
private const val BOUNDARY_EXTEND_LEVEL = 10
fun pitchToUtauMode1Track(pitch: Pitch?, notes: List<Note>): UtauMode1TrackPitchData? {
    pitch ?: return null
    val pitchDataProcessed =
        pitch.getRelativeData(notes)?.map { Pair(it.first, it.second) }?.interpolateLinear(UstMode1.PITCH_SAMPLING_INTERVAL_TICK)
            ?: return null
    val notePitches = mutableListOf<UtauMode1NotePitchData>()
    for (note in notes) {
        var notePitchPoints =
            pitchDataProcessed.filter { (tick, _) -> tick >= (note.tickOn + BOUNDARY_EXTEND_LEVEL) && tick <= (note.tickOff + BOUNDARY_EXTEND_LEVEL) }
                .let {
                    Pitch(it, false).data.map { Pair(it.first, it.second ?: 0.0) }
                        .interpolateLinear(UstMode1.PITCH_SAMPLING_INTERVAL_TICK)
                }
        notePitchPoints = notePitchPoints?.subList(0, minOf((note.length / UstMode1.PITCH_SAMPLING_INTERVAL_TICK).toInt(), notePitchPoints.count() - 1))
        notePitches.add(UtauMode1NotePitchData(notePitchPoints?.let { it ->
            Pitch(it, true).data.map { (tick, value) -> Pair(tick, (value ?: 0.0) * 100) }.let { relativeData ->
                Pitch(
                    relativeData, false
                )
            }
        }))
    }
    return UtauMode1TrackPitchData(notePitches)
}
