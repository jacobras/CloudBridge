package nl.jacobras.cloudbridge.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@InternalCloudBridgeApi
public object CloudBridgeInternals {

    /**
     * Additional configuration applied to every [HttpClient] used by CloudBridge, for example
     * to install a logging plugin. Must be set before the first service is created.
     */
    public var extraHttpClientConfig: HttpClientConfig<*>.() -> Unit = {}

    /**
     * Default configuration applied to every [HttpClient] used by CloudBridge.
     */
    internal fun HttpClientConfig<*>.applyDefaults() {
        expectSuccess = true
        install(HttpTimeout) {
            // Connection should happen in 20 seconds
            connectTimeoutMillis = 20.seconds.inWholeMilliseconds

            // No data received for 20 seconds? Cancel
            socketTimeoutMillis = 20.seconds.inWholeMilliseconds

            // Full request that are still going may take a while
            requestTimeoutMillis = 2.minutes.inWholeMilliseconds
        }
    }
}