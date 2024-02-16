package process.phonemes

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import model.Note
import model.Project
import model.Track
import process.validateNotes

@Serializable
data class PhonemesMappingRequest(
    val mapText: String = "",
) {
    val isValid get() = map.isNotEmpty()

    @Transient
    val map = mapText.lines().mapNotNull { line ->
        if (line.contains("=").not()) return@mapNotNull null
        val from = line.substringBefore("=").trim()
        val to = line.substringAfter("=").trim()
        from to to
    }.sortedWith { a, b ->
        val countA = a.first.split(" ").count()
        val countB = b.first.split(" ").count()
        if (countA != countB) return@sortedWith countB - countA // descending order
        a.first.length - b.first.length
    }

    companion object {

        fun findPreset(name: String) = Presets.find { it.first == name }?.second

        fun getPreset(name: String) = requireNotNull(findPreset(name))

        val Presets: List<Pair<String, PhonemesMappingRequest>> by lazy {
            listOf()
        }
    }
}

fun Project.mapPhonemes(request: PhonemesMappingRequest?) = copy(
    tracks = tracks.map { it.replacePhonemes(request) },
)

fun Track.replacePhonemes(request: PhonemesMappingRequest?) = copy(
    notes = notes.map { note -> note.replacePhonemes(request) }.validateNotes(),
)

fun Note.replacePhonemes(request: PhonemesMappingRequest?): Note {
    if (request == null) return copy(phoneme = null)
    var output = phoneme?.split(" ") ?: return this
    for ((key, value) in request.map) {
        val keySplit = key.split(" ")
        for (i in 0..(output.size - keySplit.size)) {
            val subList = output.subList(i, i + keySplit.size)
            if (subList == keySplit) {
                output = output.subList(0, i) + value.split(" ") +
                    output.subList(i + keySplit.size, output.size)
                // once replaced, the count of output is changed, so we could not proceed the replacement.
                // this means that multiple occurrences of the same phoneme set in one note get replaced only once.
                break
            }
        }
    }
    output = output.filter { it.isNotBlank() }
    return copy(phoneme = output.joinToString(" "))
}
