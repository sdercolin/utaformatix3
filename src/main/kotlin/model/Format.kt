package model

import io.VsqLike
import model.Feature.ConvertPitch
import model.JapaneseLyricsType.KanaCv
import model.JapaneseLyricsType.KanaVcv
import model.JapaneseLyricsType.RomajiCv
import model.JapaneseLyricsType.RomajiVcv
import org.w3c.files.File
import util.extensionName

enum class Format(
    val extension: String,
    val otherExtensions: List<String> = listOf(),
    val multipleFile: Boolean = false,
    val parser: suspend (List<File>, ImportParams) -> Project,
    val generator: suspend (Project, List<Feature>) -> ExportResult,
    val possibleLyricsTypes: List<JapaneseLyricsType>,
    val suggestedLyricType: JapaneseLyricsType? = null,
    val availableFeaturesForGeneration: List<Feature> = listOf(),
    private val customMatcher: (suspend (File) -> Boolean)? = null,
    private val alias: String? = null,
) {
    Vsqx(
        "vsqx",
        parser = { files, params ->
            io.Vsqx.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Vsqx.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch),
    ),
    Vpr(
        "vpr",
        parser = { files, params ->
            io.Vpr.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Vpr.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch),
    ),
    Ust(
        "ust",
        multipleFile = true,
        parser = { files, params ->
            io.Ust.parse(files, params)
        },
        generator = { project, features ->
            io.Ust.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, RomajiVcv, KanaCv, KanaVcv),
        availableFeaturesForGeneration = listOf(ConvertPitch),
    ),
    Ustx(
        "ustx",
        multipleFile = false,
        parser = { files, params ->
            io.Ustx.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Ustx.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, RomajiVcv, KanaCv, KanaVcv),
        availableFeaturesForGeneration = listOf(ConvertPitch),
    ),
    Ccs(
        "ccs",
        parser = { files, params ->
            io.Ccs.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Ccs.generate(project, features)
        },
        possibleLyricsTypes = listOf(KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch),
    ),
    Svp(
        "svp",
        parser = { files, params ->
            io.Svp.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Svp.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch),
    ),
    S5p(
        "s5p",
        parser = { files, params ->
            io.S5p.parse(files.first(), params)
        },
        generator = { project, features ->
            io.S5p.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch),
    ),
    MusicXml(
        "musicxml",
        otherExtensions = listOf("xml"),
        parser = { files, _ ->
            io.MusicXml.parse(files.first())
        },
        generator = { project, _ ->
            io.MusicXml.generate(project)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        suggestedLyricType = KanaCv,
    ),
    Dv(
        "dv",
        parser = { files, params ->
            io.Dv.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Dv.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        suggestedLyricType = RomajiCv,
        availableFeaturesForGeneration = listOf(ConvertPitch),
    ),
    Vsq(
        "vsq",
        parser = { files, params ->
            io.Vsq.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Vsq.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch),
    ),
    VocaloidMid(
        "mid",
        parser = { files, params ->
            io.VocaloidMid.parse(files.first(), params)
        },
        generator = { project, features ->
            io.VocaloidMid.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch),
        customMatcher = { file -> file.extensionName == "mid" && VsqLike.match(file) },
        alias = "Mid (VOCALOID)",
    ),
    StandardMid(
        "mid",
        parser = { files, _ ->
            io.StandardMid.parse(files.first())
        },
        generator = { project, _ ->
            io.StandardMid.generate(project)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(),
        customMatcher = { file -> file.extensionName == "mid" && !VsqLike.match(file) },
        alias = "Mid (Standard)",
    ),
    Ppsf(
        "ppsf",
        parser = { files, _ ->
            io.Ppsf.parse(files.first())
        },
        generator = { _, _ ->
            throw NotImplementedError()
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
    ),
    UfData(
        "ufdata",
        parser = { files, params ->
            io.UfData.parse(files.first(), params)
        },
        generator = { project, features ->
            io.UfData.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, RomajiVcv, KanaCv, KanaVcv),
        availableFeaturesForGeneration = listOf(ConvertPitch),
    );

    private val allExtensions get() = listOf(extension) + otherExtensions

    suspend fun match(files: List<File>): Boolean {
        val customMatcher = customMatcher
        if (customMatcher != null) {
            return files.all { customMatcher(it) }
        }
        val extensions = files.map { it.extensionName }.distinct()
        return extensions.size == 1 && extensions.first() in allExtensions
    }

    fun getFileName(name: String): String = "$name.$extension"

    val displayName get() = alias ?: name

    companion object {

        val importable: List<Format>
            get() = listOf(
                Vsqx,
                Vpr,
                Vsq,
                VocaloidMid,
                Ust,
                Ustx,
                Ccs,
                MusicXml,
                Svp,
                S5p,
                Dv,
                Ppsf,
                StandardMid,
                UfData,
            )

        val exportable: List<Format>
            get() = listOf(
                Vsqx,
                Vpr,
                Vsq,
                VocaloidMid,
                Ust,
                Ustx,
                Ccs,
                MusicXml,
                Svp,
                S5p,
                Dv,
                StandardMid,
                UfData,
            )
    }
}
