package process.phonemes

import model.Note
import kotlin.test.Test
import kotlin.test.assertEquals

class PhonemesMappingTest {

    private val request = PhonemesMappingRequest(
        mapText = """
            a=A
            b=B
            c a=C' A
            c=C
            d c a=DC' A
            d c=DC
            sil=
            s=S
            sh=SH
            effff=EF
            effff d=EF D
            Q=a
            OI=Q
        """.trimIndent(),
    )

    private fun createNote(phoneme: String) = Note(
        id = 0,
        key = 60,
        lyric = "",
        tickOn = 0L,
        tickOff = 480L,
        phoneme = phoneme,
    )

    @Test
    fun testNoMatch() {
        val note = createNote("l o")
        val actual = note.replacePhonemes(request).phoneme
        assertEquals("l o", actual)
    }

    @Test
    fun testSingleMatch() {
        val note = createNote("b")
        val actual = note.replacePhonemes(request).phoneme
        assertEquals("B", actual)
    }

    @Test
    fun testSingleInMultipleMatch() {
        val note = createNote("l a m b n")
        val actual = note.replacePhonemes(request).phoneme
        assertEquals("l A m B n", actual)
    }

    @Test
    fun testMultipleMatch() {
        val note = createNote("c a")
        val actual = note.replacePhonemes(request).phoneme
        assertEquals("C' A", actual)
    }

    @Test
    fun testMultipleInMultipleMatch() {
        val note = createNote("d c a m d c")
        val actual = note.replacePhonemes(request).phoneme
        assertEquals("DC' A m DC", actual)
    }

    @Test
    fun testRepeatedMultipleMatch() {
        val note = createNote("d c a d c a")
        val actual = note.replacePhonemes(request).phoneme
        assertEquals("DC' A DC' A", actual)
    }

    @Test
    fun testMutedPhoneme() {
        val note = createNote("sil a")
        val actual = note.replacePhonemes(request).phoneme
        assertEquals("A", actual)
    }

    @Test
    fun testSortLength() {
        val note = createNote("sh")
        val actual = note.replacePhonemes(request).phoneme
        assertEquals("SH", actual)
    }

    @Test
    fun testSortMultiLength() {
        val note = createNote("effff d c a")
        val actual = note.replacePhonemes(request).phoneme
        assertEquals("EF DC' A", actual)
    }

    @Test
    fun testSameTextDifferentPhoneme() {
        val note = createNote("OI")
        val actual = note.replacePhonemes(request).phoneme
        assertEquals("Q", actual)
    }
}
