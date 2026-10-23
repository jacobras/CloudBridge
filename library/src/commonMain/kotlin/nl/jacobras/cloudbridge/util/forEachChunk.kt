package nl.jacobras.cloudbridge.util

import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray

/**
 * Reads this source to the end in chunks of [chunkSizeBytes] bytes and passes each chunk to [block].
 * Every chunk except the last one is exactly [chunkSizeBytes] bytes. An empty source results in a
 * single empty chunk.
 *
 * @param block Receives the chunk, the offset of its first byte and whether it's the last chunk.
 */
internal suspend fun Source.forEachChunk(
    chunkSizeBytes: Int,
    block: suspend (chunk: ByteArray, offset: Long, isLast: Boolean) -> Unit
) {
    var offset = 0L
    do {
        val (chunk, isLast) = withContext(ioDispatcher) {
            val buffer = Buffer()
            while (buffer.size < chunkSizeBytes) {
                if (readAtMostTo(buffer, chunkSizeBytes - buffer.size) == -1L) {
                    break
                }
            }
            buffer.readByteArray() to exhausted()
        }
        block(chunk, offset, isLast)
        offset += chunk.size
    } while (!isLast)
}