package io

import exception.IllegalFileException
import external.Resources
import external.generateUUID
import kotlinx.dom.appendText
import model.ExportNotification
import model.ExportResult
import model.Feature
import model.Format
import model.ImportParams
import model.ImportWarning
import model.KEY_IN_OCTAVE
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
import process.pitch.CevioTrackPitchData
import process.pitch.generateForCevio
import process.pitch.getLength
import process.pitch.pitchFromCevioTrack
import process.validateNotes
import util.appendNewChildTo
import util.clone
import util.getElementListByTagName
import util.getRequiredAttribute
import util.getRequiredAttributeAsInteger
import util.getRequiredAttributeAsLong
import util.getSingleElementByTagName
import util.getSingleElementByTagNameOrNull
import util.innerValueOrNull
import util.insertAfterThis
import util.nameWithoutExtension
import util.readText
import util.toFixed

object Ccs {
    suspend fun parse(file: File, params: ImportParams): Project {
        val projectName = file.nameWithoutExtension
        val text = file.readText()
        val parser = DOMParser()
        val document = parser.parseFromString(text, "text/xml") as XMLDocument

        val scenarioNode = document.documentElement ?: throw IllegalFileException.XmlRootNotFound()
        val sceneNode = scenarioNode
            .getSingleElementByTagName("Sequence")
            .getSingleElementByTagName("Scene")
        val unitNodes = sceneNode
            .getSingleElementByTagName("Units")
            .getElementListByTagName("Unit")
            .filter { it.getAttribute("Category") == "SingerSong" }
        val groupNodes = sceneNode
            .getSingleElementByTagName("Groups")
            .getElementListByTagName("Group")
            .filter { it.getAttribute("Category") == "SingerSong" }

        val results = unitNodes.mapIndexed { index, unitNode ->
            val groupId = unitNode.getAttribute("Group")
            val group = groupId?.let { id ->
                groupNodes.find { it.getAttribute("Id") == id }
            }
            val trackName = group?.getAttribute("Name")
            parseTrack(index, unitNode, trackName, params)
        }

        val tracks = results.map { it.track }
        val warnings = mutableListOf<ImportWarning>()
        val tempos = mergeTempos(results, warnings)
        val timeSignatures = mergeTimeSignatures(results, warnings)

        return Project(
            format = format,
            inputFiles = listOf(file),
            name = projectName,
            tracks = tracks,
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = FIXED_MEASURE_PREFIX,
            importWarnings = warnings,
        )
    }

    private fun mergeTempos(
        results: List<TrackParseResult>,
        warnings: MutableList<ImportWarning>,
    ): MutableList<Tempo> {
        val tempos = (
            results.firstOrNull { it.tempos.isNotEmpty() }?.tempos
                ?: listOf(Tempo.default).also { warnings.add(ImportWarning.TempoNotFound) }
            ).toMutableList()

        warnings.addAll(
            results.flatMap { result ->
                val ignoredTempos = result.tempos - tempos.toSet()
                ignoredTempos.map { ImportWarning.TempoIgnoredInTrack(result.track, it) }
            },
        )

        // Delete all tempo tags inside prefix, add apply the last as the first
        val firstTempoIndex = tempos
            .last { it.tickPosition <= 0 }
            .let { tempos.indexOf(it) }
        repeat(firstTempoIndex) {
            val removed = tempos.removeAt(0)
            warnings.add(ImportWarning.TempoIgnoredInPreMeasure(removed))
        }
        tempos[0] = tempos[0].copy(tickPosition = 0)
        return tempos
    }

    private fun mergeTimeSignatures(
        results: List<TrackParseResult>,
        warnings: MutableList<ImportWarning>,
    ): List<TimeSignature> {
        val timeSignatures = (
            results.firstOrNull { it.timeSignatures.isNotEmpty() }?.timeSignatures
                ?: listOf(TimeSignature.default).also { warnings.add(ImportWarning.TimeSignatureNotFound) }
            ).toMutableList()

        warnings.addAll(
            results.flatMap { result ->
                val ignoredTimeSignatures = result.timeSignatures - timeSignatures.toSet()
                ignoredTimeSignatures.map { ImportWarning.TimeSignatureIgnoredInTrack(result.track, it) }
            },
        )

        // Delete all time signatures inside prefix, add apply the last as the first
        val firstTimeSignatureIndex = timeSignatures
            .last { it.measurePosition <= 0 }
            .let { timeSignatures.indexOf(it) }
        repeat(firstTimeSignatureIndex) {
            val removed = timeSignatures.removeAt(0)
            warnings.add(ImportWarning.TimeSignatureIgnoredInPreMeasure(removed))
        }
        timeSignatures[0] = timeSignatures[0].copy(measurePosition = 0)
        return timeSignatures.toList()
    }

