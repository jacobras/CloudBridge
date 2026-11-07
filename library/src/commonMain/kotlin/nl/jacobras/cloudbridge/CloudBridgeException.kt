package nl.jacobras.cloudbridge

import kotlinx.io.IOException
import kotlin.time.Duration

public open class CloudBridgeException : Exception {

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

    public class NotAuthenticatedException : CloudBridgeException(
        "The cloud service is not (yet) authenticated"
    )

    public class NotFoundException(itemId: String) : CloudBridgeException(
        "The requested item '$itemId' could not be found"
    )

    public class ConflictException(itemId: String) : CloudBridgeException(
        "The item '$itemId' conflicts with an existing item"
    )

    public class InsufficientSpaceException : CloudBridgeException(
        "The cloud account doesn't have enough available storage space left"
    )

    /**
     * @param retryAfter How long the service asked to wait before trying again, if it told.
     */
    public class RateLimitException(public val retryAfter: Duration?) : CloudBridgeException(
        "The cloud service rejected the request because of a rate limit"
    )

    public class IllegalArgumentException(message: String) : CloudBridgeException(message)

    public class ConnectionException(cause: IOException) : CloudBridgeException(
        cause = cause
    )

    public class SourceReadException(cause: IOException) : CloudBridgeException(
        message = "Failed to read the content to upload from the source",
        cause = cause
    )

    public class SinkWriteException(cause: IOException) : CloudBridgeException(
        message = "Failed to write the downloaded content to the sink",
        cause = cause
    )

    public class Unknown(cause: Throwable) : CloudBridgeException(cause)
}