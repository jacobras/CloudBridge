package nl.jacobras.cloudbridge.auth

import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem

/**
 * Reads the [name] query parameter from this [NSURL], like Android's `Uri.getQueryParameter()`.
 *
 * @return the parameter value or `null` if not found.
 */
internal fun NSURL.queryParameter(name: String): String? {
    val components = NSURLComponents(uRL = this, resolvingAgainstBaseURL = false)
    val items = components.queryItems ?: return null
    return items
        .filterIsInstance<NSURLQueryItem>()
        .firstOrNull { it.name == name }
        ?.value
}