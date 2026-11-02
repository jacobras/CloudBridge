package nl.jacobras.cloudbridge.service

import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import nl.jacobras.cloudbridge.CloudServiceException
import nl.jacobras.cloudbridge.ratelimit.RateLimiter

internal class CallDispatcher(
    private val rateLimiter: RateLimiter?,
    private val authCheck: () -> Boolean,
    private val responseErrorMapper: ResponseException.(itemId: String) -> CloudServiceException
) {

    suspend fun <T> tryCall(
        itemId: String,
        call: suspend () -> T
    ): T {
        if (!authCheck()) {
            throw CloudServiceException.NotAuthenticatedException()
        }
        rateLimiter?.acquire()

        return try {
            call()
        } catch (e: ResponseException) {
            throw e.responseErrorMapper(itemId)
        } catch (e: IOException) {
            throw CloudServiceException.ConnectionException(e)
        } catch (e: CloudServiceException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw CloudServiceException.Unknown(e)
        }
    }
}