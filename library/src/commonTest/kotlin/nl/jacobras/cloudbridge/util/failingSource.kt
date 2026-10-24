package nl.jacobras.cloudbridge.util

import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.buffered

/**
 * Returns a source that yields [bytesBeforeFailure] bytes (0, 1, 2, et cetera) and then throws
 * an [IOException] on the next read.
 */
internal fun failingSource(bytesBeforeFailure: Int = 0): Source {
    val data = bufferOf(bytesBeforeFailure)
    return object : RawSource {
        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            if (data.exhausted()) {
                throw IOException("Read failed")
            }
            return data.readAtMostTo(sink, byteCount)
        }

        override fun close() = Unit
    }.buffered()
}