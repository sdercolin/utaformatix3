package exception

class ValueTooLargeException(value: String, maxValue: String) : Throwable(
    "Given value $value is larger than the maximum: $maxValue."
)
