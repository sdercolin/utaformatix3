package io

import exception.IllegalFileException
import external.Resources
import model.DEFAULT_LYRIC
import model.ExportNotification
import model.ExportResult
import model.Format
import model.ImportWarning
import model.Note
import model.Project
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
import org.w3c.files.BlobPropertyBag
import org.w3c.files.File
import process.validateNotes
import util.clone
import util.getElementListByTagName
import util.getSingleElementByTagName
import util.getSingleElementByTagNameOrNull
import util.innerValue
import util.innerValueOrNull
import util.insertAfterThis
import util.nameWithoutExtension
import util.readText
import util.setSingleChildValue
import kotlin.dom.clear
import kotlin.math.max

object Vsqx {

    suspend fun parse(file: File): Project {
        val text = file.readText()
        return when {
            text.contains("xmlns=\"http://www.yamaha.co.jp/vocaloid/schema/vsq3/\"") ->
                parse(file, text, TagNames.VSQ3)
            text.contains("xmlns=\"http://www.yamaha.co.jp/vocaloid/schema/vsq4/\"") ->
                parse(file, text, TagNames.VSQ4)
            else -> throw IllegalFileException.UnknownVsqVersion()
        }
    }

    private fun parse(file: File, textRead: String, tagNames: TagNames): Project {
        val warnings = mutableListOf<ImportWarning>()
        val projectName = file.nameWithoutExtension
        val parser = DOMParser()
        val document = parser.parseFromString(textRead, "text/xml") as XMLDocument

        val root = document.documentElement ?: throw IllegalFileException.XmlRootNotFound()

        val masterTrack = root.getSingleElementByTagName(tagNames.masterTrack)

        val preMeasureNode = masterTrack.getSingleElementByTagName(tagNames.preMeasure)
        val measurePrefix = try {
            preMeasureNode.innerValue.toInt()
        } catch (t: Throwable) {
            throw IllegalFileException.XmlElementValueIllegal(tagNames.preMeasure)
        }

        val (tickPrefix, timeSignatures) = parseTimeSignatures(masterTrack, tagNames, measurePrefix, warnings)
        val tempos = parseTempos(masterTrack, tagNames, tickPrefix, warnings)
        val tracks = root.getElementListByTagName(tagNames.vsTrack).mapIndexed { index, element ->
            parseTrack(element, index, tagNames, tickPrefix)
        }

        return Project(
            format = Format.VSQX,
            inputFiles = listOf(file),
            name = projectName,
            tracks = tracks,
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = measurePrefix,
            importWarnings = warnings
        )
    }

    private fun parseTimeSignatures(
        masterTrack: Element,
        tagNames: TagNames,
        measurePrefix: Int,
        warnings: MutableList<ImportWarning>
    ): Pair<Long, List<TimeSignature>> {
        val rawTimeSignatures = masterTrack.getElementListByTagName(tagNames.timeSig, allowEmpty = false)
            .mapNotNull {
                val posMes = it.getSingleElementByTagNameOrNull(tagNames.posMes)?.innerValueOrNull?.toIntOrNull()
                    ?: return@mapNotNull null
                val nume = it.getSingleElementByTagNameOrNull(tagNames.nume)?.innerValueOrNull?.toIntOrNull()
                    ?: return@mapNotNull null
                val denomi = it.getSingleElementByTagNameOrNull(tagNames.denomi)?.innerValueOrNull?.toIntOrNull()
                    ?: return@mapNotNull null
                TimeSignature(
                    measurePosition = posMes,
                    numerator = nume,
                    denominator = denomi
                )
            }
            .let {
                if (it.isEmpty()) {
                    warnings.add(ImportWarning.TimeSignatureNotFound)
                    listOf(TimeSignature.default)
                } else it
            }

        // Calculate before time signatures are cleaned up
        val tickPrefix = getTickPrefix(rawTimeSignatures, measurePrefix)

        val timeSignatures = rawTimeSignatures
            .map { it.copy(measurePosition = it.measurePosition - measurePrefix) }
            .toMutableList()

        // Delete all time signatures inside prefix, add apply the last as the first
        val firstTimeSignatureIndex = timeSignatures
            .last { it.measurePosition <= 0 }
            .let { timeSignatures.indexOf(it) }
        repeat(firstTimeSignatureIndex) {
            val removed = timeSignatures.removeAt(0)
            warnings.add(ImportWarning.TimeSignatureIgnoredInPreMeasure(removed))
        }
        timeSignatures[0] = timeSignatures[0].copy(measurePosition = 0)
        return tickPrefix to timeSignatures.toList()
    }

    private fun getTickPrefix(timeSignatures: List<TimeSignature>, measurePrefix: Int): Long {
        val counter = TickCounter()
        timeSignatures
            .filter { it.measurePosition < measurePrefix }
            .forEach { counter.goToMeasure(it) }
        counter.goToMeasure(measurePrefix)
        return counter.tick
    }

