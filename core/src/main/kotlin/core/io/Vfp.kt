package core.io

import core.external.JsZip
import core.external.JsZipOption
import core.external.Resources
import core.model.DEFAULT_BPM
import core.model.DEFAULT_KEY
import core.model.DEFAULT_METER_HIGH
import core.model.DEFAULT_METER_LOW
import core.model.ExportNotification
import core.model.ExportResult
import core.model.Feature
import core.model.FeatureConfig
import core.model.Format
import core.model.ImportParams
import core.model.ImportWarning
import core.model.Pitch
import core.model.TICKS_IN_BEAT
import core.model.TimeSignature
import core.model.contains
import core.process.lyrics.japanese.toRomaji
import core.process.pitch.getAbsoluteData
import core.process.validateNotes
import core.util.nameWithoutExtension
import core.util.readBinary
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.w3c.files.Blob
import org.w3c.files.File
import kotlin.math.roundToLong

/**
 * VOX Factory (https://voxfactory.app) project files.
 *
 * A `.vfp` file is a zip archive containing `project.json` (the project state) and a `resources/`
 * folder holding audio data. Positions are stored both in ticks (480 per quarter note, the same
 * resolution we use internally) and in seconds; the tick values are authoritative, the seconds are
 * a cache derived from the project tempo.
 *
 * The format holds a single tempo and a single time signature for the whole project, so only the
 * first of each survives a conversion.
 */
object Vfp {
    suspend fun parse(
        file: File,
        params: ImportParams,
    ): core.model.Project {
        val content = readContent(file)
        val warnings = mutableListOf<ImportWarning>()
        val tracks =
            ordered(content.trackBank, content.trackOrder)
                .filter { it.type in VOCAL_TYPES }
                .mapIndexed { index, track -> parseTrack(track, index, params) }
        val bpm = content.tempo
        if (bpm == null) warnings.add(ImportWarning.TempoNotFound)
        val tempos = listOf(core.model.Tempo(tickPosition = 0L, bpm = bpm ?: DEFAULT_BPM))
        val timeSignature = content.timeSignature
        if (timeSignature == null) warnings.add(ImportWarning.TimeSignatureNotFound)
        val timeSignatures =
            listOf(
                TimeSignature(
                    measurePosition = 0,
                    numerator = timeSignature?.getOrNull(0) ?: DEFAULT_METER_HIGH,
                    denominator = timeSignature?.getOrNull(1) ?: DEFAULT_METER_LOW,
                ),
            )
        return core.model.Project(
            format = format,
            inputFiles = listOf(file),
            name = content.projectName?.takeUnless { it.isBlank() } ?: file.nameWithoutExtension,
            tracks = tracks,
            timeSignatures = timeSignatures,
            tempos = tempos,
            measurePrefix = 0,
            importWarnings = warnings,
        )
    }

    private fun parseTrack(
        track: Track,
        trackIndex: Int,
        params: ImportParams,
    ): core.model.Track {
        val clips = ordered(track.clipBank, track.clipOrder).filter { it.type in VOCAL_TYPES }
        val notes =
            clips
                .flatMap { clip ->
                    val tickOffset = clip.tickOffset
                    ordered(clip.noteBank, clip.noteOrder).map { tickOffset to it }
                }.sortedBy { (tickOffset, note) -> tickOffset + note.ticks }
                .mapIndexed { index, (tickOffset, note) ->
                    core.model.Note(
                        id = index,
                        tickOn = tickOffset + note.ticks,
                        tickOff = tickOffset + note.ticks + note.durationTicks,
                        lyric = note.name.takeUnless { it.isNullOrBlank() } ?: params.defaultLyric,
                        key = note.midi,
                    )
                }
        val pitch = if (params.simpleImport) null else parsePitch(clips)
        return core.model
            .Track(
                id = trackIndex,
                name = track.name?.takeUnless { it.isBlank() } ?: "Track ${trackIndex + 1}",
                notes = notes,
                pitch = pitch,
            ).validateNotes()
    }

