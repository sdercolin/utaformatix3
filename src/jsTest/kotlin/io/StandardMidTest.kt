package io

import core.io.StandardMid
import core.model.ImportParams
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.set
import org.w3c.files.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(DelicateCoroutinesApi::class)
class StandardMidTest {
    private fun fileFromBase64(base64: String): File {
        val binary = window.atob(base64)
        val bytes = Uint8Array(binary.length)
        for (i in binary.indices) bytes[i] = binary[i].code.toByte()
        return File(arrayOf(bytes), "test.mid")
    }

    // Single track; the first tempo (120 BPM) and time signature (3/4) are at tick 1920, followed by one note.
    @Test
    fun testParseWithoutTempoAndTimeSignatureAtStart() =
        GlobalScope.promise {
            val file = fileFromBase64("TVRoZAAAAAYAAAABAeBNVHJrAAAAHY8A/1EDB6EgAP9YBAMCGAgAkDxkg2CAPAAA/y8A")
            val project = StandardMid.parse(file, ImportParams())
            val notes = project.tracks.first().notes
            assertEquals(1, notes.size)
            assertEquals(listOf(0L, 1920L), project.tempos.map { it.tickPosition })
            assertEquals(listOf(0 to 4, 1 to 3), project.timeSignatures.map { it.measurePosition to it.numerator })
        }
}