    private fun parseTempos(
        masterTrack: Element,
        tagNames: TagNames,
        tickPrefix: Long,
        warnings: MutableList<ImportWarning>
    ): List<Tempo> {
        val tempos = masterTrack.getElementListByTagName(tagNames.tempo, allowEmpty = false)
            .mapNotNull {
                val posTick =
                    it.getSingleElementByTagNameOrNull(tagNames.posTick)?.innerValueOrNull?.toLongOrNull()
                        ?: return@mapNotNull null
                val bpm =
                    it.getSingleElementByTagNameOrNull(tagNames.bpm)?.innerValueOrNull?.toDoubleOrNull()
                        ?.let { bpm -> bpm / BPM_RATE }
                        ?: return@mapNotNull null
                Tempo(
                    tickPosition = posTick - tickPrefix,
                    bpm = bpm
                )
            }
            .let {
                if (it.isEmpty()) {
                    warnings.add(ImportWarning.TempoNotFound)
                    listOf(Tempo.default)
                } else it
            }
            .toMutableList()

        // Delete all tempo tags inside prefix, add apply the last as the first
        val firstTempoIndex = tempos
            .last { it.tickPosition <= 0 }
            .let { tempos.indexOf(it) }
        repeat(firstTempoIndex) {
            val removed = tempos.removeAt(0)
            warnings.add(ImportWarning.TempoIgnoredInPreMeasure(removed))
        }
        tempos[0] = tempos[0].copy(tickPosition = 0)
        return tempos.toList()
    }

    private fun parseTrack(trackNode: Element, id: Int, tagNames: TagNames, tickPrefix: Long): Track {
        val trackName = trackNode.getSingleElementByTagNameOrNull(tagNames.trackName)?.innerValueOrNull
            ?: "Track ${id + 1}"
        val notes = trackNode.getElementListByTagName(tagNames.musicalPart)
            .flatMap { partNode ->
                val tickOffset =
                    partNode.getSingleElementByTagName(tagNames.posTick).innerValue.toLong() - tickPrefix
                partNode.getElementListByTagName(tagNames.note).map { tickOffset to it }
            }
            .mapIndexed { index, (tickOffset, noteNode) ->
                val key = noteNode.getSingleElementByTagName(tagNames.noteNum).innerValue.toInt()
                val tickOn = noteNode.getSingleElementByTagName(tagNames.posTick).innerValue.toLong()
                val length = noteNode.getSingleElementByTagName(tagNames.duration).innerValue.toLong()
                val lyric = noteNode.getSingleElementByTagNameOrNull(tagNames.lyric)?.innerValueOrNull ?: DEFAULT_LYRIC
                val xSampa = noteNode.getSingleElementByTagNameOrNull(tagNames.xSampa)?.innerValueOrNull
                Note(
                    id = index,
                    key = key,
                    tickOn = tickOn + tickOffset,
                    tickOff = tickOn + tickOffset + length,
                    lyric = lyric,
                    xSampa = xSampa
                )
            }
        return Track(
            id = id,
            name = trackName,
            notes = notes
        ).validateNotes()
    }

    fun generate(project: Project): ExportResult {
        val document = generateContent(project)
        val serializer = XMLSerializer()
        val content = serializer.serializeToString(document)
        val blob = Blob(arrayOf(content), BlobPropertyBag("application/octet-stream"))
        val name = project.name + Format.VSQX.extension
        return ExportResult(blob, name, listOf(ExportNotification.PhonemeResetRequiredV4))
    }

    private fun generateContent(project: Project): Document {
        val text = Resources.vsqxTemplate
        val tagNames = TagNames.VSQ4
        val parser = DOMParser()
        val document = parser.parseFromString(text, "text/xml") as XMLDocument
        val root = requireNotNull(document.documentElement)

        val mixer = root.getSingleElementByTagName(tagNames.mixer)
        val masterTrack = root.getSingleElementByTagName(tagNames.masterTrack)

        val measurePrefix = max(project.measurePrefix, MIN_MEASURE_OFFSET)
        masterTrack.setSingleChildValue(tagNames.preMeasure, measurePrefix)
        val tickPrefix = project.timeSignatures.first().ticksInMeasure * measurePrefix.toLong()

        setupTempoNodes(masterTrack, tagNames, project.tempos, tickPrefix)
        setupTimeSignatureNodes(masterTrack, tagNames, project.timeSignatures, measurePrefix)

        val emptyTrack = root.getSingleElementByTagName(tagNames.vsTrack)
        val emptyUnit = mixer.getSingleElementByTagName(tagNames.vsUnit)
        var track = emptyTrack
        var unit = emptyUnit
        for (trackIndex in project.tracks.indices) {
            val newTrack = generateNewTrackNode(emptyTrack, tagNames, trackIndex, project, tickPrefix, document)
            track.insertAfterThis(newTrack)
            track = newTrack

            val newUnit = emptyUnit.clone()
            newUnit.setSingleChildValue(tagNames.trackNum, trackIndex)
            unit.insertAfterThis(newUnit)
            unit = newUnit
        }
        root.removeChild(emptyTrack)
        mixer.removeChild(emptyUnit)

        return document
    }

