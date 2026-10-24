package nl.jacobras.cloudbridge.util

/**
 * Couple of test bytes starting with the PNG header.
 */
internal fun examplePngBytes(): ByteArray = byteArrayOf(
    0x89.toByte(), // Start of the PNG signature
    0x50, // 'P'
    0x4E, // 'N'
    0x47, // 'G'
    0x00, // Lowest possible byte
    0xFF.toByte() // Highest possible byte
)