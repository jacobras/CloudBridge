package nl.jacobras.cloudbridge.util

/**
 * Reports that the server accepted the chunk at [offset] of [chunkSize] bytes.
 *
 * The `totalBytes` is [sizeInBytes] if given, otherwise only known after the last chunk.
 */
internal fun ((bytesSent: Long, totalBytes: Long?) -> Unit).reportChunkSent(
    sizeInBytes: Long?,
    offset: Long,
    chunkSize: Int,
    isLast: Boolean
) {
    val bytesSent = offset + chunkSize
    val totalBytes = if (isLast) {
        bytesSent
    } else {
        null
    }
    invoke(bytesSent, sizeInBytes ?: totalBytes)
}