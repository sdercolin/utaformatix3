package helpers

import core.external.require
import core.io.UfData
import core.model.FeatureConfig
import core.model.Project
import kotlinx.coroutines.await
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
