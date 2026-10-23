@file:OptIn(ExperimentalWasmJsInterop::class)

package nl.jacobras.cloudbridge.demo.ui

import kotlinx.browser.document
import kotlinx.io.Buffer
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

internal actual val isFilePickerSupported: Boolean = true

internal actual fun pickFiles(onPicked: (List<LocalFile>) -> Unit) {
    val input = document.createElement("input").unsafeCast<HTMLInputElement>()
    input.type = "file"
    input.multiple = true
    input.onchange = {
        val fileList = input.files
        val files = (0 until (fileList?.length ?: 0)).mapNotNull { fileList?.get(it) }
        readAll(files, onPicked)
    }
    input.click()
}

/**
 * Reads all [files] into memory, because a browser [File] can only be read
 * asynchronously while a [kotlinx.io.Source] is read synchronously.
 */
private fun readAll(files: List<File>, onRead: (List<LocalFile>) -> Unit) {
    if (files.isEmpty()) return

    val results = arrayOfNulls<LocalFile>(files.size)
    var remaining = files.size
    files.forEachIndexed { index, file ->
        val reader = FileReader()
        reader.onload = {
            val array = Int8Array(reader.result!!.unsafeCast<ArrayBuffer>())
            val bytes = ByteArray(array.length) { array[it] }
            results[index] = LocalFile(
                name = file.name,
                sizeInBytes = bytes.size.toLong(),
                mimeType = file.type.takeIf { it.isNotEmpty() },
                open = { Buffer().apply { write(bytes) } }
            )
            remaining--
            if (remaining == 0) {
                onRead(results.filterNotNull())
            }
        }
        reader.readAsArrayBuffer(file)
    }
}