    /**
     * Pitch points carry an absolute key value, and a point with `connected` set to false starts a
     * new segment, which we represent with a null value at its position.
     */
    private fun parsePitch(clips: List<Clip>): Pitch? {
        val data =
            clips
                .flatMap { clip ->
                    val tickOffset = clip.tickOffset
                    ordered(clip.pitchPointBank, clip.pitchPointOrder).map { tickOffset to it }
                }.sortedBy { (tickOffset, point) -> tickOffset + point.quarter * TICKS_IN_BEAT }
                .flatMapIndexed { index, (tickOffset, point) ->
                    val tick = tickOffset + (point.quarter * TICKS_IN_BEAT).roundToLong()
                    val breakPoint = if (index > 0 && point.connected == false) listOf(tick to null) else listOf()
                    breakPoint + listOf(tick to point.pitch)
                }
        return data.takeIf { it.isNotEmpty() }?.let { Pitch(it, isAbsolute = true) }
    }

    /** A bank is read in the order the project gives for it, falling back to the bank's own. */
    private fun <T> ordered(
        bank: Map<String, T>,
        order: List<String>,
    ) = order.ifEmpty { bank.keys.toList() }.mapNotNull { bank[it] }

    private suspend fun readContent(file: File): Project {
        val binary = file.readBinary()
        val zip = JsZip().loadAsync(binary).await()
        val entry = requireNotNull(zip.file(PROJECT_JSON_PATH)) { "$PROJECT_JSON_PATH not found in the archive" }
        val text = entry.async("string").await() as String
        return jsonSerializer.decodeFromString(Project.serializer(), text)
    }

    suspend fun generate(
        project: core.model.Project,
        features: List<FeatureConfig>,
    ): ExportResult {
        val jsonText = generateContent(project, features)
        val zip = JsZip()
        zip.folder(RESOURCES_FOLDER_NAME)
        zip.file(PROJECT_JSON_PATH, jsonText)
        val option =
            JsZipOption().also {
                it.type = "blob"
                it.mimeType = "application/octet-stream"
            }
        val blob = zip.generateAsync(option).await() as Blob
        return ExportResult(
            blob,
            format.getFileName(project.name),
            listOfNotNull(
                if (project.tempos.size > 1) ExportNotification.TempoIgnored else null,
                if (project.timeSignatures.size > 1) ExportNotification.TimeSignatureIgnored else null,
                if (features.contains(Feature.ConvertPitch)) ExportNotification.PitchDataExported else null,
            ),
        )
    }

    private fun generateContent(
        project: core.model.Project,
        features: List<FeatureConfig>,
    ): String {
        val template = jsonSerializer.decodeFromString(Project.serializer(), Resources.vfpTemplate)
        val templateTrack = template.trackBank.values.first { it.type in VOCAL_TYPES }
        val tempo = project.tempos.firstOrNull()?.bpm ?: DEFAULT_BPM
        val timeSignature = project.timeSignatures.firstOrNull() ?: TimeSignature.default
        val barTicks = timeSignature.ticksInMeasure.toLong()
        val trackBank =
            project.tracks
                .mapIndexed { trackIndex, track ->
                    val trackKey = "$KEY_PREFIX-tr$trackIndex"
                    trackKey to generateTrack(templateTrack, track, trackKey, tempo, barTicks, features)
                }.toMap()
        val endQuarter =
            trackBank.values
                .flatMap { it.clipBank.values }
                .maxOfOrNull { it.startQuarter + it.length } ?: 0.0
        return jsonSerializer.encodeToString(
            Project.serializer(),
            template.copy(
                projectName = project.name,
                tempo = tempo,
                timeSignature = listOf(timeSignature.numerator, timeSignature.denominator),
                loop = listOf(0.0, endQuarter.coerceAtLeast(DEFAULT_LOOP_QUARTERS)),
                trackBank = trackBank,
                trackOrder = trackBank.keys.toList(),
                selectedTrackBank = listOfNotNull(trackBank.keys.firstOrNull()),
            ),
        )
    }

