package io

import external.JsYaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import model.Format
import model.ImportParams
import model.Project
import org.w3c.files.File
import util.readText

object Ustx {

    suspend fun parse(file: File, params: ImportParams): model.Project {
        val yamlText = file.readText()
        val yaml = JsYaml.load(yamlText)
        val jsonText = JSON.stringify(yaml)
        val project = jsonSerializer.decodeFromString(Project.serializer(), jsonText)
        console.log(project)
        return Project(
            format = Format.Ustx,
            inputFiles = listOf(file),
            name = "",
            tracks = listOf(),
            timeSignatures = listOf(),
            tempos = listOf(),
            measurePrefix = 0,
            importWarnings = listOf()
        )
    }

    private val jsonSerializer = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class Project(
        val name: String,
        val comment: String,
        @SerialName("output_dir") val outputDir: String,
        @SerialName("cache_dir") val cacheDir: String,
        @SerialName("ustx_version") val ustxVersion: Double,
        val bpm: Double,
        @SerialName("beat_per_bar") val beatPerBar: Int,
        @SerialName("beat_unit") val beatUnit: Int,
        val resolution: Int,
        val expressions: Map<String, Expression>,
        val tracks: List<Track>,
        @SerialName("voice_parts") val voiceParts: List<VoicePart>
    )

    @Serializable
    private data class Expression(
        val name: String,
        val abbr: String,
        val type: String,
        val min: Int,
        val max: Int,
        @SerialName("default_value") val defaultValue: Int,
        @SerialName("is_flag") val isFlag: Boolean,
        val flag: String? = null,
        val options: List<String>? = null
    )

    @Serializable
    private data class Track(
        val phonemizer: String,
        val mute: Boolean,
        val solo: Boolean,
        val volume: Double
    )

    @Serializable
    private data class VoicePart(
        val name: String,
        val comment: String,
        @SerialName("track_no") val trackNo: Int,
        val position: Long,
        val notes: List<Note>,
        val curves: List<Curve>
    )

    @Serializable
    private data class Note(
        val position: Long,
        val duration: Long,
        val tone: Int,
        val lyric: String,
        val pitch: Pitch,
        val vibrato: Vibrato
    )

    @Serializable
    private data class Pitch(
        val data: List<Datum>,
        @SerialName("snap_first") val snapFirst: Boolean
    )

    @Serializable
    private data class Datum(
        val x: Double,
        val y: Double,
        val shape: String
    )

    @Serializable
    private data class Vibrato(
        val length: Double,
        val period: Double,
        val depth: Double,
        val `in`: Double,
        val out: Double,
        val shift: Double,
        val drift: Double
    )

    @Serializable
    private data class Curve(
        val xs: List<Long>,
        val ys: List<Double>,
        val abbr: String
    )
}
