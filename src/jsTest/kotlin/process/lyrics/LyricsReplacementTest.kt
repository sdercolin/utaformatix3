package process.lyrics

import core.model.Format
import core.model.Note
import core.model.Track
import core.process.lyrics.LyricsReplacementRequest
import core.process.lyrics.replaceLyrics
import kotlin.test.Test
import kotlin.test.assertEquals

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
                    to = "cc",
                ),
            ),
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
                    to = "$1_$2_$1$2",
                ),
            ),
        )
        val result = lyrics.map { request.doReplace(it) }
        val expected = listOf("a_as_aas", "ad", "f_af_faf", "qqw", "f_af_Rr")
        assertEquals(expected, result)
    }

    @Test
    fun testRegex2() {
        val lyrics = listOf("あ", "i あ", "e か")
        val request = LyricsReplacementRequest(
            listOf(
                LyricsReplacementRequest.Item(
                    filterType = LyricsReplacementRequest.FilterType.None,
                    filter = "",
                    matchType = LyricsReplacementRequest.MatchType.Regex,
                    from = """^. (.+)$""",
                    to = "$1",
                ),
            ),
        )
        val result = lyrics.map { request.doReplace(it) }
        val expected = listOf("あ", "あ", "か")
        assertEquals(expected, result)
    }

    @Test
    fun testRemoveNotes() {
        val track = Track(
            id = 0,
            notes = listOf(
                Note(0, 60, "a", 0L, 1L),
                Note(1, 60, "a R", 1L, 2L),
                Note(2, 60, "a", 2L, 3L),
                Note(3, 60, "R", 3L, 4L),
                Note(4, 60, "a", 4L, 5L),
            ),
            name = "",
            pitch = null,
        )
        val request = requireNotNull(LyricsReplacementRequest.getPreset(Format.Ust, Format.Ust))
        val result = track.replaceLyrics(request)
        val expected = listOf(
            Note(0, 60, "a", 0L, 1L),
            Note(1, 60, "a", 2L, 3L),
            Note(2, 60, "a", 4L, 5L),
        )
        assertEquals(expected, result.notes)
    }
}
