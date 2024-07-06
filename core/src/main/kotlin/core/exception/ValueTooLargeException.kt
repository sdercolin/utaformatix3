package core.exception

@OptIn(ExperimentalJsExport::class)
@JsExport
class ValueTooLargeException(value: String, maxValue: String) : Throwable(
    "Given value $value is larger than the maximum: $maxValue.",
)
