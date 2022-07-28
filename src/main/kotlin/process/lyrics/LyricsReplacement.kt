package process.lyrics

import model.Note
import model.Project
import model.Track

data class LyricsReplacementRequest(
    val items: List<Item>
) {

    data class Item(
        val filterType: FilterType,
        val filter: String,
        val matchType: MatchType,
        val from: String,
        val to: String
    ) {
        fun doReplace(lyric: String): String {
            if (filter(lyric).not()) return lyric
            return when (matchType) {
                MatchType.All -> to
                MatchType.Exact -> lyric.replace(from, to)
                MatchType.Regex -> Regex(from).replace(lyric, to)
            }
        }

        private fun filter(lyric: String): Boolean = when (filterType) {
            FilterType.Exact -> lyric == filter
            FilterType.Containing -> lyric.contains(filter)
            FilterType.StartingWith -> lyric.startsWith(filter)
            FilterType.EndingWith -> lyric.endsWith(filter)
            FilterType.Regex -> lyric.matches(filter.toRegex())
        }
    }

    enum class FilterType {
        Exact,
        Containing,
        StartingWith,
        EndingWith,
        Regex
    }

    enum class MatchType {
        All,
        Exact,
        Regex
    }

    fun doReplace(lyric: String): String = items.fold(lyric) { acc, item ->
        item.doReplace(acc)
    }
}

fun Project.replaceLyrics(request: LyricsReplacementRequest) = copy(
    tracks = tracks.map { it.replaceLyrics(request) }
)

private fun Track.replaceLyrics(request: LyricsReplacementRequest) = copy(
    notes = notes.map { it.replaceLyrics(request) }
)

private fun Note.replaceLyrics(request: LyricsReplacementRequest) = copy(
    lyric = request.doReplace(lyric)
)
