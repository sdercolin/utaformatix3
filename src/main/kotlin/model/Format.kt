package model

import model.Feature.CONVERT_PITCH
import model.LyricsType.KANA_CV
import model.LyricsType.KANA_VCV
import model.LyricsType.ROMAJI_CV
import model.LyricsType.ROMAJI_VCV
import org.w3c.files.File

enum class Format(
    val extension: String,
    val multipleFile: Boolean,
    val parser: suspend (files: List<File>) -> Project,
    val generator: suspend (project: Project, _: List<Feature>) -> ExportResult,
    val possibleLyricsTypes: List<LyricsType>,
    val suggestedLyricType: LyricsType? = null,
    val availableFeaturesForGeneration: List<Feature> = listOf()
) {
    VSQX(
        ".vsqx",
        multipleFile = false,
        parser = {
            io.Vsqx.parse(it.first())
        },
        generator = { project, features ->
            io.Vsqx.generate(project, features)
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, KANA_CV),
        availableFeaturesForGeneration = listOf(CONVERT_PITCH)
    ),
    VPR(
        ".vpr",
        multipleFile = false,
        parser = {
            io.Vpr.parse(it.first())
        },
        generator = { project, features ->
            io.Vpr.generate(project, features)
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, KANA_CV),
        availableFeaturesForGeneration = listOf(CONVERT_PITCH)
    ),
    UST(
        ".ust",
        multipleFile = true,
        parser = {
            io.Ust.parse(it)
        },
        generator = { project, _ ->
            io.Ust.generate(project)
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, ROMAJI_VCV, KANA_CV, KANA_VCV)
    ),
    UST_MODE1(
        ".ust",
        multipleFile = true,
        parser = {
            TODO("Same as UST, pass in purpose")
        },
        generator = { project, features ->
            io.UstMode1.generate(project, features)
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, ROMAJI_VCV, KANA_CV, KANA_VCV),
        availableFeaturesForGeneration = listOf(CONVERT_PITCH)
    ),
    CCS(
        ".ccs",
        multipleFile = false,
        parser = {
            io.Ccs.parse(it.first())
        },
        generator = { project, features ->
            io.Ccs.generate(project, features)
        },
        possibleLyricsTypes = listOf(KANA_CV),
        availableFeaturesForGeneration = listOf(CONVERT_PITCH)
    ),
    SVP(
        ".svp",
        multipleFile = false,
        parser = {
            io.Svp.parse(it.first())
        },
        generator = { project, features ->
            io.Svp.generate(project, features)
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, KANA_CV),
        availableFeaturesForGeneration = listOf(CONVERT_PITCH)
    ),
    S5P(
        ".s5p",
        multipleFile = false,
        parser = {
            io.S5p.parse(it.first())
        },
        generator = { project, features ->
            io.S5p.generate(project, features)
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, KANA_CV),
        availableFeaturesForGeneration = listOf(CONVERT_PITCH)
    ),
    MUSIC_XML(
        ".xml",
        multipleFile = true,
        parser = {
            TODO("Not Implemented")
        },
        generator = { project, _ ->
            io.MusicXml.generate(project)
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, KANA_CV),
        suggestedLyricType = KANA_CV
    ),
    DV(
        ".dv",
        multipleFile = false,
        parser = {
            io.Dv.parse(it.first())
        },
        generator = { project, _ ->
            io.Dv.generate(project)
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, KANA_CV),
        suggestedLyricType = ROMAJI_CV
    ),
    VSQ(
        ".vsq",
        multipleFile = false,
        parser = {
            io.Vsq.parse(it.first())
        },
        generator = { project, features ->
            io.Vsq.generate(project, features)
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, KANA_CV),
        availableFeaturesForGeneration = listOf(CONVERT_PITCH)
    ),
    PPSF(
        ".ppsf",
        multipleFile = false,
        parser = {
            io.Ppsf.parse(it.first())
        },
        generator = { _, _ ->
            TODO("Not Implemented")
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, KANA_CV)
    ), ;

    companion object {
        val importable get() = listOf(VSQX, VPR, VSQ, UST, UST_MODE1, CCS, SVP, S5P, DV, PPSF)
        val exportable get() = listOf(VSQX, VPR, VSQ, UST, UST_MODE1, CCS, MUSIC_XML, SVP, S5P, DV)
    }
}

