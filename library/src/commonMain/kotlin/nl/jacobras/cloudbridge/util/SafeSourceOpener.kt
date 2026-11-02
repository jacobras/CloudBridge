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

        val isEmpty = withContext(ioDispatcher) {
            try {
                source.exhausted()
            } catch (e: IOException) {
                source.close()
                throw CloudServiceException.SourceReadException(e)
            }
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