    private fun generateTrack(
        templateTrack: Track,
        track: core.model.Track,
        trackKey: String,
        tempo: Double,
        barTicks: Long,
        features: List<FeatureConfig>,
    ): Track {
        val annotated = track.notes.zip(track.notes.toPronunciations())
        val pitch = if (features.contains(Feature.ConvertPitch)) generatePitch(track) else listOf()
        var pitchFrom = 0
        val clipBank =
            track.notes
                .clipRegions(barTicks)
                .mapIndexed { clipIndex, region ->
                    val clipKey = "$trackKey-cl$clipIndex"
                    // Pitch points run in ascending order, so each clip resumes where the last began.
                    while (pitchFrom < pitch.size && pitch[pitchFrom].first < region.startTick) pitchFrom++
                    clipKey to generateClip(clipKey, annotated.slice(region.notes), region, pitch, pitchFrom, tempo)
                }.toMap()
        return templateTrack
            .copy(
                name = track.name,
                clipBank = clipBank,
                clipOrder = clipBank.keys.toList(),
            ).withRekeyedDevices(trackKey)
    }

    private fun generateClip(
        clipKey: String,
        notes: List<Pair<core.model.Note, Pronunciation>>,
        region: ClipRegion,
        pitch: List<Pair<Long, PitchPoint>>,
        pitchFrom: Int,
        tempo: Double,
    ): Clip {
        val noteBank =
            notes
                .mapIndexed { noteIndex, (note, pronunciation) ->
                    val tickInClip = note.tickOn - region.startTick
                    "$clipKey-nt$noteIndex" to
                        Note(
                            time = tickInClip.tickToSecond(tempo),
                            midi = note.key,
                            name = pronunciation.name,
                            syllable = pronunciation.syllable,
                            ticks = tickInClip,
                            duration = note.length.tickToSecond(tempo),
                            durationTicks = note.length,
                        )
                }.toMap()
        val pitchPointBank =
            (pitchFrom until pitch.size)
                .asSequence()
                .takeWhile { pitch[it].first <= region.endTick }
                .mapIndexed { pointIndex, index ->
                    val (tick, point) = pitch[index]
                    "$clipKey-pp$pointIndex" to
                        point.copy(quarter = (tick - region.startTick).toDouble() / TICKS_IN_BEAT)
                }.toMap()
        return Clip(
            // The editor labels a clip with the lyrics it holds, not with the track name.
            name = notes.joinToString(" ") { (note, _) -> note.lyric },
            startQuarter = region.startTick.toDouble() / TICKS_IN_BEAT,
            length = (region.endTick - region.startTick).toDouble() / TICKS_IN_BEAT,
            noteBank = noteBank,
            noteOrder = noteBank.keys.toList(),
            pitchPointBank = pitchPointBank,
            pitchPointOrder = pitchPointBank.keys.toList(),
            nextNoteIndex = noteBank.size,
            endOfTrackTicks = notes.maxOf { (note, _) -> note.tickOff } - region.startTick,
        )
    }

    /**
     * Where one clip of a track falls: which of its notes it holds, and the ticks it spans.
     *
     * The editor keeps one clip per phrase -- it cuts wherever the notes rest for longer than a bar,
     * on the bar line -- and caps a synthesis at `maxSynthBar` bars, so a phrase longer than the cap
     * is cut again, at its widest rest or, when its notes run on without one, before the note that
     * would overrun. A clip reaches to the bar line past its last note and back to the one before
     * its first, so two clips cut where the notes rest for less than a bar would share that bar; the
     * editor resolves clips that overlap and the loser stops playing and generating, so where there
     * is no bar line to spare the two meet at the note that starts the later clip instead.
     */
    private data class ClipRegion(
        val notes: IntRange,
        val startTick: Long,
        val endTick: Long,
    )

