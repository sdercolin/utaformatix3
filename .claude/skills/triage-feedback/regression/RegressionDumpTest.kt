package io

import core.model.FeatureConfig
import core.model.Format
import core.model.ImportParams
import helpers.fixtureText
import helpers.normalizeUuids
import helpers.readBase64
import helpers.readText
import helpers.readZipEntryTexts
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.set
import org.w3c.files.Blob
import org.w3c.files.File
import kotlin.test.Test
import kotlin.test.fail

@OptIn(DelicateCoroutinesApi::class)
class RegressionDumpTest {
    private val builtInInputs =
        listOf(
            "sample.svp" to Format.Svp,
            "svp_export.svp" to Format.Svp,
            "sample.ust" to Format.Ust,
            "sample.ustx" to Format.Ustx,
            "ustx_export.ustx" to Format.Ustx,
            "s5p_export.s5p" to Format.S5p,
            "ccs_export.ccs" to Format.Ccs,
            "vsqx_export.vsqx" to Format.Vsqx,
            "musicxml_export.txt" to Format.MusicXml,
            "dv_export_base64.txt" to Format.Dv,
            "standard_mid_export_base64.txt" to Format.StandardMid,
            "vocaloid_mid_export_base64.txt" to Format.VocaloidMid,
            "vsq_export_base64.txt" to Format.Vsq,
            "tssln_export_base64.txt" to Format.Tssln,
            "ccs_import.ufdata" to Format.UfData,
            "dv_import.ufdata" to Format.UfData,
            "export_source.ufdata" to Format.UfData,
            "musicxml_import.ufdata" to Format.UfData,
            "ppsf_import.ufdata" to Format.UfData,
            "s5p_import.ufdata" to Format.UfData,
            "standard_mid_import.ufdata" to Format.UfData,
            "svp_export_source.ufdata" to Format.UfData,
            "svp_import.ufdata" to Format.UfData,
            "tssln_import.ufdata" to Format.UfData,
            "ust_import.ufdata" to Format.UfData,
            "ustx_export_source.ufdata" to Format.UfData,
            "ustx_import.ufdata" to Format.UfData,
            "vocaloid_mid_import.ufdata" to Format.UfData,
            "vpr_import.ufdata" to Format.UfData,
            "vsq_import.ufdata" to Format.UfData,
            "vsqx_import.ufdata" to Format.UfData,
        )

    private val corpusInputs: List<Pair<String, Format>> =
        try {
            fixtureText("corpus_index.txt")
                .lines()
                .filter { it.isNotBlank() }
                .map { line -> line.substringBefore(' ') to Format.valueOf(line.substringAfter(' ').trim()) }
        } catch (t: Throwable) {
            listOf()
        }

    private fun fileOf(
        name: String,
        format: Format,
    ): File {
        val text = fixtureText(name).let { if (it.startsWith("===== ")) it.substringAfter('\n') else it }
        if (!name.contains("base64") && !name.endsWith(".b64.txt")) {
            return File(arrayOf(text), "input.${format.extension.removePrefix(".")}")
        }
        val binary = window.atob(text.trim())
        val bytes = Uint8Array(binary.length)
        for (i in binary.indices) bytes[i] = binary[i].code.toByte()
        return File(arrayOf(bytes), "input.${format.extension.removePrefix(".")}")
    }

    private fun hash(text: String): String {
        var h = 0x811c9dc5.toInt()
        for (c in text) {
            h = h xor c.code
            h *= 0x01000193
        }
        return h.toUInt().toString(16).padStart(8, '0') + ":" + text.length
    }

    private suspend fun Blob.canonical(): String {
        val base64 = readBase64()
        if (base64.startsWith("UEsD")) return readZipEntryTexts().normalizeUuids()
        val text = readText()
        return if (text.contains('\uFFFD')) base64 else text.normalizeUuids()
    }

    @Test
    fun dump() =
        GlobalScope.promise {
            val lines = mutableListOf<String>()
            for ((name, format) in builtInInputs + corpusInputs) {
                val project =
                    try {
                        format.parser(listOf(fileOf(name, format)), ImportParams())
                    } catch (t: Throwable) {
                        lines.add("$name import ERR ${t::class.simpleName}: ${t.message?.lines()?.first()}")
                        continue
                    }
                lines.add("$name import notes=${project.tracks.sumOf { it.notes.size }}")
                for (target in Format.entries) {
                    for ((label, features) in listOf("pitch" to listOf(FeatureConfig.ConvertPitch), "plain" to listOf())) {
                        val result =
                            try {
                                hash(target.generator(project, features).blob.canonical())
                            } catch (t: Throwable) {
                                "ERR ${t::class.simpleName}: ${t.message?.lines()?.first()}"
                            }
                        lines.add("$name -> ${target.name} [$label] $result")
                    }
                }
            }
            fail("REGRESSION_DUMP_BEGIN\n" + lines.joinToString("\n") + "\nREGRESSION_DUMP_END")
        }
}
