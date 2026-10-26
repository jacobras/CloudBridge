package nl.jacobras.cloudbridge.demo.ui

/**
 * How the demo shows a file, based on its extension.
 */
internal enum class FileType {
    Text,
    Image,
    Other;

    companion object {
        private val textExtensions = setOf("txt", "md", "json", "csv", "xml", "html", "log", "yml", "yaml")
        private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

        fun of(fileName: String): FileType {
            if (!fileName.contains('.')) return Text

            val extension = fileName.substringAfterLast('.').lowercase()
            return when (extension) {
                in textExtensions -> Text
                in imageExtensions -> Image
                else -> Other
            }
        }
    }
}