package process.lyrics

import model.Format
import model.Note
import model.Project
import model.Track
import ui.strings.Strings

data class LyricsReplacementRequest(
    val items: List<Item> = listOf(Item()),
) {

    val isValid get() = items.all { it.isValid }

    fun update(newIndex: Int, updater: Item.() -> Item) = copy(
        items = items.mapIndexed { index, item -> if (index == newIndex) item.updater() else item }
    )

    fun moveUp(index: Int) = move(index, -1)
    fun moveDown(index: Int) = move(index, 1)
    fun add() = copy(items = items + Item())
    fun remove(index: Int) = copy(items = items.filterIndexed { i, _ -> i != index })

    private fun move(index: Int, indexDiff: Int): LyricsReplacementRequest {
        val targetIndex = index + indexDiff
        val items = List(items.size) { i ->
            when (i) {
                index -> items[targetIndex]
                targetIndex -> items[index]
                else -> items[i]
            }
        }
        return copy(items = items)
    }

    data class Item(
        val filterType: FilterType = FilterType.Exact,
        val filter: String = "",
        val matchType: MatchType = MatchType.All,
        val from: String = "",
        val to: String = ""
    ) {
        val isValid
            get() = (filterType == FilterType.None || filter.isNotEmpty()) &&
                (matchType == MatchType.All || from.isNotEmpty())

        fun doReplace(lyric: String): String {
            if (filter(lyric).not()) return lyric
            return when (matchType) {
                MatchType.All -> to
                MatchType.Exact -> lyric.replace(from, to)
                MatchType.Regex -> Regex(from).replace(lyric, to)
            }
        }

        private fun filter(lyric: String): Boolean = when (filterType) {
            FilterType.None -> true
            FilterType.Exact -> lyric == filter
            FilterType.Containing -> lyric.contains(filter)
            FilterType.Prefix -> lyric.startsWith(filter)
            FilterType.Suffix -> lyric.endsWith(filter)
            FilterType.Regex -> lyric.matches(filter.toRegex())
        }
    }

    enum class FilterType(val strings: Strings) {
        None(Strings.LyricsReplacementFilterTypeNone),
        Exact(Strings.LyricsReplacementFilterTypeExact),
        Containing(Strings.LyricsReplacementFilterTypeContaining),
        Prefix(Strings.LyricsReplacementFilterTypePrefix),
        Suffix(Strings.LyricsReplacementFilterTypeSuffix),
        Regex(Strings.LyricsReplacementFilterTypeRegex);

        fun needsFilter(): Boolean = this != None
    }

    enum class MatchType(val strings: Strings) {
        All(Strings.LyricsReplacementMatchTypeAll),
        Exact(Strings.LyricsReplacementMatchTypeExact),
        Regex(Strings.LyricsReplacementMatchTypeRegex);

        fun needsFrom(): Boolean = this != All
    }

    fun doReplace(lyric: String): String = items.fold(lyric) { acc, item ->
        item.doReplace(acc)
    }

    companion object {

        fun getPreset(targetFormat: Format): LyricsReplacementRequest? = when (targetFormat) {
            Format.Ccs -> LyricsReplacementRequest(
                listOf(
                    Item(
                        filterType = FilterType.Exact,
                        filter = "-",
                        matchType = MatchType.All,
                        from = "",
                        to = "ー"
                    )
                )
            )
            Format.Ustx -> LyricsReplacementRequest(
                listOf(
                    Item(
                        filterType = FilterType.Exact,
                        filter = "-",
                        matchType = MatchType.All,
                        from = "",
                        to = "+"
                    )
                )
            )
            else -> null
        }
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
