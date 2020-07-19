package io

import external.JsZip
import external.JsZipOption
import external.Resources
import io.MusicXml.MXmlMeasureContent.NoteType
import io.MusicXml.MXmlMeasureContent.NoteType.BEGIN
import io.MusicXml.MXmlMeasureContent.NoteType.END
import io.MusicXml.MXmlMeasureContent.NoteType.MIDDLE
import io.MusicXml.MXmlMeasureContent.NoteType.SINGLE
import kotlinx.coroutines.await
import model.ExportResult
import model.Format
import model.KEY_IN_OCTAVE
import model.Note
import model.Project
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
import util.appendNewChildTo
import util.clone
import util.getSafeFileName
import util.getSingleElementByTagName
import kotlin.dom.appendText

object MusicXml {
    suspend fun generate(project: Project): ExportResult {
        val projectWithTickRateApplied = project.applyTickRate()
        val zip = JsZip()
        for (track in projectWithTickRateApplied.tracks) {
            val content = generateTrackContent(projectWithTickRateApplied, track)
            val trackNameUrlSafe = getSafeFileName(track.name)
            val trackFileName = "${project.name}_${track.id + 1}_$trackNameUrlSafe${Format.MUSIC_XML.extension}"
            zip.file(trackFileName, content)
        }
        val option = JsZipOption().also { it.type = "blob" }
        val blob = zip.generateAsync(option).await() as Blob
        val name = project.name + ".zip"
        return ExportResult(blob, name, listOf())
    }

    private fun generateTrackContent(project: Project, track: Track): String {
        val keyTicks = project.getKeyTicks(track)
        console.log(keyTicks)
        val measures = getMeasures(keyTicks, project.timeSignatures)
        console.log(measures)

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
                if (index == 0) firstMeasureNode else null
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
            directionNode
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
                BEGIN -> "start"
                END -> "stop"
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
                        BEGIN -> "begin"
                        MIDDLE -> "middle"
                        END -> "end"
                        SINGLE -> "single"
                    }
                )
            }
            appendNewChildTo(lyricNode, "text") {
                if (type == BEGIN || type == SINGLE) it.appendText(lyric)
            }
        }
    }

    private fun Project.applyTickRate() = copy(
        tempos = tempos.map { it.copy(tickPosition = (it.tickPosition * TICK_RATE).toLong()) },
        tracks = tracks.map { track ->
            track.copy(notes = track.notes.map {
                it.copy(
                    tickOn = (it.tickOn * TICK_RATE).toLong(),
                    tickOff = (it.tickOff * TICK_RATE).toLong()
                )
            })
        }
    )

    private fun Project.getKeyTicks(track: Track): List<KeyTick> {
        val tempos = tempos.map { KeyTick.WithTempo(it.tickPosition, it) }
        val noteStarts = track.notes.map { KeyTick.WithNoteStart(it.tickOn, it) }
        val noteEnds = track.notes.map { KeyTick.WithNoteEnd(it.tickOff, it) }
        return (noteEnds + tempos + noteStarts).sortedBy { it.tick }
    }

    private fun getMeasures(keyTicks: List<KeyTick>, timeSignatures: List<TimeSignature>): List<MXmlMeasure> {
        val tickCounter = TickCounter(ticksInFullNote = (TICKS_IN_FULL_NOTE * TICK_RATE).toLong())
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
                    if (it is KeyTick.WithNoteEnd)
                        it.tick > borderPair.first && it.tick <= borderPair.second
                    else
                        it.tick >= borderPair.first && it.tick < borderPair.second
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
                                duration = keyTickRelative - currentTickInMeasure
                            )
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
                                    type = if (note.tickOn == head) BEGIN else MIDDLE
                                )
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
                                type = if (note.tickOn == head) SINGLE else END
                            )
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
                            type = if (note.tickOn == head) BEGIN else MIDDLE
                        )
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
                    contents = contents
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
        val contents: List<MXmlMeasureContent>
    )

    private sealed class MXmlMeasureContent {
        class Tempo(val bpm: Double) : MXmlMeasureContent()
        class Rest(val duration: Long) : MXmlMeasureContent()
        class Note(val duration: Long, val note: model.Note, val type: NoteType) : MXmlMeasureContent()

        enum class NoteType {
            BEGIN,
            MIDDLE,
            END,
            SINGLE
        }
    }

    private const val TICK_RATE = 2.0
    const val MUSIC_XML_VERSION = "2.0"
}
