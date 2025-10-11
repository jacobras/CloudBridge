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

    public class ConnectionException(cause: IOException) : CloudServiceException(
        cause = cause
    )

    public class Unknown(cause: Throwable) : CloudServiceException(cause)
}