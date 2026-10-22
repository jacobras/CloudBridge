package nl.jacobras.cloudbridge.demo

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.ktor.KermitKtorLogger
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import nl.jacobras.cloudbridge.CloudBridge
import nl.jacobras.cloudbridge.internal.CloudBridgeInternals
import nl.jacobras.cloudbridge.internal.InternalCloudBridgeApi

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
            logger = KermitKtorLogger(severity = Severity.Debug, logger = Logger)
            level = LogLevel.BODY
        }
    }
}