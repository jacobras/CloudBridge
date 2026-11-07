package nl.jacobras.cloudbridge.util

import nl.jacobras.cloudbridge.CloudBridgeException

@Suppress("TooGenericExceptionCaught")
internal inline fun <T> mapUnknownErrors(block: () -> T): T {
    return try {
        block()
    } catch (e: CloudBridgeException) {
        throw e
    } catch (e: Exception) {
        throw CloudBridgeException.Unknown(e)
    }
}