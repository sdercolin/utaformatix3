package core.io

import core.exception.IllegalFileException
import core.external.Resources
import core.external.ValueTree
import core.external.ValueTreeTs
import core.external.createValueTree
import core.external.structuredClone
import core.external.toVariantType
import core.model.ExportResult
import core.model.FeatureConfig
import core.model.Format
import core.model.ImportParams
import core.model.ImportWarning
import core.model.Note
import core.model.Pitch
import core.model.Project
import core.model.TICKS_IN_BEAT
import core.model.Tempo
import core.model.TimeSignature
import core.model.Track
import core.process.interpolateLinear
import core.util.nameWithoutExtension
import core.util.readAsArrayBuffer
import org.khronos.webgl.Uint8Array
import org.w3c.files.Blob
import org.w3c.files.File
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log
import kotlin.math.log2
import kotlin.math.pow

object Tssln {
    suspend fun parse(file: File, params: ImportParams): Project {
        val blob = file.readAsArrayBuffer()
        val valueTree = ValueTreeTs.parseValueTree(
            Uint8Array(blob),
        )

        if (valueTree.type != "TSSolution") {
            throw IllegalFileException.IllegalTsslnFile()
        }

        val trackTrees =
            valueTree.children.first { it.type == "Tracks" }.children.filter { it.attributes.Type.value == 0 }

        val masterTrackResult = parseMasterTrack(trackTrees.first())
        val tempos = masterTrackResult.first
        val timeSignatures = masterTrackResult.second

        val tracks = parseTracks(trackTrees, params)

        val warnings = mutableListOf<ImportWarning>()

        return Project(
            format = format,
            inputFiles = listOf(file),
            name = file.nameWithoutExtension,
            tracks = tracks,
            tempos = tempos,
            timeSignatures = timeSignatures,
            measurePrefix = 1,
            importWarnings = warnings,
        )
    }