    private fun parseTrack(index: Int, unitNode: Element, name: String?, params: ImportParams): TrackParseResult {
        val timeNodes = unitNode
            .getSingleElementByTagNameOrNull("Song")
            ?.getSingleElementByTagNameOrNull("Beat")
            ?.getElementListByTagName("Time").orEmpty()

        val tickCounter = TickCounter(TICK_RATE)
        var timeSignatures = listOf<TimeSignature>()
        for (timeNode in timeNodes) {
            val tick = timeNode.getAttribute("Clock")?.toLongOrNull() ?: continue
            val numerator = timeNode.getAttribute("Beats")?.toIntOrNull() ?: continue
            val denominator = timeNode.getAttribute("BeatType")?.toIntOrNull() ?: continue

            tickCounter.goToTick(tick, numerator, denominator)
            timeSignatures = timeSignatures + TimeSignature(tickCounter.measure, numerator, denominator)
        }

        val tickPrefix = getTickPrefix(timeSignatures, FIXED_MEASURE_PREFIX)

        timeSignatures = timeSignatures.map { it.copy(measurePosition = it.measurePosition - FIXED_MEASURE_PREFIX) }

        val tempos = unitNode
            .getSingleElementByTagNameOrNull("Song")
            ?.getSingleElementByTagNameOrNull("Tempo")
            ?.getElementListByTagName("Sound").orEmpty()
            .mapNotNull {
                val tick = it.getAttribute("Clock")?.toLongOrNull()
                    ?.let { tick -> (tick / TICK_RATE).toLong() }
                    ?.minus(tickPrefix)
                    ?: return@mapNotNull null
                val bpm = it.getAttribute("Tempo")?.toDoubleOrNull()
                    ?: return@mapNotNull null
                Tempo(tick, bpm)
            }
            .toMutableList()

        val notes = unitNode
            .getSingleElementByTagNameOrNull("Song")
            ?.getSingleElementByTagNameOrNull("Score")
            ?.getElementListByTagName("Note").orEmpty()
            .mapIndexed { noteIndex, element ->
                val tickOn = (element.getRequiredAttributeAsLong("Clock") / TICK_RATE).toLong().minus(tickPrefix)
                val tickOff = tickOn +
                    (element.getRequiredAttributeAsLong("Duration") / TICK_RATE).toLong()
                val pitchStep = element.getRequiredAttributeAsInteger("PitchStep")
                val pitchOctave = element.getRequiredAttributeAsInteger("PitchOctave") - OCTAVE_OFFSET
                val key = pitchStep + pitchOctave * KEY_IN_OCTAVE
                val lyric = element.getRequiredAttribute("Lyric")
                Note(noteIndex, key, lyric, tickOn, tickOff)
            }

        val pitch =
            if (params.simpleImport) null
            else unitNode
                .getSingleElementByTagNameOrNull("Song")
                ?.getSingleElementByTagNameOrNull("Parameter")
                ?.getSingleElementByTagNameOrNull("LogF0")
                ?.getElementListByTagName("Data").orEmpty()
                .mapNotNull { parsePitchData(it) }
                .let { CevioTrackPitchData(it, tempos, tickPrefix) }
                .let { pitchFromCevioTrack(it) }

        val trackName = name ?: "Track ${index + 1}"
        val track = Track(index, trackName, notes, pitch).validateNotes()

        return TrackParseResult(track, tempos, timeSignatures)
    }

    private fun parsePitchData(dataElement: Element): CevioTrackPitchData.Event? {
        val index = dataElement.getAttribute("Index")?.toLongOrNull()
        val repeat = dataElement.getAttribute("Repeat")?.toLongOrNull()
        val value = dataElement.innerValueOrNull?.toDoubleOrNull() ?: return null
        return CevioTrackPitchData.Event(index, repeat, value)
    }

    private fun getTickPrefix(timeSignatures: List<TimeSignature>, measurePrefix: Int): Long {
        val counter = TickCounter()
        timeSignatures
            .filter { it.measurePosition < measurePrefix }
            .forEach { counter.goToMeasure(it) }
        counter.goToMeasure(measurePrefix)
        return counter.tick
    }

    private data class TrackParseResult(
        val track: Track,
        val tempos: List<Tempo>,
        val timeSignatures: List<TimeSignature>,
    )

    fun generate(project: Project, features: List<Feature>): ExportResult {
        val document = generateContent(project, features)
        val serializer = XMLSerializer()
        val content = serializer.serializeToString(document)
        val blob = Blob(arrayOf(content), BlobPropertyBag("application/octet-stream"))
        val name = format.getFileName(project.name)
        return ExportResult(
            blob,
            name,
            listOfNotNull(
                if (features.contains(Feature.ConvertPitch)) ExportNotification.PitchDataExported else null,
            ),
        )
    }

