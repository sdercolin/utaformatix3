package model

sealed class ExportNotification {
    object PhonemeResetRequiredVSQ : ExportNotification()
    object PhonemeResetRequiredV4 : ExportNotification()
    object PhonemeResetRequiredV5 : ExportNotification()
    object TempoChangeIgnored : ExportNotification()
    object TimeSignatureIgnored : ExportNotification()
}
