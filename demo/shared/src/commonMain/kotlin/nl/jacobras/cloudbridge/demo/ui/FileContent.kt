package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Downloaded content of the selected file.
 */
internal sealed interface FileContent {
    data object None : FileContent
    data object Loading : FileContent
    data class Text(val text: String) : FileContent
    class Image(val bitmap: ImageBitmap, val bytes: ByteArray) : FileContent
}