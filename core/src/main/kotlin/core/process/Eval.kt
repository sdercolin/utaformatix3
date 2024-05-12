package core.process

fun String.evalFractionOrNull(): Double? {
    toDoubleOrNull()?.let { return it }
    val fractionIndex = indexOf("/").takeIf { it > 0 } ?: return null
    val left = substring(0, fractionIndex).toIntOrNull() ?: return null
    val right = substring(fractionIndex + 1).toIntOrNull() ?: return null
    return left.toDouble() / right
}
