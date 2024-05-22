package core.exception

import core.model.Note

@OptIn(ExperimentalJsExport::class)
@JsExport
class IllegalNotePositionException(@Suppress("NON_EXPORTABLE_TYPE") note: Note, trackIndex: Int) : Throwable(
    "Failed to import because note with illegal position(${note.tickOn}) exists in Track No.${trackIndex + 1}",
)
