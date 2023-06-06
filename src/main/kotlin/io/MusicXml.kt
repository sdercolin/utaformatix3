package io

import exception.IllegalFileException
import external.JsZip
import external.JsZipOption
import external.Resources
import io.MusicXml.MXmlMeasureContent.NoteType
import kotlinx.coroutines.await
import kotlinx.dom.appendText
import model.DEFAULT_KEY
import model.DEFAULT_LYRIC
import model.ExportResult
import model.Format
import model.ImportWarning
import model.KEY_IN_OCTAVE
import model.Note
import model.Project
import model.TICKS_IN_BEAT
import model.TICKS_IN_FULL_NOTE
import model.Tempo
import model.TickCounter
import model.TimeSignature
import model.Track
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.XMLDocument
import org.w3c.dom.parsing.DOMParser
import org.w3c.dom.parsing.XMLSerializer
import org.w3c.files.Blob
import org.w3c.files.File
import util.appendNewChildTo
import util.clone
import util.getElementListByTagName
import util.getSafeFileName
import util.getSingleElementByTagName
import util.getSingleElementByTagNameOrNull
import util.innerValue
import util.innerValueOrNull
import util.nameWithoutExtension
import util.readText

object MusicXml {

    suspend fun parse(file: File): Project {
        val projectName = file.nameWithoutExtension
        val text = file.readText()
        val parser = DOMParser()
        val document = parser.parseFromString(text, "text/xml") as XMLDocument

        val rootNode = document.documentElement ?: throw IllegalFileException.XmlRootNotFound()
        val partNodes = rootNode.getElementListByTagName("part")

        val warnings = mutableListOf<ImportWarning>()
        val masterTrack = partNodes.firstOrNull {
            it.getElementListByTagName("measure").isNotEmpty()
        } ?: throw IllegalFileException.XmlElementNotFound("measure")
        val masterTrackResult = parseMasterTrack(masterTrack, warnings)
        val timeSignatures = masterTrackResult.timeSignatures.ifEmpty { listOf(TimeSignature.default) }
        val tempos = masterTrackResult.tempoWithMeasureIndexes.map { it.second }.ifEmpty { listOf(Tempo.default) }

        val tracks = partNodes.mapIndexed { index, element -> parseTrack(index, element, masterTrackResult) }

        return Project(
            format = format,
            inputFiles = listOf(file),
            name = projectName,
            tracks = tracks,
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = 0,
            importWarnings = warnings,
        )
    }

    private fun parseMasterTrack(partNode: Element, warnings: MutableList<ImportWarning>): MasterTrackParseResult {
        val measureNodes = partNode.getElementListByTagName("measure")
        val divisions = measureNodes.first()
            .getElementListByTagName("attributes")
            .flatMap { it.getElementListByTagName("divisions") }
            .first().innerValue.toLong()
        val importTickRate = TICKS_IN_BEAT.toDouble() / divisions
        val tempos = mutableListOf<Pair<Int, Tempo>>()
        val timeSignatures = mutableListOf<TimeSignature>()
        val measureBorders = mutableListOf(0L)
        var tickPosition = 0L
        var currentTimeSignature = TimeSignature.default
        measureNodes.forEachIndexed { index, measureNode ->
            val timeSignature = measureNode.getElementListByTagName("attributes")
                .flatMap { it.getElementListByTagName("time") }
                .firstOrNull()
                ?.let { timeNode ->
                    TimeSignature(
                        measurePosition = index,
                        numerator = timeNode.getSingleElementByTagName("beats").innerValue.toInt(),
                        denominator = timeNode.getSingleElementByTagName("beat-type").innerValue.toInt(),
                    )
                }
                ?.also {
                    timeSignatures.add(it)
                    currentTimeSignature = it
                }
                ?: currentTimeSignature

            measureNode.getElementListByTagName("sound")
                .firstOrNull { it.hasAttribute("tempo") }
                ?.let { soundNode ->
                    Tempo(
                        tickPosition = tickPosition,
                        bpm = soundNode.getAttribute("tempo")!!.toDouble(),
                    )
                }
                ?.also {
                    tempos.add(index to it)
                }

            tickPosition += timeSignature.ticksInMeasure
            measureBorders.add(tickPosition)
        }
        if (timeSignatures.isEmpty()) {
            warnings.add(ImportWarning.TimeSignatureNotFound)
        }
        if (tempos.isEmpty()) {
            warnings.add(ImportWarning.TempoNotFound)
        }
        return MasterTrackParseResult(
            tempoWithMeasureIndexes = tempos,
            timeSignatures = timeSignatures,
            importTickRate = importTickRate,
            measureBorders = measureBorders,
        )
    }

