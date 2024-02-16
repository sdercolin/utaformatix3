package process.phonemes

import external.require
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import model.Format
import model.Note
import model.PhonemesMappingPreset
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
        if (countA != countB) return@sortedWith countB - countA // descending order of phonemes count
        b.first.length - a.first.length // descending order of the length
    }

    companion object {

        fun findPreset(name: String) = Presets.find { it.name == name }?.phonemesMap

        fun getPreset(name: String) = requireNotNull(findPreset(name))

        val Presets: List<PhonemesMappingPreset> by lazy {
            listOf(
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Svp),
                    targetFormats = Format.vocaloidFormats,
                    name = "Japanese (SynthV to Vocaloid)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/SynthV JA to Vocaloid JA.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = Format.vocaloidFormats,
                    targetFormats = listOf(Format.Svp),
                    name = "Japanese (Vocaloid to SynthV)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/Vocaloid JA to SynthV JA.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Svp),
                    targetFormats = Format.vocaloidFormats,
                    name = "English (SynthV to Vocaloid)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/SynthV EN to Vocaloid EN.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Svp),
                    targetFormats = listOf(Format.Ustx),
                    name = "English: ARPAsing (SynthV to OpenUtau)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/SynthV EN to OpenUtau ARPAsing.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Svp),
                    targetFormats = listOf(Format.Ustx),
                    name = "English: X-SAMPA (SynthV to OpenUtau)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/SynthV EN to OpenUtau X-SAMPA.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Svp),
                    targetFormats = listOf(Format.Ustx),
                    name = "English: VCCV (SynthV to OpenUtau)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/SynthV EN to OpenUtau VCCV.txt").default as String,
                    ),
                ),
            )
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
