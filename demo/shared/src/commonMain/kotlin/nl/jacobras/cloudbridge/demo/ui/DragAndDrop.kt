package nl.jacobras.cloudbridge.demo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.io.Buffer
import kotlinx.io.RawSource

/**
 * Whether [fileDropTarget] does anything on this platform.
 */
internal expect val isFileDropSupported: Boolean

/**
 * Accepts files dragged in from outside the app. Does nothing on platforms
 * where [isFileDropSupported] is false.
 *
 * @param onHoverChange Called with true while files are dragged over this target.
 * @param onDrop Called with the dropped files.
 */
@Composable
internal expect fun Modifier.fileDropTarget(
    onHoverChange: (Boolean) -> Unit,
    onDrop: (List<LocalFile>) -> Unit
): Modifier

/**
 * Reports the total number of bytes read from [upstream] to [onProgress].
 */
internal class ProgressSource(
    private val upstream: RawSource,
    private val onProgress: (bytesRead: Long) -> Unit
) : RawSource {
    private var bytesRead = 0L

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        val read = upstream.readAtMostTo(sink, byteCount)
        if (read > 0) {
            bytesRead += read
            onProgress(bytesRead)
        }
        return read
    }

    override fun close() = upstream.close()
}