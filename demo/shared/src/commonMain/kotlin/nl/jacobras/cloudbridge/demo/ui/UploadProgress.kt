package nl.jacobras.cloudbridge.demo.ui

/**
 * Progress of uploading a batch of files concurrently.
 *
 * @param bytesReadPerFile Bytes read from each file so far. Runs ahead of the bytes actually
 * sent by up to one upload chunk, because the library reads a chunk before sending it.
 * @param filesDone Number of files that finished uploading.
 * @param totalBytes Combined size of all files, or null if any file's size is unknown.
 */
internal data class UploadProgress(
    val bytesReadPerFile: List<Long>,
    val filesDone: Int,
    val totalBytes: Long?
) {
    val fileCount: Int get() = bytesReadPerFile.size
    val bytesRead: Long get() = bytesReadPerFile.sum()
}
