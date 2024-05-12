package core.exception

open class UnsupportedFileFormatError : Exception()

class UnsupportedLegacyPpsfError : UnsupportedFileFormatError()
