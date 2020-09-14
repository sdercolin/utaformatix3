package util

/**
 * Expose JavaScript Number.toFixed()
 * https://developer.mozilla.org/ja/docs/Web/JavaScript/Reference/Global_Objects/Number/toFixed
 */
fun Double.toFixed(count: Int) = asDynamic().toFixed(count) as String

fun Int.padStartZero(length: Int) = toString().padStart(length, '0')

fun String.linesNotBlank() = this.lines().filter { it.isNotBlank() }
