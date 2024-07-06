@file:OptIn(DelicateCoroutinesApi::class, ExperimentalJsExport::class, ExperimentalSerializationApi::class)

import com.sdercolin.utaformatix.data.Document
import core.io.UfData
import core.model.ConversionParams
import core.model.ExportResult
import core.model.FeatureConfig
import core.model.Format
import core.model.ImportParams
import core.model.JapaneseLyricsType
import core.model.ProjectContainer
import core.process.lyrics.japanese.analyseJapaneseLyricsTypeForProject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.w3c.files.File
import kotlin.js.Promise
import core.process.lyrics.japanese.convertJapaneseLyrics as convertJapaneseLyricsBase

@JsExport
fun parseVsqx(file: File, params: ImportParams): Promise<ProjectContainer> = parse(listOf(file), params, Format.Vsqx)

@JsExport
fun parseVpr(file: File, params: ImportParams): Promise<ProjectContainer> = parse(listOf(file), params, Format.Vpr)

@JsExport
fun parseUst(
    files: Array<File>,
    params: ImportParams,
): Promise<ProjectContainer> = parse(files.toList(), params, Format.Ust)

@JsExport
fun parseUstx(file: File, params: ImportParams): Promise<ProjectContainer> = parse(listOf(file), params, Format.Ustx)

@JsExport
fun parseCcs(file: File, params: ImportParams): Promise<ProjectContainer> = parse(listOf(file), params, Format.Ccs)

@JsExport
fun parseSvp(file: File, params: ImportParams): Promise<ProjectContainer> = parse(listOf(file), params, Format.Svp)

@JsExport
fun parseS5p(file: File, params: ImportParams): Promise<ProjectContainer> = parse(listOf(file), params, Format.S5p)

@JsExport
fun parseMusicXml(file: File, params: ImportParams): Promise<ProjectContainer> =
    parse(listOf(file), params, Format.MusicXml)

@JsExport
fun parseDv(file: File, params: ImportParams): Promise<ProjectContainer> = parse(listOf(file), params, Format.Dv)

@JsExport
fun parseVsq(file: File, params: ImportParams): Promise<ProjectContainer> = parse(listOf(file), params, Format.Vsq)

@JsExport
fun parseVocaloidMid(file: File, params: ImportParams): Promise<ProjectContainer> =
    parse(listOf(file), params, Format.VocaloidMid)

@JsExport
fun parseStandardMid(file: File, params: ImportParams): Promise<ProjectContainer> =
    parse(listOf(file), params, Format.StandardMid)

@JsExport
fun parsePpsf(file: File, params: ImportParams): Promise<ProjectContainer> = parse(listOf(file), params, Format.Ppsf)

@JsExport
fun parseTssln(file: File, params: ImportParams): Promise<ProjectContainer> = parse(listOf(file), params, Format.Tssln)

@JsExport
fun parseUfData(file: File, params: ImportParams): Promise<ProjectContainer> =
    parse(listOf(file), params, Format.UfData)

private fun parse(files: List<File>, params: ImportParams, format: Format): Promise<ProjectContainer> =
    GlobalScope.promise {
        val project = format.parser(files, params)
        ProjectContainer(project)
    }

@JsExport
fun generateVsqx(project: ProjectContainer, params: ConversionParams): Promise<ExportResult> =
    generate(project, params, Format.Vsqx)

@JsExport
fun generateVpr(project: ProjectContainer, params: ConversionParams): Promise<ExportResult> =
    generate(project, params, Format.Vpr)

@JsExport
fun generateUstZip(project: ProjectContainer, params: ConversionParams): Promise<ExportResult> =
    generate(project, params, Format.Ust)

@JsExport
fun generateUstx(project: ProjectContainer, params: ConversionParams): Promise<ExportResult> =
    generate(project, params, Format.Ustx)

@JsExport
fun generateCcs(project: ProjectContainer, params: ConversionParams): Promise<ExportResult> =
    generate(project, params, Format.Ccs)

@JsExport
fun generateSvp(project: ProjectContainer, params: ConversionParams): Promise<ExportResult> =
    generate(project, params, Format.Svp)

@JsExport
fun generateS5p(project: ProjectContainer, params: ConversionParams): Promise<ExportResult> =
    generate(project, params, Format.S5p)

@JsExport
fun generateMusicXmlZip(project: ProjectContainer, params: ConversionParams): Promise<ExportResult> =
    generate(project, params, Format.MusicXml)

@JsExport
fun generateDv(project: ProjectContainer, params: ConversionParams): Promise<ExportResult> =
    generate(project, params, Format.Dv)

@JsExport
fun generateVsq(project: ProjectContainer, params: ConversionParams): Promise<ExportResult> =
    generate(project, params, Format.Vsq)

@JsExport
fun generateVocaloidMid(project: ProjectContainer, params: ConversionParams): Promise<ExportResult> =
    generate(project, params, Format.VocaloidMid)

@JsExport
fun generateStandardMid(project: ProjectContainer, params: ConversionParams): Promise<ExportResult> =
    generate(project, params, Format.StandardMid)

@JsExport
fun generateTssln(project: ProjectContainer, params: ConversionParams): Promise<ExportResult> =
    generate(project, params, Format.Tssln)

@JsExport
fun generateUfData(project: ProjectContainer, params: ConversionParams): Promise<ExportResult> =
    generate(project, params, Format.UfData)

private fun generate(project: ProjectContainer, params: ConversionParams, format: Format): Promise<ExportResult> {
    val features = mutableListOf<FeatureConfig>()
    if (params.convertPitch) {
        features.add(FeatureConfig.ConvertPitch)
    }

    return GlobalScope.promise {
        format.generator(
            project.project,
            features,
        )
    }
}

@JsExport
fun projectToUfData(project: ProjectContainer): String {
    return jsonSerializer.encodeToString(
        Document.serializer(),
        UfData.generateDocument(
            project.project,
            listOf(
                FeatureConfig.ConvertPitch,
            ),
        ),
    )
}

@JsExport
fun ufDataToProject(documentJson: String): ProjectContainer {
    val document: Document = jsonSerializer.decodeFromString(documentJson)
    return ProjectContainer(
        UfData.parseDocument(
            document,
            listOf(),
            ImportParams(),
        ),
    )
}

@JsExport
fun convertJapaneseLyrics(
    project: ProjectContainer,
    fromType: JapaneseLyricsType,
    targetType: JapaneseLyricsType,
    convertVowelConnections: Boolean,
): ProjectContainer {
    val baseProject = project.project
    val newProject = core.model.Project(
        format = Format.UfData,
        inputFiles = listOf(),
        name = baseProject.name,
        tracks = baseProject.tracks,
        timeSignatures = baseProject.timeSignatures,
        tempos = baseProject.tempos,
        measurePrefix = baseProject.measurePrefix,
        importWarnings = baseProject.importWarnings,
        japaneseLyricsType = fromType,
    )
    val converted = convertJapaneseLyricsBase(
        newProject,
        targetType,
        if (convertVowelConnections) {
            Format.Ust
        } else {
            Format.UfData
        },
    )
    return ProjectContainer(converted)
}

@JsExport
fun analyzeJapaneseLyricsType(project: ProjectContainer): JapaneseLyricsType {
    return analyseJapaneseLyricsTypeForProject(project.project)
}

@OptIn(ExperimentalSerializationApi::class)
val jsonSerializer = Json {
    isLenient = true
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
