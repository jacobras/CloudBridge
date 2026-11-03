package nl.jacobras.cloudbridge.util

import kotlinx.io.Source

/**
 * Closes this source, adding any failure to [cause] as suppressed so it doesn't hide [cause].
 */
@Suppress("TooGenericExceptionCaught")
internal fun Source.closeSuppressing(cause: Throwable) {
    try {
        close()
    } catch (closeError: Throwable) {
        cause.addSuppressed(closeError)
    }
}