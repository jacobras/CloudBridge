package nl.jacobras.cloudbridge.util

internal fun String.ellipsize(maxLength: Int): String {
    return when {
        length > maxLength -> substring(0, maxLength) + "…"
        else -> this
    }
}

internal fun String.ellipsizeIf(condition: Boolean, maxLength: Int): String {
    return if (condition) {
        ellipsize(maxLength)
    } else this
}

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