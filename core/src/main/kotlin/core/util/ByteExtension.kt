package core.util

import core.exception.ValueTooLargeException
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

fun MutableList<Byte>.addBlock(
    block: List<Byte>,
    littleEndian: Boolean = true,
    lengthInVariableLength: Boolean = false,
) {
    if (lengthInVariableLength) addIntVariableLengthBigEndian(block.count())
    else addInt(block.count(), littleEndian)
    addAll(block)
}

fun MutableList<Byte>.addList(
    list: List<List<Byte>>,
    littleEndian: Boolean = true,
    lengthInVariableLength: Boolean = false,
) {
    if (lengthInVariableLength) addIntVariableLengthBigEndian(list.count())
    else addInt(list.count(), littleEndian)
    addAll(list.flatten())
}

fun MutableList<Byte>.addListBlock(
    list: List<List<Byte>>,
    littleEndian: Boolean = true,
    lengthInVariableLength: Boolean = false,
) {
    val block = mutableListOf<Byte>().apply {
        if (lengthInVariableLength) addIntVariableLengthBigEndian(list.count())
        else addInt(list.count(), littleEndian)
        addAll(list.flatten())
    }
    addBlock(block, littleEndian, lengthInVariableLength)
}

fun MutableList<Byte>.addInt(int: Int, littleEndian: Boolean = true) {
    val view = DataView(ArrayBuffer(Int.SIZE_BYTES))
    view.setInt32(0, int, littleEndian = littleEndian)
    addArrayBuffer(view.buffer)
}

fun MutableList<Byte>.addIntVariableLengthBigEndian(int: Int) {
    val maximum = 268435455 // 2^28 - 1
    if (int >= maximum) {
        throw ValueTooLargeException(int.toString(), maximum.toString())
    }
    if (int == 0) {
        addAll(listOf(0x00))
        return
    }
    val bytes = mutableListOf<Byte>()
    var rest = int
    while (rest > 0) {
        bytes.add(0, (rest % 128).toByte())
        rest /= 128
    }
    for (i in 0 until bytes.count() - 1) {
        bytes[i] = (bytes[i] + 128).toByte()
    }
    addAll(bytes)
}

fun MutableList<Byte>.addShort(short: Short, littleEndian: Boolean = true) {
    val view = DataView(ArrayBuffer(Short.SIZE_BYTES))
    view.setInt16(0, short, littleEndian = littleEndian)
    addArrayBuffer(view.buffer)
}

fun MutableList<Byte>.addString(string: String, littleEndian: Boolean = true, lengthInVariableLength: Boolean = false) {
    val bytes = string.encodeToByteArray().toList()
    if (lengthInVariableLength) addIntVariableLengthBigEndian(bytes.count())
    else addInt(bytes.count(), littleEndian)
    addAll(bytes)
}

private fun MutableList<Byte>.addArrayBuffer(buffer: ArrayBuffer) {
    val view = Uint8Array(buffer)
    (0 until view.length).forEach {
        add(view[it])
    }
}
