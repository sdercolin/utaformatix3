package io

import exception.EmptyProjectException
import model.DEFAULT_LYRIC
import model.ExportNotification
import model.ExportResult
import model.Feature
import model.Format
import model.ImportParams
import model.ImportWarning
import model.Note
import model.Pitch
import model.Project
import model.TICKS_IN_FULL_NOTE
import model.Track
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.File
import process.lengthLimited
import process.pitch.VocaloidPartPitchData
import process.pitch.generateForVocaloid
import process.pitch.pitchFromVocaloidParts
import process.validateNotes
import util.MidiUtil
import util.addBlock
import util.addString
import util.asByteTypedArray
import util.encode
import util.linesNotBlank
import util.nameWithoutExtension
import util.padStartZero
import util.splitFirst

object VsqLike {

    suspend fun match(file: File): Boolean {
        val midi = Mid.parseMidi(file)
        if (midi == false) {
            return false
        }
        val midiTracks = midi.track as Array<dynamic>
        val tracksAsText = Mid.extractVsqTextsFromMetaEvents(midiTracks).filter { it.isNotEmpty() }
        if (tracksAsText.isEmpty()) return false
        return tracksAsText.any { track ->
            @Suppress("RegExpRedundantEscape") // Cannot remove the second \
            track.linesNotBlank().any { Regex("""\[.*\]""").matches(it) }
        }
    }

    suspend fun parse(file: File, format: Format, params: ImportParams): Project {
        val midi = Mid.parseMidi(file)
        val midiTracks = midi.track as Array<dynamic>
        val timeDivision = midi.timeDivision as Int
        val warnings = mutableListOf<ImportWarning>()
        val tracksAsText = Mid.extractVsqTextsFromMetaEvents(midiTracks).filter { it.isNotEmpty() }
        val measurePrefix = getMeasurePrefix(tracksAsText.first())
        val (tempos, timeSignatures, tickPrefix) = Mid.parseMasterTrack(
            timeDivision,
            midiTracks.first(),
            measurePrefix,
            warnings,
        )

        val tracks = tracksAsText.mapIndexed { index, trackText ->
            parseTrack(trackText, index, tickPrefix, params)
        }

        return Project(
            format = format,
            inputFiles = listOf(file),
            name = file.nameWithoutExtension,
            tracks = tracks,
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = measurePrefix,
            importWarnings = warnings,
        )
    }

    private fun getMeasurePrefix(firstTrack: String): Int {
        val parameterName = "PreMeasure"
        firstTrack.linesNotBlank()
            .forEach { line ->
                if (line.contains(parameterName)) {
                    return line.replace("$parameterName=", "").toIntOrNull() ?: 0
                }
            }
        return 0
    }

    private fun parseTrack(trackAsText: String, trackId: Int, tickPrefix: Long, params: ImportParams): Track {
        val lines = trackAsText.linesNotBlank()
        val titleWithIndexes = lines.mapIndexed { index, line ->
            @Suppress("RegExpRedundantEscape") // Cannot remove the second \
            if (Regex("""\[.*\]""").matches(line)) line.drop(1).dropLast(1) to index
            else null
        }.filterNotNull()
        val sectionMap = titleWithIndexes.zipWithNext().map { (current, next) ->
            current.first to lines.subList(current.second + 1, next.second)
        }.plus(
            titleWithIndexes.last().let { last ->
                last.first to lines.subList(last.second, lines.count())
            },
        ).associate { pair ->
            pair.first to pair.second.associate { it.splitFirst("=") }
        }

        val name = sectionMap["Common"]?.let { section ->
            section["Name"] ?: ""
        } ?: "Track ${trackId + 1}"

        val eventList = sectionMap["EventList"] ?: return Track(trackId, name, listOf())
        val notes = eventList.entries
            .map { (it.key.toLong() - tickPrefix) to sectionMap[it.value] }
            .map { (tickPosition, section) ->
                section ?: return@map null
                if (section["Type"] != "Anote") return@map null
                val length = section["Length"]?.toLongOrNull() ?: return@map null
                val key = section["Note#"]?.toIntOrNull() ?: return@map null
                val lyricsInfo = section["LyricHandle"]?.let { lyricHandleKey ->
                    sectionMap[lyricHandleKey]?.let { lyricHandle ->
                        lyricHandle["L0"]?.split(',')
                    }
                }
                val (lyric, xSampa) = lyricsInfo?.let {
                    it[0].trim('"') to it[1].trim('"')
                } ?: (DEFAULT_LYRIC to null)
                Note(
                    id = 0,
                    key = key,
                    lyric = lyric,
                    tickOn = tickPosition,
                    tickOff = tickPosition + length,
                    phoneme = xSampa,
                )
            }
            .filterNotNull()
        val pitch = if (params.simpleImport) null else parsePitchData(sectionMap, tickPrefix)
        return Track(trackId, name, notes, pitch).validateNotes()
    }

