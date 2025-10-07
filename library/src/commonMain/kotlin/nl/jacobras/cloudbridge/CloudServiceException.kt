package nl.jacobras.cloudbridge

public open class CloudServiceException : Exception {

    private constructor(
        message: String
    ) : super(message)

    private constructor(
        cause: Exception
    ) : super(cause)

    public class NotAuthenticatedException : CloudServiceException(
        "The cloud service is not (yet) authenticated"
    )

    public class Unknown(cause: Exception) : CloudServiceException(cause)
}