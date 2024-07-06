package core.exception

@OptIn(ExperimentalJsExport::class)
@JsExport
class EmptyProjectException : Throwable("This format could not take en empty project.")
