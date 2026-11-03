package nl.jacobras.cloudbridge.util

import nl.jacobras.cloudbridge.CloudServiceException

@Suppress("TooGenericExceptionCaught")
internal inline fun <T> mapUnknownErrors(block: () -> T): T {
    return try {
        block()
    } catch (e: CloudServiceException) {
        throw e
    } catch (e: Exception) {
        throw CloudServiceException.Unknown(e)
    }
}