    private fun parsePitchData(sectionMap: Map<String, Map<String, String>>, tickPrefix: Long): Pitch? {
        val pit = sectionMap["PitchBendBPList"]?.entries?.mapNotNull {
            val pos = it.key.toLongOrNull() ?: return@mapNotNull null
            val value = it.value.toIntOrNull() ?: return@mapNotNull null
            VocaloidPartPitchData.Event(pos - tickPrefix, value = value)
        } ?: listOf()
        val pbs = sectionMap["PitchBendSensBPList"]?.entries?.mapNotNull {
            val pos = it.key.toLongOrNull() ?: return@mapNotNull null
            val value = it.value.toIntOrNull() ?: return@mapNotNull null
            VocaloidPartPitchData.Event(pos - tickPrefix, value = value)
        } ?: listOf()
        return pitchFromVocaloidParts(
            listOf(
                VocaloidPartPitchData(
                    startPos = 0,
                    pit = pit,
                    pbs = pbs,
                ),
            ),
        )
    }

    private fun generatePitchTexts(pitch: Pitch, tickPrefix: Int, notes: List<Note>): List<String> =
        mutableListOf<String>().apply {
            val pitchRawData = pitch.generateForVocaloid(notes) ?: return@apply
            if (pitchRawData.pit.isNotEmpty()) {
                add("[PitchBendBPList]")
                pitchRawData.pit.forEach {
                    val pos = it.pos + tickPrefix
                    add("$pos=${it.value}")
                }
            }
            if (pitchRawData.pbs.isNotEmpty()) {
                add("[PitchBendSensBPList]")
                pitchRawData.pbs.forEach {
                    val pos = it.pos + tickPrefix
                    add("$pos=${it.value}")
                }
            }
        }

    private fun generateTrackText(
        track: Track,
        tickPrefix: Int,
        measurePrefix: Int,
        project: Project,
        features: List<Feature>,
    ): String {
        val notesLines = mutableListOf<String>()
        val lyricsLines = mutableListOf<String>()
        val tickLists = track.notes.map { it.tickOn + tickPrefix }
        track.notes.forEach { note ->
            val number = note.id + 1
            notesLines.apply {
                add("[ID#${number.padStartZero(4)}]")
                add("Type=Anote")
                add("Length=${note.length}")
                add("Note#=${note.key}")
                add("Dynamics=64")
                add("PMBendDepth=0")
                add("PMBendLength=0")
                add("PMbPortamentoUse=0")
                add("DEMdecGainRate=0")
                add("DEMaccent=0")
                add("LyricHandle=h#${number.padStartZero(4)}")
            }
            lyricsLines.apply {
                add("[h#${number.padStartZero(4)}]")
                add("L0=\"${note.lyric}\",\"${note.phoneme ?: "a"}\",0.000000,64,0,0")
            }
        }

        return mutableListOf<String>().apply {
            add("[Common]")
            add("Version=DSB301")
            add("Name=${track.name}")
            add("Color=181,162,123")
            add("DynamicsMode=1")
            add("PlayMode=1")
            if (track.id == 0) {
                add("[Master]")
                add("PreMeasure=$measurePrefix")
                add("[Mixer]")
                add("MasterFeder=0")
                add("MasterPanpot=0")
                add("MasterMute=0")
                add("OutputMode=0")
                add("Tracks=${project.tracks.count()}")
                for (i in 0 until project.tracks.count()) {
                    add("Feder$i=0")
                    add("Panpot$i=0")
                    add("Mute$i=0")
                    add("Solo$i=0")
                }
            }
            add("[EventList]")
            add("0=ID#0000")
            tickLists.forEachIndexed { index, tick ->
                add("$tick=ID#${(index + 1).padStartZero(4)}")
            }
            add("${track.notes.last().tickOff + tickPrefix}=EOS")
            add("[ID#0000]")
            add("Type=Singer")
            add("IconHandle=h#0000")
            addAll(notesLines)
            add("[h#0000]")
            add("IconID=\$07010000")
            add("IDS=Miku")
            add("Original=0")
            add("Caption=")
            add("Length=1")
            add("Language=0")
            add("Program=0")
            addAll(lyricsLines)
            if (features.contains(Feature.ConvertPitch) && track.pitch != null) {
                addAll(generatePitchTexts(track.pitch, tickPrefix, track.notes))
            }
        }.joinToString("\n")
    }

