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
                    name = "Japanese (SynthV to VOCALOID)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/SynthV JA to Vocaloid JA.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = Format.vocaloidFormats,
                    targetFormats = listOf(Format.Svp),
                    name = "Japanese (VOCALOID to SynthV)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/Vocaloid JA to SynthV JA.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Svp),
                    targetFormats = Format.vocaloidFormats,
                    name = "English (SynthV to VOCALOID)",
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
                        require("./texts/SynthV EN to OpenUtau X-SAMPA EN.txt").default as String,
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
                    name = "English (VOCALOID to SynthV)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/Vocaloid EN to SynthV EN.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = Format.vocaloidFormats,
                    targetFormats = listOf(Format.Ustx),
                    name = "English: ARPAsing (VOCALOID to OpenUtau)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/Vocaloid EN to OpenUtau ARPAsing.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = Format.vocaloidFormats,
                    targetFormats = listOf(Format.Ustx),
                    name = "English: X-SAMPA (VOCALOID to OpenUtau)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/Vocaloid EN to OpenUtau X-SAMPA EN.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = Format.vocaloidFormats,
                    targetFormats = listOf(Format.Ustx),
                    name = "English: VCCV (VOCALOID to OpenUtau)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/Vocaloid EN to OpenUtau VCCV.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Ustx),
                    targetFormats = listOf(Format.Svp),
                    name = "English: ARPAsing (OpenUtau to SynthV)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/OpenUtau ARPAsing to SynthV EN.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Ustx),
                    targetFormats = Format.vocaloidFormats,
                    name = "English: ARPAsing (OpenUtau to VOCALOID)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/OpenUtau ARPAsing to Vocaloid EN.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Ustx),
                    targetFormats = listOf(Format.Ustx),
                    name = "English: ARPAsing to X-SAMPA (OpenUtau to OpenUtau)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/OpenUtau ARPAsing to OpenUtau X-SAMPA EN.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Ustx),
                    targetFormats = listOf(Format.Ustx),
                    name = "English: ARPAsing to VCCV (OpenUtau to OpenUtau)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/OpenUtau ARPAsing to OpenUtau VCCV.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Ustx),
                    targetFormats = listOf(Format.Svp),
                    name = "English: X-SAMPA (OpenUtau to SynthV)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/OpenUtau X-SAMPA EN to SynthV EN.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Ustx),
                    targetFormats = Format.vocaloidFormats,
                    name = "English: X-SAMPA (OpenUtau to VOCALOID)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/OpenUtau X-SAMPA EN to Vocaloid EN.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Ustx),
                    targetFormats = listOf(Format.Ustx),
                    name = "English: X-SAMPA to ARPAsing (OpenUtau to OpenUtau)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/OpenUtau X-SAMPA EN to OpenUtau ARPAsing.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Ustx),
                    targetFormats = listOf(Format.Ustx),
                    name = "English: X-SAMPA to VCCV (OpenUtau to OpenUtau)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/OpenUtau X-SAMPA EN to OpenUtau VCCV.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Ustx),
                    targetFormats = listOf(Format.Svp),
                    name = "English: VCCV (OpenUtau to SynthV)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/OpenUtau VCCV to SynthV EN.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Ustx),
                    targetFormats = Format.vocaloidFormats,
                    name = "English: VCCV (OpenUtau to VOCALOID)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/OpenUtau VCCV to Vocaloid EN.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Ustx),
                    targetFormats = listOf(Format.Ustx),
                    name = "English: VCCV to ARPAsing (OpenUtau to OpenUtau)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/OpenUtau VCCV to OpenUtau ARPAsing.txt").default as String,
                    ),
                ),
                PhonemesMappingPreset(
                    sourceFormats = listOf(Format.Ustx),
                    targetFormats = listOf(Format.Ustx),
                    name = "English: VCCV to X-SAMPA (OpenUtau to OpenUtau)",
                    phonemesMap = PhonemesMappingRequest(
                        require("./texts/OpenUtau VCCV to OpenUtau X-SAMPA EN.txt").default as String,
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
    val rawInput = phoneme?.split(" ") ?: return this
    val input = rawInput.map { it to true }.toMutableList() // boolean represents the phoneme is not converted yet
    var output = mutableListOf<Pair<String, Int>>() // int represents index from input phoneme list
    for ((key, value) in request.map) {
        val keySplit = key.split(" ")
        for (i in 0..(input.size - keySplit.size)) {
            val subList = input.subList(i, i + keySplit.size)
            if (subList.map { it.first } == keySplit && subList.map { it.second }.all { it }) {
                output += value to i
                for (j in 0 until subList.size) {
                    subList[j] = subList[j].first to false
                }
            }
        }
    }
    output += input.mapIndexedNotNull { index, pair -> if (pair.second) pair.first to index else null }
    output = output.filter { it.first.isNotBlank() }.sortedBy { it.second }.toMutableList()
    return copy(phoneme = output.joinToString(" ") { it.first })
}