    private fun generateContent(project: Project, features: List<Feature>): Document {
        val text = Resources.ccsTemplate
        val parser = DOMParser()
        val document = parser.parseFromString(text, "text/xml") as XMLDocument
        val scenarioNode = requireNotNull(document.documentElement)
        val sceneNode = scenarioNode
            .getSingleElementByTagName("Sequence")
            .getSingleElementByTagName("Scene")

        val unitsNodes = sceneNode.getSingleElementByTagName("Units")
        val emptyUnitNode = unitsNodes.getSingleElementByTagName("Unit")
        unitsNodes.removeChild(emptyUnitNode)
        val groupsNode = sceneNode.getSingleElementByTagName("Groups")
        val emptyGroupNode = groupsNode.getSingleElementByTagName("Group")
        groupsNode.removeChild(emptyGroupNode)

        val measurePrefix = FIXED_MEASURE_PREFIX
        val tickPrefix = (project.timeSignatures.first().ticksInMeasure * TICK_RATE * measurePrefix).toLong()

        val tempos = emptyUnitNode
            .getSingleElementByTagName("Song")
            .getSingleElementByTagName("Tempo")
        setupTempoNodes(tempos, project.tempos, tickPrefix)

        val beats = emptyUnitNode
            .getSingleElementByTagName("Song")
            .getSingleElementByTagName("Beat")
        setupBeatNodes(beats, project.timeSignatures, tickPrefix)

        project.tracks.forEach { model ->
            val newUnit = emptyUnitNode.clone()
            val newGroup = emptyGroupNode.clone()

            val id = generateUUID()
            newUnit.setAttribute("Group", id)
            newGroup.setAttribute("Id", id)
            newGroup.setAttribute("Name", model.name)
            setupNotes(document, newUnit, model.notes, tickPrefix)

            if (features.contains(Feature.ConvertPitch)) {
                setupPitchData(document, newUnit, model, project.tempos, tickPrefix)
            }

            unitsNodes.appendChild(newUnit)
            groupsNode.appendChild(newGroup)
        }

        return document
    }

    private fun setupTempoNodes(
        temposNode: Element,
        models: List<Tempo>,
        tickPrefix: Long,
    ) {
        var previous = temposNode.getSingleElementByTagName("Sound")
        previous.setAttribute("Tempo", models.first().bpm.toFixed(2))
        models.drop(1).forEach {
            val new = previous.clone()
            new.setAttribute("Tempo", it.bpm.toFixed(2))
            new.setAttribute("Clock", (it.tickPosition * TICK_RATE + tickPrefix).toLong().toString())
            previous.insertAfterThis(new)
            previous = new
        }
    }

    private fun setupBeatNodes(
        beatsNode: Element,
        models: List<TimeSignature>,
        tickPrefix: Long,
    ) {
        var previous = beatsNode.getSingleElementByTagName("Time")
        previous.setAttribute("Beats", models.first().numerator.toString())
        previous.setAttribute("BeatType", models.first().denominator.toString())
        val counter = TickCounter(TICK_RATE)
        counter.goToMeasure(models.first())
        models.drop(1).forEach {
            val new = previous.clone()
            counter.goToMeasure(it)
            new.setAttribute("Clock", (counter.outputTick + tickPrefix).toString())
            new.setAttribute("Beats", it.numerator.toString())
            new.setAttribute("BeatType", it.denominator.toString())
            previous.insertAfterThis(new)
            previous = new
        }
    }

    private fun setupNotes(
        document: Document,
        unitNode: Element,
        models: List<Note>,
        tickPrefix: Long,
    ) {
        val score = unitNode
            .getSingleElementByTagName("Song")
            .getSingleElementByTagName("Score")
        models.forEach {
            val newNote = document.createElement("Note")
            newNote.setAttribute("Clock", (it.tickOn * TICK_RATE + tickPrefix).toLong().toString())
            newNote.setAttribute("PitchStep", (it.key % KEY_IN_OCTAVE).toString())
            newNote.setAttribute("PitchOctave", (it.key / KEY_IN_OCTAVE + OCTAVE_OFFSET).toString())
            newNote.setAttribute("Duration", (it.length * TICK_RATE).toLong().toString())
            newNote.setAttribute("Lyric", it.lyric)
            score.appendChild(newNote)
        }
    }

    private fun setupPitchData(
        document: Document,
        unitNode: Element,
        trackModel: Track,
        tempos: List<Tempo>,
        tickPrefix: Long,
    ) {
        val data = trackModel.pitch
            ?.generateForCevio(trackModel.notes, tempos, (tickPrefix / TICK_RATE).toLong()) ?: return
        val dataNodes = data.events.map {
            val newDataNode = document.createElement("Data")
            if (it.index != null) newDataNode.setAttribute("Index", it.index.toString())
            if (it.repeat != null) newDataNode.setAttribute("Repeat", it.repeat.toString())
            newDataNode.appendText(it.value.toString())
            newDataNode
        }
        val songNode = unitNode.getSingleElementByTagName("Song")
        document.appendNewChildTo(songNode, "Parameter") { parameterNode ->
            document.appendNewChildTo(parameterNode, "LogF0") { logF0Node ->
                logF0Node.setAttribute("Length", data.getLength().toString())
                dataNodes.forEach { logF0Node.appendChild(it) }
            }
        }
    }

    private const val TICK_RATE = 2.0
    private const val OCTAVE_OFFSET = -1
    private const val FIXED_MEASURE_PREFIX = 1
    private val format = Format.Ccs
}