    private fun generateTrack(
        track: Track,
        tickPrefix: Int,
        measurePrefix: Int,
        project: Project,
        features: List<Feature>,
    ): List<Byte> {
        val bytes = mutableListOf<Byte>()
        bytes.add(0x00)
        bytes.addAll(MidiUtil.MetaType.TrackName.eventHeaderBytes)
        bytes.addString(track.name, Mid.IS_LITTLE_ENDIAN, lengthInVariableLength = true)
        var textBytes = generateTrackText(track, tickPrefix, measurePrefix, project, features)
            .encode("SJIS")
            .toList()
        val textEvents = mutableListOf<List<Byte>>()
        while (textBytes.isNotEmpty()) {
            val id = textEvents.count()
            val idStringLength = kotlin.math.log(id.coerceAtLeast(1).toFloat(), 10000f).toInt() + 1 * 4
            val idString = id.padStartZero(idStringLength)
            val header = "DM:$idString:".asByteTypedArray()
            val availableByteSize = 127 - header.size
            textEvents.add(header.toList() + textBytes.take(availableByteSize))
            textBytes = textBytes.drop(availableByteSize)
        }
        textEvents.forEach {
            bytes.add(0x00)
            bytes.addAll(MidiUtil.MetaType.Text.eventHeaderBytes)
            bytes.addBlock(it, Mid.IS_LITTLE_ENDIAN, lengthInVariableLength = true)
        }
        bytes.add(0x00)
        bytes.addAll(MidiUtil.MetaType.EndOfTrack.eventHeaderBytes)
        bytes.add(0x00)
        return bytes
    }

    fun generate(project: Project, features: List<Feature>, format: Format): ExportResult {
        val projectFixed = project.lengthLimited(MAX_VSQ_OUTPUT_TICK)
            .copy(measurePrefix = project.measurePrefix.coerceIn(MIN_MEASURE_OFFSET, MAX_MEASURE_OFFSET))
            .withoutEmptyTracks()
        val content = projectFixed?.let {
            Mid.generateContent(it) { track, tickPrefix, measurePrefix ->
                generateTrack(track, tickPrefix, measurePrefix, project, features)
            }
        } ?: throw EmptyProjectException()
        val blob = Blob(arrayOf(content), BlobPropertyBag("application/octet-stream"))
        val name = format.getFileName(projectFixed.name)
        return ExportResult(
            blob,
            name,
            listOfNotNull(
                if (projectFixed.hasXSampaData) null else ExportNotification.PhonemeResetRequiredVSQ,
                if (features.contains(Feature.ConvertPitch)) ExportNotification.PitchDataExported else null,
                if (project == projectFixed) null else ExportNotification.DataOverLengthLimitIgnored,
            ),
        )
    }

    private const val MAX_VSQ_OUTPUT_TICK = 4096L * TICKS_IN_FULL_NOTE
    private const val MIN_MEASURE_OFFSET = 1
    private const val MAX_MEASURE_OFFSET = 8
}
