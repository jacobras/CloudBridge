import org.w3c.dom.Location

internal fun Location.getHashParam(key: String): String? {
    val hash = hash.removePrefix("#")
    return hash
        .split("&")
        .mapNotNull {
            val (k, v) = it.split("=", limit = 2).let { parts ->
                if (parts.size == 2) parts[0] to parts[1] else null
            } ?: return@mapNotNull null
            k to v
        }
        .firstOrNull { it.first == key }
        ?.second
}