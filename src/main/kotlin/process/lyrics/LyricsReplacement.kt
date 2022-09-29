package process.lyrics

import kotlinx.serialization.Serializable
import model.Format
import model.Note
import model.Project
import model.Track
import process.validateNotes
import util.runIf

@Serializable
data class LyricsReplacementRequest(
    val items: List<Item> = listOf(Item()),
) {

    val isValid get() = items.all { it.isValid }

    fun update(editIndex: Int, updater: Item.() -> Item) = copy(
        items = items.mapIndexed { index, item -> item.runIf(index == editIndex) { updater() } },
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

    @Serializable
    data class Item(
        val filterType: FilterType = FilterType.Exact,
        val filter: String = "",
        val matchType: MatchType = MatchType.All,
        val from: String = "",
        val to: String = "",
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

    enum class FilterType {
        None,
        Exact,
        Containing,
        Prefix,
        Suffix,
        Regex;

        fun needsFilter(): Boolean = this != None
    }

    enum class MatchType {
        All,
        Exact,
        Regex;

        fun needsFrom(): Boolean = this != All
    }

    fun doReplace(lyric: String): String = items.fold(lyric) { acc, item ->
        item.doReplace(acc)
    }

    companion object {

        fun getPreset(fromFormat: Format, toFormat: Format): LyricsReplacementRequest? {
            val items = mutableListOf<Item>()

            when (fromFormat) {
                Format.Ust -> items.add(
                    Item(
                        filterType = FilterType.Suffix,
                        filter = "R",
                        matchType = MatchType.All,
                        from = "",
                        to = "",
                    ),
                )

                Format.Ccs -> items.add(
                    Item(
                        filterType = FilterType.Exact,
                        filter = "ー",
                        matchType = MatchType.All,
                        from = "",
                        to = "-",
                    ),
                )

                Format.Ustx -> items.add(
                    Item(
                        filterType = FilterType.Exact,
                        filter = "+",
                        matchType = MatchType.All,
                        from = "",
                        to = "-",
                    ),
                )
                else -> Unit
            }

            when (toFormat) {
                Format.Ccs -> items.add(
                    Item(
                        filterType = FilterType.Exact,
                        filter = "-",
                        matchType = MatchType.All,
                        from = "",
                        to = "ー",
                    ),
                )

                Format.Ustx -> items.add(
                    Item(
                        filterType = FilterType.Exact,
                        filter = "-",
                        matchType = MatchType.All,
                        from = "",
                        to = "+",
                    ),
                )
                else -> Unit
            }

            return LyricsReplacementRequest(items).takeIf { it.items.isNotEmpty() }
        }
    }
}

fun Project.replaceLyrics(request: LyricsReplacementRequest) = copy(
    tracks = tracks.map { it.replaceLyrics(request) },
)

fun Track.replaceLyrics(request: LyricsReplacementRequest) = copy(
    notes = notes.mapNotNull { note -> note.replaceLyrics(request).takeIf { it.lyric.isNotEmpty() } }
        .validateNotes(),
)

private fun Note.replaceLyrics(request: LyricsReplacementRequest) = copy(
    lyric = request.doReplace(lyric),
)
