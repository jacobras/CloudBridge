package nl.jacobras.cloudbridge.util

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.exhausted
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.Sink
import nl.jacobras.cloudbridge.CloudServiceException

/**
 * Reads this channel to the end and writes everything to [sink], in chunks of [chunkSizeBytes]
 * bytes. The sink is flushed, but not closed.
 *
 * @throws CloudServiceException.SinkWriteException when writing to [sink] fails.
 */
internal suspend fun ByteReadChannel.transferTo(
    sink: Sink,
    chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE
) {
    while (!exhausted()) {
        val chunk = readBuffer(chunkSizeBytes)
        withContext(ioDispatcher) {
            mapSinkFailure { chunk.transferTo(sink) }
        }
    }
    withContext(ioDispatcher) {
        mapSinkFailure { sink.flush() }
    }
}

/**
 * Maps any [IOException] to a [CloudServiceException.SinkWriteException].
 */
private inline fun mapSinkFailure(block: () -> Unit) {
    try {
        block()
    } catch (e: IOException) {
        throw CloudServiceException.SinkWriteException(e)
    }
}

private const val DEFAULT_CHUNK_SIZE = 64 * 1024L