package process.pitch

import io.UstLegacy
import model.Note
import model.Pitch
import process.interpolateLinear

data class UtauLegacyTrackPitchData(
    val notes: List<UtauLegacyNotePitchData?>
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
data class UtauLegacyNotePitchData(
    val pitchData: Pitch?
)

fun pitchFromUtauTrack(pitchData: UtauLegacyTrackPitchData?, notes: List<Note>): Pitch? {
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

fun pitchToUtauLegacyTrack(pitch: Pitch?, notes: List<Note>): UtauLegacyTrackPitchData? {
    pitch ?: return null
    val pitchDataProcessed =
        pitch.getRelativeData(notes)?.map { Pair(it.first, it.second) }?.interpolateLinear(UstLegacy.PITCH_TICK) ?: return null
    val notePitches = mutableListOf<UtauLegacyNotePitchData>()
    for (note in notes) {
        val notePitchPoints = pitchDataProcessed.filter { it.first >= note.tickOn && it.first <= note.tickOff }.let {
                Pitch(it, false).data.map{Pair(it.first, it.second ?: 0.0)}.interpolateLinear(UstLegacy.PITCH_TICK)
            }
        notePitches.add(UtauLegacyNotePitchData(notePitchPoints?.let { it ->
            Pitch(it, true).data.map{Pair(it.first, (it.second?:0.0) * 100)}.let { relativeData ->
                Pitch(
                    relativeData, false
                )
            }
        }))
    }
    return UtauLegacyTrackPitchData(notePitches)
}
