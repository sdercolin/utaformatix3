package core.io

import core.exception.IllegalFileException
import core.external.ValueTree
import core.model.ExportResult
import core.model.FeatureConfig
import core.model.ImportParams
import core.util.readAsArrayBuffer
import org.w3c.files.File
import core.external.ValueTreeTs
import core.model.Format
import core.model.ImportWarning
import core.model.Note
import core.model.TICKS_IN_BEAT
import core.model.Tempo
import core.model.TimeSignature
import core.model.Track
import org.khronos.webgl.Uint8Array
import core.util.nameWithoutExtension
import kotlin.math.floor

object Tssln {
    suspend fun parse(file: File, params: ImportParams): core.model.Project {
        val blob = file.readAsArrayBuffer()
        val valueTree = ValueTreeTs.parseValueTree(
            Uint8Array(blob),
        )

        if (valueTree.type != "TSSolution") {
            throw IllegalFileException.IllegalTsslnFile()
        }

        val trackTrees = valueTree.children.first { it.type == "Tracks" }.children.filter { it.attributes.Type == 0 }

        val masterTrackResult = parseMasterTrack(trackTrees.first())


        val tracks = parseTracks(trackTrees, params)


        val warnings = mutableListOf<ImportWarning>()

        return core.model.Project(
            format = format,
            inputFiles = listOf(file),
            name = file.nameWithoutExtension,
            tracks = tracks,
            tempos = masterTrackResult.first,
            timeSignatures = masterTrackResult.second,
            measurePrefix = 1,
            importWarnings = warnings,
        )
    }

    private fun parseTracks(trackTrees: List<ValueTree>, params: ImportParams): List<core.model.Track> {
        return trackTrees.mapIndexed { trackIndex, trackTree ->
            val trackName = trackTree.attributes.Name as String
            val pluginData = trackTree.attributes.PluginData as Uint8Array
            val pluginDataNode = ValueTreeTs.parseValueTree(pluginData)

            if (pluginDataNode.type != "StateInformation") {
                throw IllegalFileException.IllegalTsslnFile()
            }

            val songNode = pluginDataNode.children.first { it.type == "Song" }
            val scoreNode = songNode.children.first { it.type == "Score" }

            val notes = mutableListOf<Note>()

            for ((noteIndex, noteNode) in scoreNode.children.withIndex()) {
                if (noteNode.type != "Note") {
                    continue
                }

                val pitchStep = noteNode.attributes.PitchStep as Int
                val pitchOctave = noteNode.attributes.PitchOctave as Int
                val rawLyric = noteNode.attributes.Lyric as String
                val lyric = phonemePartPattern.replace(rawLyric, "")

                val phoneme = (noteNode.attributes.Phoneme as String).replace(",", "")

                val tickOn = (noteNode.attributes.Clock as Int)
                val tickOff = tickOn + (noteNode.attributes.Duration as Int)

                notes.add(
                    Note(
                        id = noteIndex,
                        key = pitchOctave * 12 + pitchStep + 12,
                        lyric = lyric,
                        phoneme = phoneme,
                        tickOn = (tickOn / TICK_RATE).toLong(),
                        tickOff = (tickOff / TICK_RATE).toLong(),
                    ),
                )

            }

            Track(
                id = trackIndex,
                name = trackName,
                notes = notes,
            )
        }
    }

    suspend fun generate(project: core.model.Project, features: List<FeatureConfig>): ExportResult {
        throw NotImplementedError("Not implemented")
    }

    private fun parseMasterTrack(trackTree: ValueTree): Pair<List<Tempo>, List<TimeSignature>> {
        val pluginData = trackTree.attributes.PluginData as Uint8Array
        val pluginDataNode = ValueTreeTs.parseValueTree(pluginData)
        if (pluginDataNode.type != "StateInformation") {
            throw IllegalFileException.IllegalTsslnFile()
        }

        val songNode = pluginDataNode.children.first { it.type == "Song" }

        val tempoNode = songNode.children.first { it.type == "Tempo" }

        val tempos = tempoNode.children.map {
            Tempo(
                ((it.attributes.Clock as Int) / TICK_RATE).toLong(),
                it.attributes.Tempo as Double,
            )
        }

        val timeSignaturesNode = songNode.children.first { it.type == "Beat" }

        val timeSignatures = mutableListOf<TimeSignature>()

        var currentBeatIndex = 0
        var currentMeasureIndex = 0

        for (timeSignatureNode in timeSignaturesNode.children.sortedBy {
            it.attributes.Clock as Int
        }) {
            val numerator = timeSignatureNode.attributes.Beats as Int
            val denominator = timeSignatureNode.attributes.BeatType as Int
            val clock = timeSignatureNode.attributes.Clock as Int
            val beatIndex = floor(clock / TICK_RATE / TICKS_IN_BEAT).toInt()

            val beatNum = beatIndex - currentBeatIndex
            val beatLength = numerator / denominator * 4

            if (beatNum < 0) {
                throw IllegalFileException.IllegalTsslnFile()
            }

            val measureIndex = currentMeasureIndex + beatNum / beatLength

            currentBeatIndex = beatIndex
            currentMeasureIndex = measureIndex

            timeSignatures.add(
                TimeSignature(
                    measureIndex,
                    numerator,
                    denominator,
                ),
            )
        }

        return Pair(tempos, timeSignatures)
    }

    private val TICK_RATE = 2.0

    private val phonemePartPattern = Regex("""\[.+?\]""")

    private val format = Format.Tssln
}
