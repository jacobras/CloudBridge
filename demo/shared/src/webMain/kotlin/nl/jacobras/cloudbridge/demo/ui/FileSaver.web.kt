@file:OptIn(ExperimentalWasmJsInterop::class)

package nl.jacobras.cloudbridge.demo.ui

import kotlinx.browser.document
import org.khronos.webgl.Int8Array
import org.khronos.webgl.set
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob

internal actual val isFileSaveSupported: Boolean = true

internal actual fun saveFile(name: String, bytes: ByteArray) {
    val array = Int8Array(bytes.size)
    bytes.forEachIndexed { index, byte -> array[index] = byte }
    val url = URL.createObjectURL(Blob(arrayOf<JsAny?>(array).toJsArray()))

    val anchor = document.createElement("a").unsafeCast<HTMLAnchorElement>()
    anchor.href = url
    anchor.download = name
    anchor.click()
    URL.revokeObjectURL(url)
}