    private fun setupTempoNodes(
        masterTrack: Element,
        tagNames: TagNames,
        models: List<Tempo>,
        tickPrefix: Long
    ) {
        val empty = masterTrack.getSingleElementByTagName(tagNames.tempo)
        var previous = empty
        previous.setSingleChildValue(tagNames.bpm, (models.first().bpm * BPM_RATE).toInt())
        models.drop(1).forEach {
            val model =
                if (it.tickPosition == 0L) it
                else it.copy(tickPosition = it.tickPosition + tickPrefix)

            val new = empty.clone()
            new.setSingleChildValue(tagNames.posTick, model.tickPosition)
            new.setSingleChildValue(tagNames.bpm, (model.bpm * BPM_RATE).toInt())
            previous.insertAfterThis(new)
            previous = new
        }
    }

    private fun setupTimeSignatureNodes(
        masterTrack: Element,
        tagNames: TagNames,
        models: List<TimeSignature>,
        measurePrefix: Int
    ) {
        val empty = masterTrack.getSingleElementByTagName(tagNames.timeSig)
        var previous = empty
        previous.setSingleChildValue(tagNames.nume, models.first().numerator)
        previous.setSingleChildValue(tagNames.denomi, models.first().denominator)
        models.drop(1).forEach {
            val model =
                if (it.measurePosition == 0) it
                else it.copy(measurePosition = it.measurePosition + measurePrefix)

            val new = empty.clone()
            new.setSingleChildValue(tagNames.posMes, model.measurePosition)
            new.setSingleChildValue(tagNames.nume, model.numerator)
            new.setSingleChildValue(tagNames.denomi, model.denominator)
            previous.insertAfterThis(new)
            previous = new
        }
    }

    private fun generateNewTrackNode(
        emptyTrack: Element,
        tagNames: TagNames,
        trackIndex: Int,
        project: Project,
        tickPrefix: Long,
        document: XMLDocument
    ): Element {
        val trackModel = project.tracks[trackIndex]

        val newTrack = emptyTrack.clone()
        newTrack.setSingleChildValue(tagNames.trackNum, trackIndex)
        newTrack.setSingleChildValue(tagNames.trackName, trackModel.name)

        val part = newTrack.getSingleElementByTagName(tagNames.musicalPart)
        part.setSingleChildValue(tagNames.posTick, tickPrefix)
        part.setSingleChildValue(tagNames.playTime, trackModel.notes.lastOrNull()?.tickOff ?: 0)

        val emptyNote = part.getSingleElementByTagName(tagNames.note)
        var note = emptyNote
        trackModel.notes
            .map { model -> generateNewNote(emptyNote, tagNames, model, document) }
            .forEach { newNote ->
                note.insertAfterThis(newNote)
                note = newNote
            }
        part.removeChild(emptyNote)

        if (trackModel.notes.isEmpty()) {
            newTrack.removeChild(part)
        }
        return newTrack
    }

    private fun generateNewNote(
        emptyNote: Element,
        tagNames: TagNames,
        model: Note,
        document: XMLDocument
    ): Element {
        val newNote = emptyNote.clone()
        newNote.setSingleChildValue(tagNames.posTick, model.tickOn)
        newNote.setSingleChildValue(tagNames.duration, model.length)
        newNote.setSingleChildValue(tagNames.noteNum, model.key)
        newNote.getSingleElementByTagName(tagNames.lyric).also {
            it.clear()
            val lyricCData = document.createCDATASection(model.lyric)
            it.appendChild(lyricCData)
        }
        return newNote
    }

    private const val BPM_RATE = 100.0
    private const val MIN_MEASURE_OFFSET = 1

    private enum class TagNames(
        val masterTrack: String = "masterTrack",
        val preMeasure: String = "preMeasure",
        val timeSig: String = "timeSig",
        val posMes: String = "posMes",
        val nume: String = "nume",
        val denomi: String = "denomi",
        val tempo: String = "tempo",
        val posTick: String = "posTick",
        val bpm: String = "bpm",
        val vsTrack: String = "vsTrack",
        val trackName: String = "trackName",
        val musicalPart: String = "musicalPart",
        val note: String = "note",
        val duration: String = "durTick",
        val noteNum: String = "noteNum",
        val lyric: String = "lyric",
        val xSampa: String = "phnms",
        val mixer: String = "mixer",
        val vsUnit: String = "vsUnit",
        val trackNum: String = "vsTrackNo",
        val playTime: String = "playTime"
    ) {
        VSQ3,
        VSQ4(
            posMes = "m",
            nume = "nu",
            denomi = "de",
            posTick = "t",
            bpm = "v",
            trackName = "name",
            musicalPart = "vsPart",
            duration = "dur",
            noteNum = "n",
            lyric = "y",
            trackNum = "tNo",
            xSampa = "p"
        )
    }
}
