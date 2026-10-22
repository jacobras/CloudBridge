package nl.jacobras.cloudbridge.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * Unsupported hooks into CloudBridge, intended for the demo app and debugging.
 */
@InternalCloudBridgeApi
public object CloudBridgeInternals {

    /**
     * Additional configuration applied to every [HttpClient] used by CloudBridge, for example
     * to install a logging plugin. Must be set before the first service is created.
     */
    public var extraHttpClientConfig: HttpClientConfig<*>.() -> Unit = {}
}