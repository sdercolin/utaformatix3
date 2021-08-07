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
    val parser: suspend (List<File>) -> Project,
    val generator: suspend (Project, List<Feature>) -> ExportResult,
    val possibleLyricsTypes: List<LyricsType>,
    val suggestedLyricType: LyricsType? = null,
    val availableFeaturesForGeneration: List<Feature> = listOf()
) {
    Vsqx(
        ".vsqx",
        parser = {
            io.Vsqx.parse(it.first())
        },
        generator = { project, features ->
            io.Vsqx.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    Vpr(
        ".vpr",
        parser = {
            io.Vpr.parse(it.first())
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
        parser = {
            io.Ust.parse(it)
        },
        generator = { project, features ->
            io.Ust.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, RomajiVcv, KanaCv, KanaVcv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    Ccs(
        ".ccs",
        parser = {
            io.Ccs.parse(it.first())
        },
        generator = { project, features ->
            io.Ccs.generate(project, features)
        },
        possibleLyricsTypes = listOf(KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    Svp(
        ".svp",
        parser = {
            io.Svp.parse(it.first())
        },
        generator = { project, features ->
            io.Svp.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    S5p(
        ".s5p",
        parser = {
            io.S5p.parse(it.first())
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
        parser = {
            io.MusicXml.parse(it.first())
        },
        generator = { project, _ ->
            io.MusicXml.generate(project)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        suggestedLyricType = KanaCv
    ),
    Dv(
        ".dv",
        parser = {
            io.Dv.parse(it.first())
        },
        generator = { project, _ ->
            io.Dv.generate(project)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        suggestedLyricType = RomajiCv
    ),
    Vsq(
        ".vsq",
        parser = {
            io.Vsq.parse(it.first())
        },
        generator = { project, features ->
            io.Vsq.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    Midi(
        ".mid",
        parser = {
            io.VocaloidMidi.parse(it.first())
        },
        generator = { project, features ->
            io.VocaloidMidi.generate(project, features)
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv),
        availableFeaturesForGeneration = listOf(ConvertPitch)
    ),
    Ppsf(
        ".ppsf",
        parser = {
            io.Ppsf.parse(it.first())
        },
        generator = { _, _ ->
            TODO("Not Implemented")
        },
        possibleLyricsTypes = listOf(RomajiCv, KanaCv)
    );

    val allExtensions get() = listOf(extension) + otherExtensions

    companion object {
        val importable get() = listOf(Vsqx, Vpr, Vsq, Midi, Ust, Ccs, MusicXml, Svp, S5p, Dv, Ppsf)
        val exportable get() = listOf(Vsqx, Vpr, Vsq, Midi, Ust, Ccs, MusicXml, Svp, S5p, Dv)
    }
}
