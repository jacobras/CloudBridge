package nl.jacobras.cloudbridge.util

internal fun String.ensurePrefix(prefix: String): String {
    return if (startsWith(prefix)) {
        this
    } else {
        prefix + this
    }
}

internal fun String.ensureSuffix(suffix: String): String {
    return if (endsWith(suffix)) {
        this
    } else {
        this + suffix
    }
}