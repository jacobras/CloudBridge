package nl.jacobras.cloudbridge.util

import kotlinx.io.Buffer

/**
 * Returns a buffer with bytes from 0 to [size]: 0, 1, 2, et cetera.
 */
internal fun bufferOf(size: Int): Buffer = Buffer().apply { write(ByteArray(size) { it.toByte() }) }