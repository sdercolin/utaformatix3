package process.pitch

import kotlin.test.Test
import kotlin.test.assertEquals
import process.lyrics.LyricsReplacementRequest

class LyricsReplacementTest {

    @Test
    fun testSimple() {
        val lyrics = listOf("as", "ad", "ff", "qqw")
        val request = LyricsReplacementRequest(
            listOf(
                LyricsReplacementRequest.Item(
                    filterType = LyricsReplacementRequest.FilterType.Exact,
                    filter = "ff",
                    matchType = LyricsReplacementRequest.MatchType.All,
                    from = "",
                    to = "cc"
                )
            )
        )
        val result = lyrics.map { request.doReplace(it) }
        val expected = listOf("as", "ad", "cc", "qqw")
        assertEquals(expected, result)
    }

    @Test
    fun testRegex() {
        val lyrics = listOf("a_as_R", "ad", "f_af_R", "qqw", "f_af_Rr")
        val request = LyricsReplacementRequest(
            listOf(
                LyricsReplacementRequest.Item(
                    filterType = LyricsReplacementRequest.FilterType.Regex,
                    filter = """\w+_\w+_R""",
                    matchType = LyricsReplacementRequest.MatchType.Regex,
                    from = """(\w+)_(\w+)_R""",
                    to = "$1_$2_$1$2"
                )
            )
        )
        val result = lyrics.map { request.doReplace(it) }
        val expected = listOf("a_as_aas", "ad", "f_af_faf", "qqw", "f_af_Rr")
        assertEquals(expected, result)
    }
}
