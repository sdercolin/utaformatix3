package core.model

@OptIn(ExperimentalJsExport::class)
@JsExport
sealed class ExportNotification {
    object PhonemeResetRequiredVSQ : ExportNotification()
    object PhonemeResetRequiredV4 : ExportNotification()
    object PhonemeResetRequiredV5 : ExportNotification()
    object TimeSignatureIgnored : ExportNotification()
    object PitchDataExported : ExportNotification()
    object DataOverLengthLimitIgnored : ExportNotification()
}
