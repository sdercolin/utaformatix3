@file:OptIn(DelicateCoroutinesApi::class, ExperimentalJsExport::class)

import com.sdercolin.utaformatix.data.Document
import core.io.UfData
import core.model.Format
import core.model.ImportParams
import core.model.ExportResult
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.w3c.files.File
import kotlin.js.Promise

@JsExport
fun parseVsqx(file: File): Promise<Document> = parse(listOf(file), Format.Vsqx)

@JsExport
fun parseVpr(file: File): Promise<Document> = parse(listOf(file), Format.Vpr)

@JsExport
fun parseUst(files: Array<File>): Promise<Document> = parse(files.toList(), Format.Ust)

@JsExport
fun parseUstx(file: File): Promise<Document> = parse(listOf(file), Format.Ustx)

@JsExport
fun parseCcs(file: File): Promise<Document> = parse(listOf(file), Format.Ccs)

@JsExport
fun parseSvp(file: File): Promise<Document> = parse(listOf(file), Format.Svp)

@JsExport
fun parseS5p(file: File): Promise<Document> = parse(listOf(file), Format.S5p)

@JsExport
fun parseMusicXml(file: File): Promise<Document> = parse(listOf(file), Format.MusicXml)

@JsExport
fun parseDv(file: File): Promise<Document> = parse(listOf(file), Format.Dv)

@JsExport
fun parseVsq(file: File): Promise<Document> = parse(listOf(file), Format.Vsq)

@JsExport
fun parseVocaloidMid(file: File): Promise<Document> = parse(listOf(file), Format.VocaloidMid)

@JsExport
fun parseStandardMid(file: File): Promise<Document> = parse(listOf(file), Format.StandardMid)

@JsExport
fun parsePpsf(file: File): Promise<Document> = parse(listOf(file), Format.Ppsf)

@JsExport
fun parseUfData(files: Array<File>): Promise<Document> = parse(files.toList(), Format.UfData)

private fun parse(files: List<File>, format: Format): Promise<Document> = GlobalScope.promise {
    val project = format.parser(files, ImportParams())
    UfData.generateDocument(project)
}

@JsExport
fun generateVsqx(document: Document): Promise<ExportResult> = generate(document, Format.Vsqx)

@JsExport
fun generateVpr(document: Document): Promise<ExportResult> = generate(document, Format.Vpr)

@JsExport
fun generateUstZip(document: Document): Promise<ExportResult> = generate(document, Format.Ust)

@JsExport
fun generateUstx(document: Document): Promise<ExportResult> = generate(document, Format.Ustx)

@JsExport
fun generateCcs(document: Document): Promise<ExportResult> = generate(document, Format.Ccs)

@JsExport
fun generateSvp(document: Document): Promise<ExportResult> = generate(document, Format.Svp)

@JsExport
fun generateS5p(document: Document): Promise<ExportResult> = generate(document, Format.S5p)

@JsExport
fun generateMusicXmlZip(document: Document): Promise<ExportResult> = generate(document, Format.MusicXml)

@JsExport
fun generateDv(document: Document): Promise<ExportResult> = generate(document, Format.Dv)

@JsExport
fun generateVsq(document: Document): Promise<ExportResult> = generate(document, Format.Vsq)

@JsExport
fun generateVocaloidMid(document: Document): Promise<ExportResult> = generate(document, Format.VocaloidMid)

@JsExport
fun generateStandardMid(document: Document): Promise<ExportResult> = generate(document, Format.StandardMid)

@JsExport
fun generateUfData(document: Document): Promise<ExportResult> = generate(document, Format.UfData)

private fun generate(document: Document, format: Format): Promise<ExportResult> = GlobalScope.promise {
    format.generator(document, listOf())
}

@JsExport
fun hello(): String = "core"