    private fun parseTrack(trackIndex: Int, partNode: Element, masterTrackResult: MasterTrackParseResult): Track {
        val trackName = "Track ${trackIndex + 1}"
        val notes = mutableListOf<Note>()
        var isInsideNote = false
        val importTickRate = masterTrackResult.importTickRate
        partNode.getElementListByTagName("measure").forEachIndexed { index, measureNode ->
            var tickPosition = masterTrackResult.measureBorders[index]
            measureNode.getElementListByTagName("note").forEach { noteNode ->
                val duration = noteNode.getSingleElementByTagNameOrNull("duration")
                    ?.innerValue?.toLongOrNull()?.times(importTickRate)?.toLong()
                    ?: if (noteNode.getSingleElementByTagNameOrNull("grace") != null) {
                        return@forEach
                    } else {
                        throw IllegalFileException.XmlElementNotFound("duration")
                    }
                if (noteNode.getElementListByTagName("rest").isNotEmpty()) {
                    tickPosition += duration
                    return@forEach
                }

                val key = noteNode.getSingleElementByTagNameOrNull("pitch")?.let { pitchNode ->
                    val step = pitchNode.getSingleElementByTagName("step").innerValue
                    val alter = pitchNode.getSingleElementByTagNameOrNull("alter")?.innerValueOrNull?.toInt()
                    val relativeKey = when (step) {
                        "C" -> 0
                        "D" -> 2
                        "E" -> 4
                        "F" -> 5
                        "G" -> 7
                        "A" -> 9
                        "B" -> 11
                        else -> throw IllegalStateException()
                    } + (alter ?: 0)
                    val octave = pitchNode.getSingleElementByTagName("octave").innerValue.toInt() + 1
                    octave * KEY_IN_OCTAVE + relativeKey
                } ?: DEFAULT_KEY

                val lyric = noteNode.getSingleElementByTagNameOrNull("lyric")
                    ?.getSingleElementByTagNameOrNull("text")
                    ?.innerValueOrNull ?: DEFAULT_LYRIC

                val note = if (!isInsideNote) {
                    Note(
                        id = 0,
                        key = key,
                        lyric = lyric,
                        tickOn = tickPosition,
                        tickOff = tickPosition + duration,
                    )
                } else {
                    notes.removeLast().let {
                        it.copy(tickOff = it.tickOff + duration)
                    }
                }

                tickPosition += duration
                notes.add(note)

                when (noteNode.getSingleElementByTagNameOrNull("tie")?.getAttribute("type")) {
                    "start" -> isInsideNote = true
                    "stop" -> isInsideNote = false
                    else -> Unit
                }
            }
        }
        return Track(
            id = trackIndex,
            name = trackName,
            notes = notes,
        )
    }

    private data class MasterTrackParseResult(
        val tempoWithMeasureIndexes: List<Pair<Int, Tempo>>,
        val timeSignatures: List<TimeSignature>,
        val importTickRate: Double,
        val measureBorders: List<Long>,
    )

    suspend fun generate(project: Project): ExportResult {
        val projectWithTickRateApplied = project.applyTickRate()
        val zip = JsZip()
        for (track in projectWithTickRateApplied.tracks) {
            val content = generateTrackContent(projectWithTickRateApplied, track)
            val trackNameUrlSafe = getSafeFileName(track.name)
            val trackFileName = "${project.name}_${track.id + 1}_$trackNameUrlSafe.${format.extension}"
            zip.file(trackFileName, content)
        }
        val option = JsZipOption().also { it.type = "blob" }
        val blob = zip.generateAsync(option).await() as Blob
        val name = project.name + ".zip"
        return ExportResult(blob, name, listOf())
    }

