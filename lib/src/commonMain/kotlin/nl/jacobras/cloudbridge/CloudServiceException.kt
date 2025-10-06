package nl.jacobras.cloudbridge

public open class CloudServiceException : Exception {

    private constructor(
        message: String
    ) : super(message)

    public class NotAuthenticatedException : CloudServiceException(
        "The cloud service is not (yet) authenticated"
    )
}