    private fun parseTracks(trackTrees: List<ValueTree>, params: ImportParams): List<Track> {
        return trackTrees.mapIndexed { trackIndex, trackTree ->
            val trackName = trackTree.attributes.Name.value as String
            val pluginData = trackTree.attributes.PluginData.value as Uint8Array
            val pluginDataTree = ValueTreeTs.parseValueTree(pluginData)

            if (pluginDataTree.type != "StateInformation") {
                throw IllegalFileException.IllegalTsslnFile()
            }

            val songTree = pluginDataTree.children.first { it.type == "Song" }
            val scoreTree = songTree.children.first { it.type == "Score" }

            val notes = mutableListOf<Note>()

            for ((noteIndex, noteTree) in scoreTree.children.withIndex()) {
                if (noteTree.type != "Note") {
                    continue
                }

                val pitchStep = noteTree.attributes.PitchStep.value as Int
                val pitchOctave = noteTree.attributes.PitchOctave.value as Int
                val rawLyric = (noteTree.attributes.Lyric.value as String)
                val lyric = phonemePartPattern.replace(rawLyric, "").takeUnless { it.isBlank() } ?: params.defaultLyric

                val phoneme = (noteTree.attributes.Phoneme.value as String).replace(",", "")

                val tickOn = (noteTree.attributes.Clock.value as Int)
                val tickOff = tickOn + (noteTree.attributes.Duration.value as Int)

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

            val pitch = if (!params.simpleImport) {
                parsePitch(pluginDataTree)
            } else {
                null
            }

            Track(
                id = trackIndex,
                name = trackName,
                notes = notes,
                pitch = if (pitch != null) Pitch(pitch, isAbsolute = true) else null,
            )
        }
    }

    private fun parsePitch(trackTree: ValueTree): List<Pair<Long, Double?>> {
        val parameterTree = trackTree.children.first { it.type == "Parameter" }
        val pitchTree = parameterTree.children.find { it.type == "LogF0CTick" }
        if (pitchTree == null) {
            return emptyList()
        }

        var currentIndex = 0
        val pitchValues = pitchTree.children.flatMap {
            val maybeIndex = it.attributes.Index?.value as? Int
            if (maybeIndex != null) {
                currentIndex = maybeIndex
            }

            val repeat = (it.attributes.Repeat?.value ?: 1) as Int
            val pitchLogFrq = it.attributes.Value.value as Double
            val pitchFrq = exp(pitchLogFrq)

            val pitchAsMidi = log2(pitchFrq / 440.0) * 12 + 69

            val values = List(repeat) { i ->
                ((currentIndex + i) / PITCH_TICK_RATE).toLong() to pitchAsMidi
            }

            currentIndex += repeat

            values
        }

        return pitchValues
    }

    private fun parseMasterTrack(trackTree: ValueTree): Pair<List<Tempo>, List<TimeSignature>> {
        val pluginData = trackTree.attributes.PluginData.value as Uint8Array
        val pluginDataTree = ValueTreeTs.parseValueTree(pluginData)
        if (pluginDataTree.type != "StateInformation") {
            throw IllegalFileException.IllegalTsslnFile()
        }

        val songTree = pluginDataTree.children.first { it.type == "Song" }

        val tempoTree = songTree.children.first { it.type == "Tempo" }

        val tempos = tempoTree.children.map {
            Tempo(
                ((it.attributes.Clock.value as Int) / TICK_RATE).toLong(),
                it.attributes.Tempo.value as Double,
            )
        }

        val timeSignaturesTree = songTree.children.first { it.type == "Beat" }

        val timeSignatures = mutableListOf<TimeSignature>()

        var currentBeatIndex = 0
        var currentMeasureIndex = 0
        var beatLength = 4.0

        for (timeSignatureTree in timeSignaturesTree.children.sortedBy {
            it.attributes.Clock.value as Int
        }) {
            val numerator = timeSignatureTree.attributes.Beats.value as Int
            val denominator = timeSignatureTree.attributes.BeatType.value as Int
            val clock = timeSignatureTree.attributes.Clock.value as Int
            val beatIndex = floor(clock / TICK_RATE / TICKS_IN_BEAT).toInt()

            val beatNum = beatIndex - currentBeatIndex

            if (beatNum < 0) {
                throw IllegalFileException.IllegalTsslnFile()
            }

            val measureIndex = (currentMeasureIndex + beatNum / beatLength).toInt()
            beatLength = numerator.toDouble() / denominator * 4

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

    fun generate(project: Project, features: List<FeatureConfig>): ExportResult {
        val baseJson = Resources.tsslnTemplate

        val baseTree = ValueTreeTs.parseValueTree(Uint8Array(baseJson))

        val tracksTree = baseTree.children.first { it.type == "Tracks" }
        val baseTrack = tracksTree.children.first { it.attributes.Type.value == 0 }

        val tracks = generateTracks(baseTrack, project, features)
        tracksTree.children = tracks.toTypedArray()

        val result = ValueTreeTs.dumpValueTree(baseTree)

        return ExportResult(
            Blob(arrayOf(result)),
            project.name + ".tssln",
            listOf(),
        )
    }

    private fun generateTracks(baseTrack: ValueTree, project: Project, features: List<FeatureConfig>): List<ValueTree> {
        return project.tracks.map { track ->
            val trackTree = structuredClone(baseTrack)
            trackTree.attributes.Name = (track.name).toVariantType()

            val pluginData = trackTree.attributes.PluginData.value as Uint8Array
            val pluginDataTree = ValueTreeTs.parseValueTree(pluginData)

            val songTree = pluginDataTree.children.first { it.type == "Song" }

            val tempoTree = songTree.children.first { it.type == "Tempo" }
            val timeSignaturesTree = songTree.children.first { it.type == "Beat" }

            tempoTree.children = (generateTempos(project.tempos)).toTypedArray()
            timeSignaturesTree.children = (generateTimeSignatures(project.timeSignatures)).toTypedArray()

            val scoreTree = songTree.children.first { it.type == "Score" }
            val notes = generateNotes(track)
            val baseChildren = structuredClone(scoreTree.children)

            val newChildren = baseChildren.toMutableList()
            newChildren.addAll(notes)

            scoreTree.children = newChildren.toTypedArray()

            if (features.contains(FeatureConfig.ConvertPitch) && track.pitch != null) {
                val pitchTree = createValueTree()
                pitchTree.type = "LogF0CTick"

                var prevIndex = 0
                var prevValue = 0.0

                val pitchDataTrees = track.pitch.data.flatMapIndexed { dataIndex, pitch ->
                    val (tick, midi) = pitch
                    if (midi == null) {
                        return@flatMapIndexed listOf()
                    }
                    val pitchFrq = (440.0 * 2.0.pow((midi - 69) / 12.0))
                    val pitchLogFrq = ln(pitchFrq)
                    val index = (tick * PITCH_TICK_RATE).toInt()

                    val pitchDataTrees = if (dataIndex == 0) {
                        val pitchDataTree = createValueTree()
                        pitchDataTree.type = "Data"
                        pitchDataTree.attributes.Index = index.toVariantType()
                        pitchDataTree.attributes.Repeat = 1.toVariantType()
                        pitchDataTree.attributes.Value = pitchLogFrq.toVariantType()

                        listOf(pitchDataTree)
                    } else {
                        val lerpedPitch = listOf(
                            Pair(prevIndex.toLong(), prevValue),
                            Pair(index.toLong(), pitchLogFrq),
                        ).interpolateLinear(1)
                        if (lerpedPitch == null) {
                            return@flatMapIndexed listOf()
                        }
                        lerpedPitch.subList(1, lerpedPitch.size - 1).map {
                                val pitchDataTree = createValueTree()
                                pitchDataTree.type = "Data"
                                pitchDataTree.attributes.Value = it.second.toVariantType()
                                pitchDataTree
                            }
                    }

                    prevIndex = index
                    prevValue = pitchLogFrq

                    pitchDataTrees
                }

                pitchTree.children = pitchDataTrees.toTypedArray()

                val parameterTree = pluginDataTree.children.first { it.type == "Parameter" }
                parameterTree.children = arrayOf(pitchTree)
            }

            trackTree.attributes.PluginData = (ValueTreeTs.dumpValueTree(pluginDataTree)).toVariantType()

            trackTree
        }
    }

    private fun generateTempos(tempos: List<Tempo>): List<ValueTree> {
        return tempos.map {
            val tempoTree = createValueTree()
            tempoTree.type = "Sound"
            val attributes: dynamic = js("{}")
            attributes.Clock = ((it.tickPosition * TICK_RATE).toInt()).toVariantType()
            attributes.Tempo = (it.bpm).toVariantType()
            tempoTree.attributes = attributes

            tempoTree
        }
    }

    private fun generateTimeSignatures(timeSignatures: List<TimeSignature>): List<ValueTree> {
        val timeSignatureTrees = mutableListOf<ValueTree>()

        var currentBeat = 0
        var currentMeasure = 0
        var beatLength = 4.0

        for (timeSignature in timeSignatures) {
            val timeSignatureTree = createValueTree()
            timeSignatureTree.type = "Time"
            val attributes: dynamic = js("{}")

            val numMeasure = timeSignature.measurePosition - currentMeasure
            val numBeat = numMeasure * beatLength
            currentBeat += numBeat.toInt()

            beatLength = timeSignature.numerator.toDouble() / timeSignature.denominator * 4
            currentMeasure = timeSignature.measurePosition

            attributes.Clock = (currentBeat * TICKS_IN_BEAT * TICK_RATE).toInt().toVariantType()
            attributes.Beats = timeSignature.numerator.toVariantType()
            attributes.BeatType = timeSignature.denominator.toVariantType()

            timeSignatureTree.attributes = attributes

            timeSignatureTrees.add(timeSignatureTree)
        }

        return timeSignatureTrees
    }

    private fun generateNotes(track: Track): List<ValueTree> {
        return track.notes.map {
            val noteTree = createValueTree()
            noteTree.type = "Note"
            val attributes: dynamic = js("{}")
            attributes.PitchStep = (it.key % 12).toVariantType()
            attributes.PitchOctave = (it.key / 12 - 1).toVariantType()
            attributes.Lyric = it.lyric.toVariantType()
            attributes.Phoneme = (it.phoneme ?: "").toVariantType()
            attributes.Clock = (it.tickOn * TICK_RATE).toInt().toVariantType()
            attributes.Syllabic = 0.toVariantType()
            attributes.Duration = ((it.tickOff - it.tickOn) * TICK_RATE).toInt().toVariantType()
            noteTree.attributes = attributes

            noteTree
        }
    }

    private const val PITCH_TICK_RATE = 0.2
    private const val TICK_RATE = 2.0

    private val phonemePartPattern = Regex("""\[.+?\]""")

    private val format = Format.Tssln
}
