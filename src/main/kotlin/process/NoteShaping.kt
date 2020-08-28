package process

import model.Track
import kotlin.math.min

fun Track.validateNotes() =
    if (notes.isEmpty()) this
    else this.copy(
        notes = notes
            .sortedBy { it.tickOn }
            .let {
                it.zipWithNext()
                    .map { (note, nextNote) ->
                        note.copy(tickOff = min(note.tickOff, nextNote.tickOn))
                    }
                    .filter { note -> note.length > 0 }
                    .plus(it.last())
            }
            .mapIndexed { index, note -> note.copy(id = index) }
    )

fun Track.fillRests(excludedMaxLength: Long) =
    if (notes.isEmpty()) this
    else this.copy(
        notes = notes.let {
            it.zipWithNext()
                .map { (note, nextNote) ->
                    if (nextNote.tickOn - note.tickOff < excludedMaxLength)
                        note.copy(tickOff = nextNote.tickOn)
                    else note
                }
                .plus(it.last())
        }
    )

const val RESTS_FILLING_MAX_LENGTH_DENOMINATOR_DEFAULT = 64
val restsFillingMaxLengthDenominatorOptions = listOf(8, 16, 32, 64, 128)
