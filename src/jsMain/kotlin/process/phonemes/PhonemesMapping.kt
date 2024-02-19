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
                PhonemesMappingPreset(
                    sourceFormats = Format.vocaloidFormats,
                    targetFormats = listOf(Format.Svp),
                    name = "English (Vocaloid to SynthV)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/Vocaloid EN to SynthV EN.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = Format.vocaloidFormats,
                    targetFormats = listOf(Format.Ustx),
                    name = "English: ARPAsing (Vocaloid to OpenUtau)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/Vocaloid EN to OpenUtau ARPAsing.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = Format.vocaloidFormats,
                    targetFormats = listOf(Format.Ustx),
                    name = "English: X-SAMPA (Vocaloid to OpenUtau)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/Vocaloid EN to OpenUtau X-SAMPA.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = Format.vocaloidFormats,
                    targetFormats = listOf(Format.Ustx),
                    name = "English: VCCV (Vocaloid to OpenUtau)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/Vocaloid EN to OpenUtau VCCV.txt").default as String,
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
    var rawInput = phoneme?.split(" ") ?: return this
    var input = rawInput.map{it to true}.toMutableList() // boolean represents the phoneme is not converted yet
    var output = listOf<Pair<String,Int>>() // int represents index from input phoneme list
    for ((key, value) in request.map) {
        val keySplit = key.split(" ")
        for (i in 0..(input.size - keySplit.size)) {
            val subList = input.subList(i, i + keySplit.size)
            if (subList.map{it.first} == keySplit && subList.map{it.second}.all { it }) {
                output += Pair(value, i)
                for (j in 0..subList.size-1) {
                    subList[j] = Pair(subList[j].first, false)
                }
            }
        }
    }
    output += input.mapIndexed { index, pair -> if (pair.second) Pair(pair.first, index) else Pair("",0)}
    output = output.filter { it.first.isNotBlank() }.sortedBy { it.second }
    return copy(phoneme = output.map{it.first}.joinToString(" "))
}
