package nl.jacobras.cloudbridge.util

import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSink
import kotlinx.io.Sink
import kotlinx.io.buffered

/**
 * Returns a sink that throws an [IOException] as soon as anything is written through to it.
 */
internal fun failingSink(): Sink {
    return object : RawSink {
        override fun write(source: Buffer, byteCount: Long) {
            throw IOException("Write failed")
        }

        override fun flush() = Unit

        override fun close() = Unit
    }.buffered()
}
