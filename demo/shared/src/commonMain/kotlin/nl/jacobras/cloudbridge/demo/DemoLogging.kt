package nl.jacobras.cloudbridge.demo

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.ktor.KermitKtorLogger
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import nl.jacobras.cloudbridge.CloudBridge
import nl.jacobras.cloudbridge.internal.CloudBridgeInternals
import nl.jacobras.cloudbridge.internal.InternalCloudBridgeApi
import io.ktor.client.plugins.logging.Logger as KtorLogger

private class KermitLogger : nl.jacobras.cloudbridge.logging.Logger {
    override fun log(message: String) {
        Logger.d(message)
    }
}

/**
 * Routes CloudBridge and HTTP logging to Kermit. Must run before the first service is created.
 */
@OptIn(InternalCloudBridgeApi::class)
internal fun setUpLogging() {
    CloudBridge.logger = KermitLogger()
    CloudBridgeInternals.extraHttpClientConfig = {
        install(Logging) {
            // Not LoggingFormat.OkHttp: it slowed down requests too much, causing timeouts.
            logger = BinaryBodyOmittingLogger(
                KermitKtorLogger(
                    severity = Severity.Debug,
                    logger = Logger
                )
            )
            level = LogLevel.BODY
            sanitizeHeader { it == HttpHeaders.Authorization }
        }
    }
}

/**
 * Replaces bodies with a non-text content type (like file uploads) by a placeholder. Relies on the
 * default format, which logs a body between "BODY Content-Type: x", "BODY START" and "BODY END".
 */
private class BinaryBodyOmittingLogger(private val delegate: KtorLogger) : KtorLogger {
    override fun log(message: String) {
        delegate.log(binaryBodyRegex.replace(message) { match ->
            val contentType = match.groupValues[1]
            if (contentType.isText()) {
                match.value
            } else {
                "BODY Content-Type: $contentType\n" +
                        "BODY (binary body omitted, ${match.groupValues[2].length} chars)\n" +
                        "BODY END"
            }
        })
    }

    private fun String.isText(): Boolean {
        val type = substringBefore(';').trim().lowercase()
        return type.startsWith("text/") ||
                type.endsWith("json") ||
                type.endsWith("xml") ||
                type == "application/x-www-form-urlencoded"
    }
}

private val binaryBodyRegex = Regex(
    "BODY Content-Type: ([^\\n]*)\\nBODY START\\n([\\s\\S]*?)\\nBODY END"
)