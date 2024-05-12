package core.util

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int32Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

class ArrayBufferReader(private val buffer: ArrayBuffer) {

    var index = 0
        private set

    fun skip(length: Int) {
        index += length
    }

    fun readInt(): Int {
        return Int32Array(buffer.slice(index, index + 4))[0].also {
            index += 4
        }
    }

    fun readBytes(): Uint8Array {
        val length = readInt()
        return Uint8Array(buffer.slice(index, index + length)).also {
            index += length
        }
    }

    fun readString(): String {
        val bytes = readBytes()
        return ByteArray(bytes.byteLength) { bytes[it] }.decodeToString()
    }
}