    private fun List<core.model.Note>.clipRegions(barTicks: Long): List<ClipRegion> {
        if (isEmpty()) return listOf()
        val phrases = listOf(0) + (1..lastIndex).filter { this[it].tickOn - this[it - 1].tickOff > barTicks }
        val ranges =
            phrases
                .mapIndexed { index, start -> start until (phrases.getOrNull(index + 1) ?: size) }
                .flatMap { range -> cappedRanges(range, barTicks) }
        val startTicks = ranges.map { this[it.first].tickOn.floorToBar(barTicks) }.toMutableList()
        val endTicks = ranges.map { this[it.last].tickOff.ceilToBar(barTicks) }.toMutableList()
        for (index in 0 until ranges.size - 1) {
            if (endTicks[index] <= startTicks[index + 1]) continue
            val boundary = this[ranges[index + 1].first].tickOn
            endTicks[index] = boundary
            startTicks[index + 1] = boundary
        }
        return ranges.mapIndexed { index, range -> ClipRegion(range, startTicks[index], endTicks[index]) }
    }

    private fun List<core.model.Note>.cappedRanges(
        range: IntRange,
        barTicks: Long,
    ): List<IntRange> {
        val limit = this[range.first].tickOn.floorToBar(barTicks) + barTicks * MAX_CLIP_BARS
        if (this[range.last].tickOff <= limit) return listOf(range)
        val fitting = range.filter { this[it].tickOff <= limit }
        val cut =
            fitting
                .drop(1)
                .maxByOrNull { this[it].tickOn - this[it - 1].tickOff }
                ?.takeIf { this[it].tickOn > this[it - 1].tickOff }
                ?: fitting.lastOrNull()?.plus(1)
                ?: (range.first + 1)
        return listOf(range.first until cut) + cappedRanges(cut..range.last, barTicks)
    }

    /**
     * Written out rather than built on [core.process.pitch.bpmToSecPerTick], which folds the rate
     * into one constant and so rounds differently: the editor stores these seconds beside the ticks
     * it derives them from, and they should round the way a reader of that formula expects.
     */
    private fun Long.tickToSecond(bpm: Double) = this.toDouble() / TICKS_IN_BEAT * SECONDS_IN_MINUTE / bpm

    private fun Long.floorToBar(barTicks: Long) = this / barTicks * barTicks

    private fun Long.ceilToBar(barTicks: Long) = (this + barTicks - 1) / barTicks * barTicks

    private fun generatePitch(track: core.model.Track): List<Pair<Long, PitchPoint>> {
        val data = track.pitch?.getAbsoluteData(track.notes) ?: return listOf()
        var connected = false
        return data.mapNotNull { (tick, value) ->
            if (value == null) {
                connected = false
                return@mapNotNull null
            }
            tick to PitchPoint(pitch = value, connected = connected).also { connected = true }
        }
    }

    /**
     * Device keys are only meaningful within their own track, but VOX Factory hands out globally
     * unique ones, so the chain cloned from the template is rekeyed for every track.
     */
    private fun Track.withRekeyedDevices(trackKey: String): Track {
        val newKeys = deviceOrder.mapIndexed { index, key -> key to "$trackKey-de$index" }.toMap()
        return copy(
            deviceBank = deviceBank.mapNotNull { (key, device) -> newKeys[key]?.let { it to device } }.toMap(),
            deviceOrder = deviceOrder.mapNotNull { newKeys[it] },
        )
    }

