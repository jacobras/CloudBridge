package nl.jacobras.cloudbridge.demo.ui

import kotlinx.io.Source

/**
 * A local file, either drag & dropped in or chosen with [pickFiles].
 *
 * @param sizeInBytes Size if known.
 * @param mimeType MIME type if known.
 * @param open Opens the file's content. Caller should close the returned [Source].
 */
internal class LocalFile(
    val name: String,
    val sizeInBytes: Long?,
    val mimeType: String?,
    val open: () -> Source
)