    private fun generateTrackContent(project: Project, track: Track): String {
        val keyTicks = project.getKeyTicks(track)
        val measures = getMeasures(keyTicks, project.timeSignatures)

        val templateText = Resources.musicXmlTemplate
        val parser = DOMParser()
        val document = parser.parseFromString(templateText, "text/xml") as XMLDocument
        val root = requireNotNull(document.documentElement)
        val partNode = root.getSingleElementByTagName("part")
        val firstMeasureNode = partNode.getSingleElementByTagName("measure")
        partNode.removeChild(firstMeasureNode)

        measures.forEachIndexed { index, measure ->
            val measureNode = document.generateMeasureNode(
                measure,
                index,
                if (index == 0) firstMeasureNode else null,
            )
            partNode.appendChild(measureNode)
        }

        val serializer = XMLSerializer()
        return serializer.serializeToString(document)
    }

    private fun Document.generateMeasureNode(measure: MXmlMeasure, index: Int, baseMeasureNode: Element?): Element {
        val node = baseMeasureNode?.clone() ?: createElement("measure")
        node.setAttribute("number", (index + 1).toString())
        measure.timeSignature?.let { timeSignature ->
            generateTimeSignatureNode(timeSignature).also {
                node.appendChild(it)
            }
        }
        for (content in measure.contents) {
            when (content) {
                is MXmlMeasureContent.Tempo -> generateNodesForTempo(content).forEach {
                    node.appendChild(it)
                }
                is MXmlMeasureContent.Rest -> generateRestNode(content).also {
                    node.appendChild(it)
                }
                is MXmlMeasureContent.Note -> generateNoteNode(content).also {
                    node.appendChild(it)
                }
            }
        }
        return node
    }

    private fun Document.generateTimeSignatureNode(timeSignature: TimeSignature): Element =
        createElement("attributes").also { attributesNode ->
            appendNewChildTo(attributesNode, "time") { timeNode ->
                appendNewChildTo(timeNode, "beats") {
                    it.appendText(timeSignature.numerator.toString())
                }
                appendNewChildTo(timeNode, "beat-type") {
                    it.appendText(timeSignature.denominator.toString())
                }
            }
        }

    private fun Document.generateNodesForTempo(tempo: MXmlMeasureContent.Tempo): List<Element> {
        val soundNode = createElement("sound").also {
            it.setAttribute("tempo", tempo.bpm.toString())
        }
        val directionNode = createElement("direction").also { directionNode ->
            appendNewChildTo(directionNode, "direction-type") { directionTypeNode ->
                appendNewChildTo(directionTypeNode, "metronome") { metronomeNode ->
                    appendNewChildTo(metronomeNode, "beat-unit") {
                        it.appendText("quarter")
                    }
                    appendNewChildTo(metronomeNode, "per-minute") {
                        it.appendText(tempo.bpm.toString())
                    }
                }
            }
            directionNode.appendChild(soundNode.clone())
        }
        return listOf(
            soundNode,
            directionNode,
        )
    }

    private fun Document.generateRestNode(rest: MXmlMeasureContent.Rest): Element =
        createElement("note").also { noteNode ->
            appendNewChildTo(noteNode, "rest") {}
            appendNewChildTo(noteNode, "duration") {
                it.appendText(rest.duration.toString())
            }
        }

    private fun Document.generateNoteNode(note: MXmlMeasureContent.Note): Element =
        createElement("note").also { noteNode ->
            appendNewChildTo(noteNode, "pitch") { pitchNode ->
                val octave = (note.note.key / KEY_IN_OCTAVE) - 1
                val (step, alter) = when (note.note.key % KEY_IN_OCTAVE) {
                    0 -> "C" to 0
                    1 -> "C" to 1
                    2 -> "D" to 0
                    3 -> "D" to 1
                    4 -> "E" to 0
                    5 -> "F" to 0
                    6 -> "F" to 1
                    7 -> "G" to 0
                    8 -> "G" to 1
                    9 -> "A" to 0
                    10 -> "A" to 1
                    11 -> "B" to 0
                    else -> throw IllegalStateException()
                }
                appendNewChildTo(pitchNode, "step") {
                    it.appendText(step)
                }
                if (alter == 1) {
                    appendNewChildTo(pitchNode, "alter") {
                        it.appendText(alter.toString())
                    }
                }
                appendNewChildTo(pitchNode, "octave") {
                    it.appendText(octave.toString())
                }
            }
            appendNewChildTo(noteNode, "duration") {
                it.appendText(note.duration.toString())
            }
            val tieType = when (note.type) {
                NoteType.Begin -> "start"
                NoteType.End -> "stop"
                else -> null
            }
            if (tieType != null) {
                appendNewChildTo(noteNode, "tie") {
                    it.setAttribute("type", tieType)
                }
                appendNewChildTo(noteNode, "notations") { notationsNode ->
                    appendNewChildTo(notationsNode, "tied") {
                        it.setAttribute("type", tieType)
                    }
                }
            }
            appendLyricNode(noteNode, note.type, note.note.lyric)
        }

