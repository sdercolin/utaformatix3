@file:OptIn(DelicateCoroutinesApi::class, ExperimentalJsExport::class, ExperimentalSerializationApi::class)

import com.sdercolin.utaformatix.data.Document
import core.io.UfData
import core.model.ProjectContainer
import core.model.ExportResult
import core.model.Format
import core.model.ImportParams
import core.model.JapaneseLyricsType
import core.process.lyrics.japanese.analyseJapaneseLyricsTypeForProject
import core.process.lyrics.japanese.convertJapaneseLyrics
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.w3c.files.File
import kotlin.js.Promise

@JsExport
fun parseVsqx(file: File): Promise<ProjectContainer> = parse(listOf(file), Format.Vsqx)

@JsExport
fun parseVpr(file: File): Promise<ProjectContainer> = parse(listOf(file), Format.Vpr)

@JsExport
fun parseUst(files: Array<File>): Promise<ProjectContainer> = parse(files.toList(), Format.Ust)

@JsExport
fun parseUstx(file: File): Promise<ProjectContainer> = parse(listOf(file), Format.Ustx)

@JsExport
fun parseCcs(file: File): Promise<ProjectContainer> = parse(listOf(file), Format.Ccs)

@JsExport
fun parseSvp(file: File): Promise<ProjectContainer> = parse(listOf(file), Format.Svp)

@JsExport
fun parseS5p(file: File): Promise<ProjectContainer> = parse(listOf(file), Format.S5p)

@JsExport
fun parseMusicXml(file: File): Promise<ProjectContainer> = parse(listOf(file), Format.MusicXml)

@JsExport
fun parseDv(file: File): Promise<ProjectContainer> = parse(listOf(file), Format.Dv)

@JsExport
fun parseVsq(file: File): Promise<ProjectContainer> = parse(listOf(file), Format.Vsq)

@JsExport
fun parseVocaloidMid(file: File): Promise<ProjectContainer> = parse(listOf(file), Format.VocaloidMid)

@JsExport
fun parseStandardMid(file: File): Promise<ProjectContainer> = parse(listOf(file), Format.StandardMid)

@JsExport
fun parsePpsf(file: File): Promise<ProjectContainer> = parse(listOf(file), Format.Ppsf)

@JsExport
fun parseUfData(file: File): Promise<ProjectContainer> = parse(listOf(file), Format.UfData)

private fun parse(files: List<File>, format: Format): Promise<ProjectContainer> = GlobalScope.promise {
    val project = format.parser(files, ImportParams())
    ProjectContainer(project)
}

@JsExport
fun generateVsqx(project: ProjectContainer): Promise<ExportResult> = generate(project, Format.Vsqx)

@JsExport
fun generateVpr(project: ProjectContainer): Promise<ExportResult> = generate(project, Format.Vpr)

@JsExport
fun generateUstZip(project: ProjectContainer): Promise<ExportResult> = generate(project, Format.Ust)

@JsExport
fun generateUstx(project: ProjectContainer): Promise<ExportResult> = generate(project, Format.Ustx)

@JsExport
fun generateCcs(project: ProjectContainer): Promise<ExportResult> = generate(project, Format.Ccs)

@JsExport
fun generateSvp(project: ProjectContainer): Promise<ExportResult> = generate(project, Format.Svp)

@JsExport
fun generateS5p(project: ProjectContainer): Promise<ExportResult> = generate(project, Format.S5p)

@JsExport
fun generateMusicXmlZip(project: ProjectContainer): Promise<ExportResult> = generate(project, Format.MusicXml)

@JsExport
fun generateDv(project: ProjectContainer): Promise<ExportResult> = generate(project, Format.Dv)

@JsExport
fun generateVsq(project: ProjectContainer): Promise<ExportResult> = generate(project, Format.Vsq)

@JsExport
fun generateVocaloidMid(project: ProjectContainer): Promise<ExportResult> = generate(project, Format.VocaloidMid)

@JsExport
fun generateStandardMid(project: ProjectContainer): Promise<ExportResult> = generate(project, Format.StandardMid)

@JsExport
fun generateUfData(project: ProjectContainer): Promise<ExportResult> = generate(project, Format.UfData)

private fun generate(project: ProjectContainer, format: Format): Promise<ExportResult> = GlobalScope.promise {
    format.generator(project.project, listOf())
}

@JsExport
fun projectToUfData(project: ProjectContainer): String {
    return jsonSerializer.encodeToString(Document.serializer(), UfData.generateDocument(project.project))
}

@JsExport
fun ufDataToProject(documentJson: String): ProjectContainer {
    val document: Document = jsonSerializer.decodeFromString(documentJson)
    return ProjectContainer(UfData.parseDocument(document, listOf(), ImportParams()))
}

@JsExport
fun documentConvertJapaneseLyrics(
    project: ProjectContainer,
    fromType: JapaneseLyricsType,
    targetType: JapaneseLyricsType,
    convertVowelConnections: Boolean
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
    val converted = convertJapaneseLyrics(
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
