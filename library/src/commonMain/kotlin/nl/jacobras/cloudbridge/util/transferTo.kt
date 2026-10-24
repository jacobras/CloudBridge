package nl.jacobras.cloudbridge.util

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.exhausted
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.withContext
import kotlinx.io.Sink

/**
 * Reads this channel to the end and writes everything to [sink], in chunks of [chunkSizeBytes]
 * bytes. The sink is flushed, but not closed.
 */
internal suspend fun ByteReadChannel.transferTo(
    sink: Sink,
    chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE
) {
    while (!exhausted()) {
        val chunk = readBuffer(chunkSizeBytes)
        withContext(ioDispatcher) {
            chunk.transferTo(sink)
        }
    }
    withContext(ioDispatcher) {
        sink.flush()
    }
}

private const val DEFAULT_CHUNK_SIZE = 64 * 1024L