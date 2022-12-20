package model

import org.w3c.files.File

sealed class ImportWarning {
    object TempoNotFound : ImportWarning()
    class TempoIgnoredInFile(val file: File, val tempo: Tempo) : ImportWarning()
    class TempoIgnoredInTrack(val track: Track, val tempo: Tempo) : ImportWarning()
    class TempoIgnoredInPreMeasure(val tempo: Tempo) : ImportWarning()
    class DefaultTempoFixed(val originalBpm: Double) : ImportWarning()
    object TimeSignatureNotFound : ImportWarning()
    class TimeSignatureIgnoredInTrack(val track: Track, val timeSignature: TimeSignature) : ImportWarning()
    class TimeSignatureIgnoredInPreMeasure(val timeSignature: TimeSignature) : ImportWarning()
    class IncompatibleFormatSerializationVersion(val currentVersion: String, val dataVersion: String) : ImportWarning()
}