    /**
     * VOX Factory keeps the displayed lyric in `name` and the pronunciation in `syllable`, written
     * in a romanization of its own rather than plain romaji: the vowel `u` becomes `eu` and vowel
     * sequences are collapsed. Three kana are spelled from their neighbours instead of themselves,
     * so a whole track is resolved at once:
     *
     * - a small tsu takes over the consonant that opens the next mora,
     * - the moraic n becomes `n`, `m` or `ng` according to the consonant that follows it,
     * - a long vowel mark is a slur: the editor names it `-` and carries over the vowel of the
     *   mora before it, the same way it splits a syllable when a note is cut in two.
     *
     * Only Japanese is covered. The editor reads English pronunciations out of a dictionary we do
     * not have, so a lyric that is neither kana nor already a valid syllable keeps the editor's own
     * default.
     */
    private fun List<core.model.Note>.toPronunciations(): List<Pronunciation> {
        val lyrics = map { it.lyric.trim().katakanaToHiragana() }
        val hangul = lyrics.toHangulSpellings()
        val moras =
            lyrics.mapIndexed { index, lyric ->
                if (lyric.isMarker()) null else hangul[index]?.parseSyllable() ?: lyric.toSyllable()
            }
        val next = arrayOfNulls<ParsedSyllable>(moras.size)
        val previous = arrayOfNulls<ParsedSyllable>(moras.size)
        for (index in moras.indices.reversed()) {
            next[index] = if (index + 1 < moras.size) moras[index + 1] ?: next[index + 1] else null
        }
        for (index in moras.indices) {
            previous[index] = if (index > 0) moras[index - 1] ?: previous[index - 1] else null
        }
        return lyrics.mapIndexed { index, lyric ->
            when {
                lyric == SOKUON -> Pronunciation(this[index].lyric, next[index]?.onsets?.firstOrNull().orEmpty())
                lyric in MORAIC_N -> Pronunciation(this[index].lyric, next[index].moraicNSpelling())
                lyric.isSlur() -> {
                    val carried = lyric.drop(1).takeIf { it.isNotEmpty() }?.toSyllable() ?: previous[index]
                    Pronunciation(SLUR, carried?.tail.orEmpty())
                }
                else -> Pronunciation(this[index].lyric, moras[index]?.toString() ?: DEFAULT_SYLLABLE)
            }
        }
    }

    /**
     * Hangul is written one syllable to a note, so the editor's spelling of it falls out of taking
     * that syllable apart: an onset, a nucleus, and a coda levelled to one of seven sounds. A silent
     * onset drops out, a coda ㄹ is `l` where an onset ㄹ is `r`, and an onset ㄹ after a coda ㄹ
     * becomes `l` as well.
     *
     * The editor first runs the lyric through Korean phonology -- liaison, palatalization, tensing
     * -- which is a library of its own, so a syllable is spelled here as it is written rather than
     * as the word around it would make it sound. Re-entering the lyrics in the editor's own lyrics
     * box applies the rest.
     */
    private fun List<String>.toHangulSpellings(): List<String?> {
        var previousCoda = ""
        return map { lyric ->
            val code = (lyric.singleOrNull()?.code ?: 0) - HANGUL_BASE
            if (code !in 0 until HANGUL_ONSETS.size * HANGUL_NUCLEI.size * HANGUL_CODAS.size) {
                previousCoda = ""
                return@map null
            }
            val onset = HANGUL_ONSETS[code / (HANGUL_NUCLEI.size * HANGUL_CODAS.size)]
            val nucleus = HANGUL_NUCLEI[code / HANGUL_CODAS.size % HANGUL_NUCLEI.size]
            val coda = HANGUL_CODAS[code % HANGUL_CODAS.size]
            val liaised = if (previousCoda == "l" && onset == "r") "l" else onset
            previousCoda = coda
            liaised + nucleus + coda
        }
    }

    private fun String.isMarker() = this == SOKUON || this in MORAIC_N || isSlur()

    /** A slur carries the vowel before it, either written after the mark or taken from that note. */
    private fun String.isSlur() = SLUR_MARKS.any { startsWith(it) }

    private fun String.toSyllable() = toRomaji().toVoxRomanization().parseSyllable()

    private data class Pronunciation(
        val name: String,
        val syllable: String,
    )

    private fun String.katakanaToHiragana() =
        map { char -> if (char in KATAKANA_RANGE) char - KATAKANA_TO_HIRAGANA_OFFSET else char }
            .joinToString("")

    /** The editor spells the moraic n after the first letter of what follows, not its whole onset. */
    private fun ParsedSyllable?.moraicNSpelling() =
        when (this?.onsets?.firstOrNull()?.firstOrNull()) {
            'k', 'g' -> "ng"
            't', 'd', 's', 'z', 'n', 'r' -> "n"
            'p', 'b', 'm' -> "m"
            else -> "ng"
        }

    private fun String.toVoxRomanization() =
        lowercase()
            .replace("ei", "i")
            .replace("ey", "y")
            .replace("ou", "u")
            .replace("ua", "a")
            .replace("ue", "e")
            .replace("ui", "i")
            .replace("uo", "o")
            .replace("uu", "u")
            .replace("fy", "f")
            .replace("vy", "v")
            .replace("u", "eu")

