package util

import exception.CannotReadFileException
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileList
import org.w3c.files.FileReader
import org.w3c.files.get
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

val File.nameWithoutExtension: String
    get() {
        val name = this.name
        val lastPointIndex = name.lastIndexOf(".").takeIf { it > 0 } ?: return name
        return name.substring(0, lastPointIndex)
    }

val File.extensionName: String
    get() {
        return if (!name.contains('.')) ""
        else name.split(".").last().lowercase()
    }

suspend fun waitFileSelection(accept: String, multiple: Boolean): List<File> = suspendCoroutine { cont ->
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.multiple = multiple
    input.accept = accept
    input.onchange = {
        cont.resume(input.files?.toList().orEmpty())
    }
    input.click()
}

suspend fun File.readText(encoding: String? = null): String = suspendCoroutine { cont ->
    val fileReader = FileReader()
    fileReader.onloadend = {
        val text = fileReader.result as String
        cont.resume(text)
    }

    fileReader.onerror = {
        cont.resumeWithException(CannotReadFileException())
    }
    if (encoding == null) {
        fileReader.readAsText(this)
    } else {
        fileReader.readAsText(this, encoding)
    }
}

suspend fun File.readBinary() = suspendCoroutine<Array<Byte>> { cont ->
    val fileReader = FileReader()
    fileReader.onloadend = {
        cont.resume(fileReader.result)
    }
    fileReader.onerror = {
        cont.resumeWithException(CannotReadFileException())
    }
    fileReader.readAsBinaryString(this)
}

suspend fun File.readAsArrayBuffer() = suspendCoroutine<ArrayBuffer> { cont ->
    val fileReader = FileReader()
    fileReader.onload = {
        cont.resume(fileReader.result)
    }
    fileReader.onerror = {
        cont.resumeWithException(CannotReadFileException())
    }
    fileReader.readAsArrayBuffer(this)
}

fun FileList.toList(): List<File> = (0 until length).mapNotNull { get(it) }

fun getSafeFileName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "")
