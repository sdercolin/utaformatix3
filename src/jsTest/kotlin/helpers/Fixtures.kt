package helpers

import core.external.JsZip
import core.external.JsZipOption
import core.external.require
import core.io.UfData
import core.model.FeatureConfig
import core.model.Project
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.files.Blob
import org.w3c.files.File
import kotlin.js.Promise
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Loads a text fixture from src/jsTest/resources/fixtures.
 * The files are bundled by webpack (raw-loader) via the copyJsResourcesForTests task.
 */
fun fixtureText(name: String): String = require("./fixtures/$name").default as String

fun fixtureFile(name: String): File = File(arrayOf(fixtureText(name)), name)

suspend fun Blob.readText(): String = asDynamic().text().unsafeCast<Promise<String>>().await()

fun Blob.toFile(name: String): File = File(arrayOf(this), name)

/** Reads the blob's binary content as a base64 string, used to snapshot binary outputs. */
suspend fun Blob.readBase64(): String {
    val buffer = asDynamic().arrayBuffer().unsafeCast<Promise<ArrayBuffer>>().await()
    val bytes = Uint8Array(buffer)
    val builder = StringBuilder()
    for (i in 0 until bytes.length) {
        builder.append(Char(bytes[i].toInt() and 0xFF))
    }
    return window.btoa(builder.toString())
}

/**
 * Reads a zip blob and renders all entries as text, sorted by entry name,
 * so that zipped outputs can be snapshot without depending on zip metadata (e.g. file dates).
 */
suspend fun Blob.readZipEntryTexts(): String {
    val zip = JsZip().loadAsync(this).await()
    val names = js("Object").keys(zip.asDynamic().files).unsafeCast<Array<String>>().sorted()
    val builder = StringBuilder()
    for (name in names) {
        val content = zip.file(name)?.async("string")?.await() as? String ?: ""
        builder.append("===== $name =====\n$content\n")
    }
    return builder.toString()
}

/** Reads the text content of the first entry (by name order) of a zip blob. */
suspend fun Blob.readFirstZipEntryText(): String {
    val zip = JsZip().loadAsync(this).await()
    val name =
        js("Object")
            .keys(zip.asDynamic().files)
            .unsafeCast<Array<String>>()
            .sorted()
            .first()
    return zip.file(name)?.async("string")?.await() as String
}

/** Builds an in-memory zip file with a single entry, e.g. to feed zip-based parsers. */
suspend fun zippedFile(
    fileName: String,
    entryName: String,
    entryContent: String,
): File {
    val zip = JsZip()
    zip.file(entryName, entryContent)
    val option = JsZipOption().also { it.type = "blob" }
    val blob = zip.generateAsync(option).await() as Blob
    return blob.toFile(fileName)
}

/** Serializes a parsed project to UfData JSON, used as the canonical snapshot form. */
suspend fun Project.toUfDataJson(): String =
    UfData
        .generate(this, listOf(FeatureConfig.ConvertPitch))
        .blob
        .readText()

private val uuidRegex = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

/**
 * Replaces randomly generated UUIDs with stable placeholders (numbered by first appearance),
 * so that generated outputs can be compared against golden files.
 */
fun String.normalizeUuids(): String {
    val mapping = mutableMapOf<String, String>()
    return uuidRegex.replace(this) { match ->
        mapping.getOrPut(match.value) {
            "00000000-0000-0000-0000-" + mapping.size.toString().padStart(12, '0')
        }
    }
}

/**
 * Compares [actual] against the checked-in snapshot fixture [name].
 * If the snapshot is missing or outdated, the actual content is printed so that it can be
 * copied into the fixture file, and the test fails.
 */
fun assertMatchesSnapshot(
    name: String,
    actual: String,
) {
    val expected =
        try {
            fixtureText(name).trimEnd('\n')
        } catch (t: Throwable) {
            null
        }
    val actualTrimmed = actual.trimEnd('\n')
    if (expected != actualTrimmed) {
        println("===== SNAPSHOT BOOTSTRAP BEGIN fixtures/$name =====")
        println(actualTrimmed)
        println("===== SNAPSHOT BOOTSTRAP END fixtures/$name =====")
    }
    if (expected == null) {
        fail("Snapshot fixtures/$name does not exist. Its content has been printed to the console.")
    }
    assertEquals(expected, actualTrimmed, "Content does not match snapshot fixtures/$name.")
}
