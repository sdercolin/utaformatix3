package core.exception

@OptIn(ExperimentalJsExport::class)
@JsExport
open class UnsupportedFileFormatError : Exception()

@OptIn(ExperimentalJsExport::class)
@JsExport
class UnsupportedLegacyPpsfError : UnsupportedFileFormatError()
