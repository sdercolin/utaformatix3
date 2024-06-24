package core.model

import core.io.VsqLike
import core.model.Feature.ConvertPhonemes
import core.model.Feature.ConvertPitch
import core.model.Feature.SplitProject
import core.model.JapaneseLyricsType.KanaCv
import core.model.JapaneseLyricsType.KanaVcv
import core.model.JapaneseLyricsType.RomajiCv
import core.model.JapaneseLyricsType.RomajiVcv
import core.util.extensionName
import org.w3c.files.File

enum class Format(
    val extension: String,
    val otherExtensions: List<String> = listOf(),
    val multipleFile: Boolean = false,
    val parser: suspend (List<File>, ImportParams) -> Project,
    val generator: suspend (Project, List<FeatureConfig>) -> ExportResult,
    val possibleLyricsTypes: List<JapaneseLyricsType>,
    val suggestedLyricType: JapaneseLyricsType? = null,
    val availableFeaturesForGeneration: List<Feature> = listOf(),
    private val customMatcher: (suspend (File) -> Boolean)? = null,
    private val alias: String? = null,
) {
    Vsqx(
        "vsqx",
        parser = { files, params ->
            core.io.Vsqx.parse(files.first(), params)
        },
        generator = { project, features ->
            core.io.Vsqx.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch, ConvertPhonemes),
    ),
    Vpr(
        "vpr",
        parser = { files, params ->
            core.io.Vpr.parse(files.first(), params)
        },
        generator = { project, features ->
            core.io.Vpr.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch, ConvertPhonemes),
    ),
    Ust(
        "ust",
        multipleFile = true,
        parser = { files, params ->
            core.io.Ust.parse(files, params)
        },
        generator = { project, features ->
            core.io.Ust.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, RomajiVcv, KanaCv, KanaVcv),
        availableFeaturesForGeneration = listOf(ConvertPitch),
    ),
    Ustx(
        "ustx",
        multipleFile = false,
        parser = { files, params ->
            core.io.Ustx.parse(files.first(), params)
        },
        generator = { project, features ->
            core.io.Ustx.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, RomajiVcv, KanaCv, KanaVcv),
        availableFeaturesForGeneration = listOf(ConvertPitch, ConvertPhonemes),
    ),
    Ccs(
        "ccs",
        parser = { files, params ->
            core.io.Ccs.parse(files.first(), params)
        },
        generator = { project, features ->
            core.io.Ccs.generate(project, features)
        },
        possibleLyricsTypes = listOf(KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch),
    ),
    Svp(
        "svp",
        parser = { files, params ->
            core.io.Svp.parse(files.first(), params)
        },
        generator = { project, features ->
            core.io.Svp.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch, SplitProject, ConvertPhonemes),
    ),
    S5p(
        "s5p",
        parser = { files, params ->
            core.io.S5p.parse(files.first(), params)
        },
        generator = { project, features ->
            core.io.S5p.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch),
    ),
    MusicXml(
        "musicxml",
        otherExtensions = listOf("xml"),
        parser = { files, params ->
            core.io.MusicXml.parse(files.first(), params)
        },
        generator = { project, _ ->
            core.io.MusicXml.generate(project)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        suggestedLyricType = KanaCv,
    ),
    Dv(
        "dv",
        parser = { files, params ->
            core.io.Dv.parse(files.first(), params)
        },
        generator = { project, features ->
            core.io.Dv.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        suggestedLyricType = RomajiCv,
        availableFeaturesForGeneration = listOf(ConvertPitch),
    ),
    Vsq(
        "vsq",
        parser = { files, params ->
            core.io.Vsq.parse(files.first(), params)
        },
        generator = { project, features ->
            core.io.Vsq.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch, ConvertPhonemes),
    ),
    VocaloidMid(
        "mid",
        parser = { files, params ->
            core.io.VocaloidMid.parse(files.first(), params)
        },
        generator = { project, features ->
            core.io.VocaloidMid.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch),
        customMatcher = { file -> file.extensionName == "mid" && VsqLike.match(file) },
        alias = "Mid (VOCALOID)",
    ),
    StandardMid(
        "mid",
        parser = { files, params ->
            core.io.StandardMid.parse(files.first(), params)
        },
        generator = { project, _ ->
            core.io.StandardMid.generate(project)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(),
        customMatcher = { file -> file.extensionName == "mid" && !VsqLike.match(file) },
        alias = "Mid (Standard)",
    ),
    Ppsf(
        "ppsf",
        parser = { files, params ->
            core.io.Ppsf.parse(files.first(), params)
        },
        generator = { _, _ ->
            throw NotImplementedError()
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
    ),
    Tssln(
        "tssln",
        parser = { files, params ->
            core.io.Tssln.parse(files.first(), params)
        },
        generator = { project, _ ->
            core.io.Tssln.generate(project)
        },
        possibleLyricsTypes = listOf(KanaCv, RomajiCv),
        availableFeaturesForGeneration = listOf(ConvertPhonemes),
    ),
    UfData(
        "ufdata",
        parser = { files, params ->
            core.io.UfData.parse(files.first(), params)
        },
        generator = { project, features ->
            core.io.UfData.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, RomajiVcv, KanaCv, KanaVcv),
        availableFeaturesForGeneration = listOf(ConvertPitch, ConvertPhonemes),
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
                Tssln,
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
                Tssln,
                UfData,
            )

        val vocaloidFormats: List<Format>
            get() = listOf(Vsq, Vsqx, VocaloidMid, Vpr)
    }
}
