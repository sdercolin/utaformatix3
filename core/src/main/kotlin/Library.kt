@file:OptIn(DelicateCoroutinesApi::class, ExperimentalJsExport::class)

import core.io.UfData
import core.model.Format
import core.model.ImportParams
import core.model.ExportResult
import core.model.DocumentContainer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.w3c.files.File
import kotlin.js.Promise

@JsExport
fun parseVsqx(file: File): Promise<DocumentContainer> = parse(listOf(file), Format.Vsqx)

@JsExport
fun parseVpr(file: File): Promise<DocumentContainer> = parse(listOf(file), Format.Vpr)

@JsExport
fun parseUst(files: Array<File>): Promise<DocumentContainer> = parse(files.toList(), Format.Ust)

@JsExport
fun parseUstx(file: File): Promise<DocumentContainer> = parse(listOf(file), Format.Ustx)

@JsExport
fun parseCcs(file: File): Promise<DocumentContainer> = parse(listOf(file), Format.Ccs)

@JsExport
fun parseSvp(file: File): Promise<DocumentContainer> = parse(listOf(file), Format.Svp)

@JsExport
fun parseS5p(file: File): Promise<DocumentContainer> = parse(listOf(file), Format.S5p)

@JsExport
fun parseMusicXml(file: File): Promise<DocumentContainer> = parse(listOf(file), Format.MusicXml)

@JsExport
fun parseDv(file: File): Promise<DocumentContainer> = parse(listOf(file), Format.Dv)

@JsExport
fun parseVsq(file: File): Promise<DocumentContainer> = parse(listOf(file), Format.Vsq)

@JsExport
fun parseVocaloidMid(file: File): Promise<DocumentContainer> = parse(listOf(file), Format.VocaloidMid)

@JsExport
fun parseStandardMid(file: File): Promise<DocumentContainer> = parse(listOf(file), Format.StandardMid)

@JsExport
fun parsePpsf(file: File): Promise<DocumentContainer> = parse(listOf(file), Format.Ppsf)

@JsExport
fun parseUfData(file: File): Promise<DocumentContainer> = parse(listOf(file), Format.UfData)

private fun parse(files: List<File>, format: Format): Promise<DocumentContainer> = GlobalScope.promise {
    val project = format.parser(files, ImportParams())
    DocumentContainer(UfData.generateDocument(project))
}

@JsExport
fun generateVsqx(document: DocumentContainer): Promise<ExportResult> = generate(document, Format.Vsqx)

@JsExport
fun generateVpr(document: DocumentContainer): Promise<ExportResult> = generate(document, Format.Vpr)

@JsExport
fun generateUstZip(document: DocumentContainer): Promise<ExportResult> = generate(document, Format.Ust)

@JsExport
fun generateUstx(document: DocumentContainer): Promise<ExportResult> = generate(document, Format.Ustx)

@JsExport
fun generateCcs(document: DocumentContainer): Promise<ExportResult> = generate(document, Format.Ccs)

@JsExport
fun generateSvp(document: DocumentContainer): Promise<ExportResult> = generate(document, Format.Svp)

@JsExport
fun generateS5p(document: DocumentContainer): Promise<ExportResult> = generate(document, Format.S5p)

@JsExport
fun generateMusicXmlZip(document: DocumentContainer): Promise<ExportResult> = generate(document, Format.MusicXml)

@JsExport
fun generateDv(document: DocumentContainer): Promise<ExportResult> = generate(document, Format.Dv)

@JsExport
fun generateVsq(document: DocumentContainer): Promise<ExportResult> = generate(document, Format.Vsq)

@JsExport
fun generateVocaloidMid(document: DocumentContainer): Promise<ExportResult> = generate(document, Format.VocaloidMid)

@JsExport
fun generateStandardMid(document: DocumentContainer): Promise<ExportResult> = generate(document, Format.StandardMid)

@JsExport
fun generateUfData(document: DocumentContainer): Promise<ExportResult> = generate(document, Format.UfData)

private fun generate(document: DocumentContainer, format: Format): Promise<ExportResult> = GlobalScope.promise {
    format.generator(UfData.parseDocument(document.document, listOf(), ImportParams()), listOf())
}
