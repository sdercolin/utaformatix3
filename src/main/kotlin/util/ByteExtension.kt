package util

import kotlinx.serialization.toUtf8Bytes
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int32Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

fun MutableList<Byte>.addBlock(block: List<Byte>) {
    addInt(block.count())
    addAll(block)
}

fun MutableList<Byte>.addList(list: List<List<Byte>>) {
    addInt(list.count())
    addAll(list.flatten())
}

fun MutableList<Byte>.addListBlock(list: List<List<Byte>>) {
    val block = mutableListOf<Byte>().apply {
        addInt(list.count())
        addAll(list.flatten())
    }
    addBlock(block)
}

fun MutableList<Byte>.addInt(int: Int) {
    val view = Int32Array(ArrayBuffer(Int.SIZE_BYTES))
    view[0] = int
    addArrayBuffer(view.buffer)
}

fun MutableList<Byte>.addString(string: String) {
    val bytes = string.toUtf8Bytes().toList()
    addInt(bytes.count())
    addAll(bytes)
}

private fun MutableList<Byte>.addArrayBuffer(buffer: ArrayBuffer) {
    val view = Uint8Array(buffer)
    (0 until view.length).forEach {
        add(view[it])
    }
}
