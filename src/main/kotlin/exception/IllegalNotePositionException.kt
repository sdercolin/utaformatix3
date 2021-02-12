package exception

import model.Note

class IllegalNotePositionException(note: Note, trackIndex: Int) : Throwable(
    "Failed to import because note with illegal position(${note.tickOn}) exists in Track No.${trackIndex + 1}"
)