    private fun Document.appendLyricNode(noteNode: Element, type: NoteType, lyric: String) {
        appendNewChildTo(noteNode, "lyric") { lyricNode ->
            appendNewChildTo(lyricNode, "syllabic") {
                it.appendText(
                    when (type) {
                        NoteType.Begin -> "begin"
                        NoteType.Middle -> "middle"
                        NoteType.End -> "end"
                        NoteType.Single -> "single"
                    },
                )
            }
            appendNewChildTo(lyricNode, "text") {
                if (type == NoteType.Begin || type == NoteType.Single) it.appendText(lyric)
            }
        }
    }

    private fun Project.applyTickRate() = copy(
        tempos = tempos.map { it.copy(tickPosition = (it.tickPosition * DEFAULT_TICK_RATE_CEVIO).toLong()) },
        tracks = tracks.map { track ->
            track.copy(
                notes = track.notes.map {
                    it.copy(
                        tickOn = (it.tickOn * DEFAULT_TICK_RATE_CEVIO).toLong(),
                        tickOff = (it.tickOff * DEFAULT_TICK_RATE_CEVIO).toLong(),
                    )
                },
            )
        },
    )

    private fun Project.getKeyTicks(track: Track): List<KeyTick> {
        val tempos = tempos.map { KeyTick.WithTempo(it.tickPosition, it) }
        val noteStarts = track.notes.map { KeyTick.WithNoteStart(it.tickOn, it) }
        val noteEnds = track.notes.map { KeyTick.WithNoteEnd(it.tickOff, it) }
        return (noteEnds + tempos + noteStarts).sortedBy { it.tick }
    }

