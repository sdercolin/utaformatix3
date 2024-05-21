package core.exception

@OptIn(ExperimentalJsExport::class)
@JsExport
class NotesOverlappingException : Throwable(
    "Failed to process because there are notes overlapping with each other.",
)
