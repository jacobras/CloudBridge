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
            // Not LoggingFormat.OkHttp: although it omits binary bodies, it slows streamed uploads
            // down so much that large ones hit the CIO engine's request timeout. The default
            // format does dump binary bodies, so long messages are truncated instead.
            logger = TruncatingLogger(KermitKtorLogger(severity = Severity.Debug, logger = Logger))
            level = LogLevel.BODY
            sanitizeHeader { it == HttpHeaders.Authorization }
        }
    }
}

/**
 * Truncates messages longer than [MAX_LOG_LENGTH], which will usually be binary request bodies.
 */
private class TruncatingLogger(private val delegate: KtorLogger) : KtorLogger {
    override fun log(message: String) {
        delegate.log(
            if (message.length <= MAX_LOG_LENGTH) {
                message
            } else {
                message.take(MAX_LOG_LENGTH) + "… (truncated, ${message.length} chars in total)"
            }
        )
    }
}

private const val MAX_LOG_LENGTH = 4_000