package core.process

import core.model.Note
import core.model.Project
import core.model.Track
import kotlin.math.min

interface RichNote<T : Any> {
    val note: Note
    fun copyWithNote(note: Note): T
}

fun <T : RichNote<T>> List<T>.validateNotes(): List<T> {
    if (isEmpty()) return this
    return this.sortedBy { it.note.tickOn }
        .let { list ->
            list.asSequence()
                .zipWithNext()
                .map { (current, next) ->
                    current.copyWithNote(current.note.copy(tickOff = min(current.note.tickOff, next.note.tickOn)))
                }
                .filter { it.note.length > 0 }
                .plus(list.last())
                .mapIndexed { index, richNote ->
                    richNote.copyWithNote(richNote.note.copy(id = index))
                }
                .toList()
        }
}

fun Track.validateNotes() = copy(notes = notes.validateNotes())

fun Project.fillRests(excludedMaxLength: Long) = copy(tracks = tracks.map { it.fillRests(excludedMaxLength) })

private fun Track.fillRests(excludedMaxLength: Long) =
    if (notes.isEmpty()) this
    else this.copy(
        notes = notes.let {
            it.zipWithNext()
                .map { (note, nextNote) ->
                    if (nextNote.tickOn - note.tickOff < excludedMaxLength) {
                        note.copy(tickOff = nextNote.tickOn)
                    } else note
                }
                .plus(it.last())
        },
    )

const val RESTS_FILLING_MAX_LENGTH_DENOMINATOR_DEFAULT = 64
val restsFillingMaxLengthDenominatorOptions = listOf(8, 16, 32, 64, 128)
