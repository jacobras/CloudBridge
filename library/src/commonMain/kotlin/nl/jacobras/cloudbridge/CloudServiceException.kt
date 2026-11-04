package nl.jacobras.cloudbridge

import kotlinx.io.IOException
import kotlin.time.Duration

public open class CloudServiceException : Exception {

    private constructor(
        message: String
    ) : super(message)

    private constructor(
        cause: Throwable
    ) : super(cause)

    private constructor(
        message: String,
        cause: Throwable
    ) : super(message, cause)

    public class NotAuthenticatedException : CloudServiceException(
        "The cloud service is not (yet) authenticated"
    )

    public class NotFoundException(itemId: String) : CloudServiceException(
        "The requested item '$itemId' could not be found"
    )

    public class ConflictException(itemId: String) : CloudServiceException(
        "The item '$itemId' conflicts with an existing item"
    )

    public class InsufficientSpaceException : CloudServiceException(
        "The cloud account doesn't have enough available storage space left"
    )

    /**
     * @param retryAfter How long the service asked to wait before trying again, if it told.
     */
    public class RateLimitException(public val retryAfter: Duration?) : CloudServiceException(
        "The cloud service rejected the request because of a rate limit"
    )

    public class IllegalArgumentException(message: String) : CloudServiceException(message)

    public class ConnectionException(cause: IOException) : CloudServiceException(
        cause = cause
    )

    public class SourceReadException(cause: IOException) : CloudServiceException(
        message = "Failed to read the content to upload from the source",
        cause = cause
    )

    public class SinkWriteException(cause: IOException) : CloudServiceException(
        message = "Failed to write the downloaded content to the sink",
        cause = cause
    )

    public class Unknown(cause: Throwable) : CloudServiceException(cause)
}