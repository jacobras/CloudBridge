package nl.jacobras.cloudbridge

import kotlinx.io.IOException

public open class CloudServiceException : Exception {

    private constructor(
        message: String
    ) : super(message)

    private constructor(
        cause: Throwable
    ) : super(cause)

    public class NotAuthenticatedException : CloudServiceException(
        "The cloud service is not (yet) authenticated"
    )

    public class NotFoundException(itemId: String) : CloudServiceException(
        "The requested item '$itemId' could not be found"
    )

    public class ConnectionException(cause: IOException) : CloudServiceException(
        cause = cause
    )

    public class Unknown(cause: Throwable) : CloudServiceException(cause)
}