    /** Mirrors the editor's own parser: onsets up to the nucleus, codas after it. */
    private fun String.parseSyllable(): ParsedSyllable? {
        if (isEmpty()) return null
        val onsets = mutableListOf<String>()
        val nuclei = StringBuilder()
        val codas = StringBuilder()
        var index = 0
        while (index < length) {
            val unit = SYLLABLE_UNITS.firstOrNull { startsWith(it, index) } ?: return null
            when {
                unit in SYLLABLE_VOWELS -> nuclei.append(unit)
                nuclei.isEmpty() -> onsets.add(unit)
                else -> codas.append(unit)
            }
            index += unit.length
        }
        return if (nuclei.isEmpty()) null else ParsedSyllable(onsets, "$nuclei$codas")
    }

    private data class ParsedSyllable(
        val onsets: List<String>,
        /** What the editor gives a slur note when it splits this syllable across two notes. */
        val tail: String,
    ) {
        override fun toString() = onsets.joinToString("") + tail
    }

    private val jsonSerializer =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private const val PROJECT_JSON_PATH = "project.json"
    private const val RESOURCES_FOLDER_NAME = "resources"
    private const val KEY_PREFIX = "utaformatix"
    private const val SECONDS_IN_MINUTE = 60.0
    private const val DEFAULT_LOOP_QUARTERS = 16.0
    private const val DEFAULT_SYLLABLE = "la"

    /** `maxSynthBar` in the editor's own constants: the longest stretch a synthesis covers. */
    private const val MAX_CLIP_BARS = 10
    private const val SOKUON = "っ"

