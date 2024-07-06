package core.util

import core.external.Encoding

fun String.asByteTypedArray() = indices.map { i -> this.asDynamic().charCodeAt(i) as Byte }.toTypedArray()

fun String.encode(encoding: String) = Encoding.convert(asByteTypedArray(), encoding)

fun Array<Byte>.decode(encoding: String) = this.let { bytes ->
    val convertedBytes = Encoding.convert(bytes, "UTF8", encoding)
    ByteArray(convertedBytes.size) { convertedBytes[it] }.decodeToString()
}
