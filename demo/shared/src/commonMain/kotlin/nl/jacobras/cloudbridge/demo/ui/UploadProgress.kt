package nl.jacobras.cloudbridge.demo.ui

/**
 * Progress of uploading a batch of files concurrently.
 *
 * @param bytesSentPerFile Bytes of each file sent so far.
 * @param totalBytesPerFile Size of each file, or null while it's unknown.
 * @param filesDone Number of files that finished uploading.
 */
internal data class UploadProgress(
    val bytesSentPerFile: List<Long>,
    val totalBytesPerFile: List<Long?>,
    val filesDone: Int
) {
    val fileCount: Int get() = bytesSentPerFile.size
    val bytesSent: Long get() = bytesSentPerFile.sum()

    /**
     * Combined size of all files, or null if any file's size is unknown.
     */
    val totalBytes: Long?
        get() = if (totalBytesPerFile.any { it == null }) null else totalBytesPerFile.sumOf { it ?: 0L }
}