    private fun getMeasures(keyTicks: List<KeyTick>, timeSignatures: List<TimeSignature>): List<MXmlMeasure> {
        val tickCounter = TickCounter(ticksInFullNote = (TICKS_IN_FULL_NOTE * DEFAULT_TICK_RATE_CEVIO).toLong())
        val measureBorderTicks = mutableListOf(0L)
        for (timeSignature in timeSignatures) {
            val previousMeasure = tickCounter.measure
            val ticksInMeasure = tickCounter.ticksInMeasure
            tickCounter.goToMeasure(timeSignature)
            val currentMeasure = tickCounter.measure
            repeat(currentMeasure - previousMeasure) {
                measureBorderTicks.add(measureBorderTicks.last() + ticksInMeasure)
            }
        }
        val lastTick = keyTicks.last().tick
        if (lastTick >= tickCounter.tick + tickCounter.ticksInMeasure) {
            val previousMeasure = tickCounter.measure
            val ticksInMeasure = tickCounter.ticksInMeasure
            tickCounter.goToTick(lastTick)
            val currentMeasure = tickCounter.measure
            repeat(currentMeasure - previousMeasure) {
                measureBorderTicks.add(measureBorderTicks.last() + ticksInMeasure)
            }
        }
        measureBorderTicks.add(measureBorderTicks.last() + tickCounter.ticksInMeasure)

        val keyTicksWithMeasureBorders = measureBorderTicks.zipWithNext()
            .map { borderPair ->
                borderPair to keyTicks.filter {
                    if (it is KeyTick.WithNoteEnd) {
                        it.tick > borderPair.first && it.tick <= borderPair.second
                    } else {
                        it.tick >= borderPair.first && it.tick < borderPair.second
                    }
                }
            }

        var currentContentGroup: MutableList<MXmlMeasureContent>
        val contentGroupBorderPairMap = mutableMapOf<Pair<Long, Long>, List<MXmlMeasureContent>>()
        var ongoingNoteWithCurrentHead: Pair<Note, Long>? = null
        for ((borderPair, keyTickGroup) in keyTicksWithMeasureBorders) {
            var currentTickInMeasure = 0L
            currentContentGroup = mutableListOf()
            for (keyTick in keyTickGroup) {
                val keyTickRelative = keyTick.tick - borderPair.first
                if (keyTickRelative > currentTickInMeasure) {
                    if (ongoingNoteWithCurrentHead == null) {
                        currentContentGroup.add(
                            MXmlMeasureContent.Rest(
                                duration = keyTickRelative - currentTickInMeasure,
                            ),
                        )
                    }
                    currentTickInMeasure = keyTickRelative
                }
                when (keyTick) {
                    is KeyTick.WithTempo ->
                        if (ongoingNoteWithCurrentHead == null) {
                            currentContentGroup.add(MXmlMeasureContent.Tempo(keyTick.tempo.bpm))
                        } else {
                            val (note, head) = ongoingNoteWithCurrentHead
                            currentContentGroup.add(
                                MXmlMeasureContent.Note(
                                    duration = keyTick.tick - head,
                                    note = note,
                                    type = if (note.tickOn == head) NoteType.Begin else NoteType.Middle,
                                ),
                            )
                            ongoingNoteWithCurrentHead = note to keyTick.tick
                            currentContentGroup.add(MXmlMeasureContent.Tempo(keyTick.tempo.bpm))
                        }
                    is KeyTick.WithNoteStart ->
                        ongoingNoteWithCurrentHead = keyTick.note to keyTick.tick
                    is KeyTick.WithNoteEnd -> {
                        val (note, head) = ongoingNoteWithCurrentHead!!
                        currentContentGroup.add(
                            MXmlMeasureContent.Note(
                                duration = keyTick.note.tickOff - head,
                                note = keyTick.note,
                                type = if (note.tickOn == head) NoteType.Single else NoteType.End,
                            ),
                        )
                        ongoingNoteWithCurrentHead = null
                    }
                }
            }
            val restLength = borderPair.second - borderPair.first - currentTickInMeasure
            if (restLength > 0) {
                val ongoingNoteAtMeasureEnd = ongoingNoteWithCurrentHead
                if (ongoingNoteAtMeasureEnd == null) {
                    currentContentGroup.add(MXmlMeasureContent.Rest(duration = restLength))
                } else {
                    val (note, head) = ongoingNoteAtMeasureEnd
                    currentContentGroup.add(
                        MXmlMeasureContent.Note(
                            duration = borderPair.second - head,
                            note = note,
                            type = if (note.tickOn == head) NoteType.Begin else NoteType.Middle,
                        ),
                    )
                    ongoingNoteWithCurrentHead = note to borderPair.second
                }
            }
            contentGroupBorderPairMap[borderPair] = (currentContentGroup)
        }

        return contentGroupBorderPairMap.toList()
            .sortedBy { it.first.first }
            .mapIndexed { index, (borderPair, contents) ->
                MXmlMeasure(
                    tickStart = borderPair.first,
                    length = borderPair.second - borderPair.first,
                    timeSignature = timeSignatures.find { it.measurePosition == index },
                    contents = contents,
                )
            }
    }

    private sealed class KeyTick(val tick: Long) {
        class WithTempo(tick: Long, val tempo: Tempo) : KeyTick(tick)
        class WithNoteStart(tick: Long, val note: Note) : KeyTick(tick)
        class WithNoteEnd(tick: Long, val note: Note) : KeyTick(tick)
    }

    private data class MXmlMeasure(
        val tickStart: Long,
        val length: Long,
        val timeSignature: TimeSignature?,
        val contents: List<MXmlMeasureContent>,
    )

    private sealed class MXmlMeasureContent {
        class Tempo(val bpm: Double) : MXmlMeasureContent()
        class Rest(val duration: Long) : MXmlMeasureContent()
        class Note(val duration: Long, val note: model.Note, val type: NoteType) : MXmlMeasureContent()

        enum class NoteType {
            Begin,
            Middle,
            End,
            Single
        }
    }

    private const val DEFAULT_TICK_RATE_CEVIO = 2.0
    const val MUSIC_XML_VERSION = "2.0"
    private val format = Format.MusicXml
}
