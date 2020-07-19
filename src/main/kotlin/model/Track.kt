package model

import kotlin.math.min

data class Track(
    val id: Int,
    val name: String,
    val notes: List<Note>
) {

    fun validateNotes() =
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
}
