@file:OptIn(DelicateCoroutinesApi::class, ExperimentalJsExport::class)

import com.sdercolin.utaformatix.data.Document
import core.io.UfData
import core.model.Format
import core.model.ImportParams
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
fun hello(): String = "core"
