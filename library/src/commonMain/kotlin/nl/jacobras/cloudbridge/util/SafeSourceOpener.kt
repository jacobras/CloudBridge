package nl.jacobras.cloudbridge.util

import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.Source
import nl.jacobras.cloudbridge.CloudServiceException

/**
 * Opens [source] for each upload attempt, and guards against reused sources, to prevent developers
 * passing in empty sources (which could return in empty files being written in cloud on a retry).
 */
internal class SafeSourceOpener(private val source: suspend () -> Source) {
    private var previous: Source? = null
    private var firstWasEmpty = false

    /**
     * Opens the source. Caller must close it.
     *
     * @throws CloudServiceException.SourceReadException when opening fails, or when the source is
     * the same instance as before or is empty while the first one wasn't.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun open(): Source {
        val source = try {
            source()
        } catch (e: IOException) {
            throw CloudServiceException.SourceReadException(e)
        }
        val isFirst = previous == null
        if (!isFirst && source === previous) {
            throw reusedSourceException()
        }

        // Close on any failure of the whole withContext call: cancellation while entering or
        // returning from it would otherwise leak the source, since the caller never gets it.
        val isEmpty = try {
            withContext(ioDispatcher) { source.exhausted() }
        } catch (e: IOException) {
            try {
                source.close()
            } catch (closeError: Throwable) {
                e.addSuppressed(closeError)
            }
            throw CloudServiceException.SourceReadException(e)
        } catch (e: Throwable) {
            try {
                source.close()
            } catch (closeError: Throwable) {
                e.addSuppressed(closeError)
            }
            throw e
        }
        if (isFirst) {
            firstWasEmpty = isEmpty
        } else if (isEmpty && !firstWasEmpty) {
            source.close()
            throw reusedSourceException()
        }

        previous = source
        return source
    }

    private fun reusedSourceException() = CloudServiceException.SourceReadException(
        IOException("The source param should have returned a new Source, but instead was reused")
    )
}