package model

import model.LyricsType.KANA_CV
import model.LyricsType.KANA_VCV
import model.LyricsType.ROMAJI_CV
import model.LyricsType.ROMAJI_VCV
import org.w3c.files.File

enum class Format(
    val extension: String,
    val multipleFile: Boolean,
    val parser: suspend (files: List<File>) -> Project,
    val generator: suspend (project: Project) -> ExportResult,
    val possibleLyricsTypes: List<LyricsType>,
    val suggestedLyricType: LyricsType? = null
) {
    VSQX(
        ".vsqx",
        multipleFile = false,
        parser = {
            io.Vsqx.parse(it.first())
        },
        generator = {
            io.Vsqx.generate(it)
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, KANA_CV)
    ),
    VPR(
        ".vpr",
        multipleFile = false,
        parser = {
            io.Vpr.parse(it.first())
        },
        generator = {
            io.Vpr.generate(it)
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, KANA_CV)
    ),
    UST(
        ".ust",
        multipleFile = true,
        parser = {
            io.Ust.parse(it)
        },
        generator = {
            io.Ust.generate(it)
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, ROMAJI_VCV, KANA_CV, KANA_VCV)
    ),
    CCS(
        ".ccs",
        multipleFile = false,
        parser = {
            io.Ccs.parse(it.first())
        },
        generator = {
            io.Ccs.generate(it)
        },
        possibleLyricsTypes = listOf(KANA_CV)
    ),
    SVP(
        ".svp",
        multipleFile = false,
        parser = {
            io.Svp.parse(it.first())
        },
        generator = {
            io.Svp.generate(it)
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, KANA_CV)
    ),
    S5P(
        ".s5p",
        multipleFile = false,
        parser = {
            io.S5p.parse(it.first())
        },
        generator = {
            io.S5p.generate(it)
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, KANA_CV)
    ),
    MUSIC_XML(
        ".xml",
        multipleFile = true,
        parser = {
            TODO("Not Implemented")
        },
        generator = {
            io.MusicXml.generate(it)
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
        generator = {
            io.Dv.generate(it)
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
        generator = {
            TODO("Not Implemented")
        },
        possibleLyricsTypes = listOf(ROMAJI_CV, KANA_CV)
    );

    companion object {
        val importable get() = listOf(VSQX, VPR, VSQ, UST, CCS, SVP, S5P, DV)
        val exportable get() = listOf(VSQX, VPR, UST, CCS, MUSIC_XML, SVP, S5P, DV)
    }
}