    /** The kana, plus the spellings a lyric carries when it was never converted out of romaji. */
    private val MORAIC_N = listOf("ん", "n", "m", "ng")
    private val SLUR_MARKS = listOf("ー", "-", "+")
    private const val HANGUL_BASE = 0xAC00
    private val HANGUL_ONSETS =
        listOf("g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "cc", "ch", "k", "t", "p", "h")
    private val HANGUL_NUCLEI =
        listOf(
            "a",
            "ae",
            "ya",
            "yae",
            "ah",
            "e",
            "yah",
            "ye",
            "o",
            "wa",
            "wae",
            "we",
            "yo",
            "u",
            "wah",
            "we",
            "wi",
            "yu",
            "eu",
            "eui",
            "i",
        )

    /** Every coda is levelled to one of the seven sounds a Korean syllable can end on. */
    private val HANGUL_CODAS =
        listOf(
            "",
            "k",
            "k",
            "k",
            "n",
            "n",
            "n",
            "t",
            "l",
            "l",
            "l",
            "l",
            "l",
            "l",
            "l",
            "l",
            "m",
            "p",
            "p",
            "t",
            "t",
            "ng",
            "t",
            "t",
            "k",
            "t",
            "p",
            "t",
        )
    private const val SLUR = "-"
    private val KATAKANA_RANGE = 'ァ'..'ヶ'
    private const val KATAKANA_TO_HIRAGANA_OFFSET = 0x60
    private val VOCAL_TYPES = listOf("vocal", "voice")

    /**
     * The onsets/codas and nuclei accepted by VOX Factory's syllable parser.
     */
    private val SYLLABLE_CONSONANTS =
        "m b p pp v f dh th n r rr l d t tt s ss z cc sh zh j ch sr zr cr y ng g k kk x h w '".split(" ")
    private val SYLLABLE_VOWELS = "a ae ah ao e eo o i ih u uh eu ue".split(" ")

    /** Longest first, so that digraphs are matched before their single-letter prefixes. */
    private val SYLLABLE_UNITS = (SYLLABLE_CONSONANTS + SYLLABLE_VOWELS).sortedByDescending { it.length }

    @Serializable
    private data class Project(
        var version: String? = null,
        var projectName: String? = null,
        var tempo: Double? = null,
        var timeSignature: List<Int>? = null,
        var loop: List<Double>? = null,
        var master: JsonElement? = null,
        var trackBank: Map<String, Track> = mapOf(),
        var trackOrder: List<String> = listOf(),
        var selectedTrackBank: List<String> = listOf(),
        var selectedClipBank: List<JsonElement> = listOf(),
        var selectedNoteBank: List<JsonElement> = listOf(),
        var selectedPitchPointBank: List<JsonElement> = listOf(),
        var audioDataBank: Map<String, JsonElement> = mapOf(),
    )

    @Serializable
    private data class Track(
        var type: String = "vocal",
        var name: String? = null,
        var instrument: JsonElement? = null,
        var h: JsonElement? = null,
        var color: String? = null,
        var volume: Double? = null,
        var pan: Double? = null,
        var solo: Boolean? = null,
        var mute: Boolean? = null,
        var arm: Boolean? = null,
        var clipBank: Map<String, Clip> = mapOf(),
        var clipOrder: List<String> = listOf(),
        var devicePreset: String? = null,
        var deviceBank: Map<String, JsonElement> = mapOf(),
        var deviceOrder: List<String> = listOf(),
        var audioViewProperty: JsonElement? = null,
    )

    @Serializable
    private data class Clip(
        var type: String = "vocal",
        var lengthType: String = "quarter",
        var name: String? = null,
        var startQuarter: Double = 0.0,
        var offsetQuarter: Double = 0.0,
        var length: Double = 0.0,
        var noteBank: Map<String, Note> = mapOf(),
        var noteOrder: List<String> = listOf(),
        var pitchPointBank: Map<String, PitchPoint> = mapOf(),
        var pitchPointOrder: List<String> = listOf(),
        var nextNoteIndex: Int = 0,
        var useSource: Boolean = true,
        var pinnedAudioDataOrder: List<String> = listOf(),
        var audioDataOrder: List<String> = listOf(),
        var sourceAudioDataKey: String? = null,
        var audioDataKey: String? = null,
        var audioDataQuarter: Double = 0.0,
        var metadata: Metadata = Metadata(),
        var endOfTrackTicks: Long? = null,
        var thumbnailDataKey: String? = null,
        var processKey: String? = null,
    ) {
        /** Notes and pitch points are positioned relative to the clip's content origin. */
        val tickOffset get() = ((startQuarter - offsetQuarter) * TICKS_IN_BEAT).roundToLong()
    }

    @Serializable
    private data class Metadata(
        var style: String = "Normal.A",
        var accent: String = "en-us",
        var transpose: Int = 0,
        var harmonicRatio: Double = 1.0,
        var pitchDetection: String = "Accurate",
    )

    @Serializable
    private data class Note(
        var time: Double = 0.0,
        var midi: Int = DEFAULT_KEY,
        var name: String? = null,
        var syllable: String = DEFAULT_SYLLABLE,
        var velocity: Double = 1.0,
        var duration: Double = 0.0,
        var ticks: Long = 0L,
        var durationTicks: Long = 0L,
        var noteType: String? = null,
        var vibratoDepth: Double? = null,
        var preBend: Double? = null,
        var postBend: Double? = null,
        var harmonicRatio: Double? = null,
        var correctionParam: CorrectionParam = CorrectionParam(),
        var vibratoParam: VibratoParam = VibratoParam(),
        var growlParam: GrowlParam = GrowlParam(),
    )

    @Serializable
    private data class CorrectionParam(
        var modulation: Double = 1.0,
        var center: Double = 0.0,
    )

    @Serializable
    private data class VibratoParam(
        var amplitude: Double = 0.0,
        var frequency: Double = 5.0,
        var start: Double = 0.0,
        var phase: Double = 0.0,
        var fadeIn: Double = 0.2,
        var fadeOut: Double = 0.2,
    )

    @Serializable
    private data class GrowlParam(
        var start: Double = 0.0,
        var duration: Double = 0.0,
    )

    @Serializable
    private data class PitchPoint(
        var quarter: Double = 0.0,
        var pitch: Double = DEFAULT_KEY.toDouble(),
        var connected: Boolean? = null,
    )

    private val format = Format.Vfp
}
