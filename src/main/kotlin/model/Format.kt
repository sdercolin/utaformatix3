package model

import model.Feature.ConvertPitch
import model.LyricsType.KanaCv
import model.LyricsType.KanaVcv
import model.LyricsType.RomajiCv
import model.LyricsType.RomajiVcv
import org.w3c.files.File

enum class Format(
    val extension: String,
    val otherExtensions: List<String> = listOf(),
    val multipleFile: Boolean = false,
    val parser: suspend (List<File>, ImportParams) -> Project,
    val generator: suspend (Project, List<Feature>) -> ExportResult,
    val possibleLyricsTypes: List<LyricsType>,
    val suggestedLyricType: LyricsType? = null,
    val availableFeaturesForGeneration: List<Feature> = listOf()
) {
    Vsqx(
        ".vsqx",
        parser = { files, params ->
            io.Vsqx.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Vsqx.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    Vpr(
        ".vpr",
        parser = { files, params ->
            io.Vpr.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Vpr.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    Ust(
        ".ust",
        multipleFile = true,
        parser = { files, params ->
            io.Ust.parse(files, params)
        },
        generator = { project, features ->
            io.Ust.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, RomajiVcv, KanaCv, KanaVcv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    Ustx(
        ".ustx",
        multipleFile = false,
        parser = { files, params ->
            io.Ustx.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Ustx.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, RomajiVcv, KanaCv, KanaVcv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    Ccs(
        ".ccs",
        parser = { files, params ->
            io.Ccs.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Ccs.generate(project, features)
        },
        possibleLyricsTypes = listOf(KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    ),
    Ccst(
        ".ccst",
        parser = { files, params ->
            io.Ccst.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Ccst.generate(project, features)
        },
        possibleLyricsTypes = listOf(KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    ),
    Tssln(
        ".tssln",
        parser = { files, params ->
            io.Tssln.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Tssln.generate(project, features)
        },
        possibleLyricsTypes = listOf(KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    Svp(
        ".svp",
        parser = { files, params ->
            io.Svp.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Svp.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    S5p(
        ".s5p",
        parser = { files, params ->
            io.S5p.parse(files.first(), params)
        },
        generator = { project, features ->
            io.S5p.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    MusicXml(
        ".musicxml",
        otherExtensions = listOf(".xml"),
        parser = { files, _ ->
            io.MusicXml.parse(files.first())
        },
        generator = { project, _ ->
            io.MusicXml.generate(project)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        suggestedLyricType = KanaCv
    ),
    Dv(
        ".dv",
        parser = { files, params ->
            io.Dv.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Dv.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        suggestedLyricType = RomajiCv,
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    Vsq(
        ".vsq",
        parser = { files, params ->
            io.Vsq.parse(files.first(), params)
        },
        generator = { project, features ->
            io.Vsq.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    VocaloidMid(
        ".mid",
        parser = { files, params ->
            io.VocaloidMid.parse(files.first(), params)
        },
        generator = { project, features ->
            io.VocaloidMid.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    Ppsf(
        ".ppsf",
        parser = { files, _ ->
            io.Ppsf.parse(files.first())
        },
        generator = { _, _ ->
            TODO("Not Implemented")
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv)
    );

    val allExtensions get() = listOf(extension) + otherExtensions

    companion object {
        val importable get() = listOf(Vsqx, Vpr, Vsq, VocaloidMid, Ust, Ustx, Ccs, Ccst, Tssln, MusicXml, Svp, S5p, Dv, Ppsf)
        val exportable get() = listOf(Vsqx, Vpr, Vsq, VocaloidMid, Ust, Ustx, Ccs, Ccst, Tssln, MusicXml, Svp, S5p, Dv)
    }
}
