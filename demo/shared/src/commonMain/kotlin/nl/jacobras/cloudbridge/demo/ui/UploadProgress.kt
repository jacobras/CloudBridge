package nl.jacobras.cloudbridge.demo.ui

/**
 * @param bytesRead Bytes read from the file so far. Runs ahead of the bytes actually
 * sent by up to one upload chunk, because the library reads a chunk before sending it.
 * @param totalBytes File size, or null if unknown.
 */
internal data class UploadProgress(
    val fileName: String,
    val bytesRead: Long,
    val totalBytes: Long?
)