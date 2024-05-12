package core.process.pitch

import core.io.Ust
import core.model.Note
import core.model.Pitch
import core.process.dotResampled

data class UtauMode1TrackPitchData(
    val notes: List<UtauMode1NotePitchData?>,
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
    val pitchPoints: List<Double>?,
)

fun pitchFromUtauMode1Track(pitchData: UtauMode1TrackPitchData?, notes: List<Note>): Pitch? {
    pitchData ?: return null
    val notePitches = notes.zip(pitchData.notes)
    val pitchPoints = mutableListOf<Pair<Long, Double>>()
    for ((note, notePitch) in notePitches) {
        notePitch?.pitchPoints?.let { data ->
            pitchPoints.addAll(
                data.mapIndexed { index, value ->
                    Pair(note.tickOn + index * Ust.MODE1_PITCH_SAMPLING_INTERVAL_TICK, value / 100)
                },
            )
        }
    }
    return Pitch(pitchPoints, false).getAbsoluteData(notes)?.let { Pitch(it, true) }
}

fun pitchToUtauMode1Track(pitch: Pitch?, notes: List<Note>): UtauMode1TrackPitchData? {
    pitch ?: return null
    return UtauMode1TrackPitchData(
        notes.map { note ->
            UtauMode1NotePitchData(
                pitch
                    .getAbsoluteData(notes)
                    ?.filter { it.first >= note.tickOn && it.first < note.tickOff }
                    ?.dotResampled(Ust.MODE1_PITCH_SAMPLING_INTERVAL_TICK)
                    ?.map { Pair(it.first, it.second ?: note.key.toDouble()) }
                    ?.map { (it.second - note.key) * 100 },
            )
        },
    )
}
