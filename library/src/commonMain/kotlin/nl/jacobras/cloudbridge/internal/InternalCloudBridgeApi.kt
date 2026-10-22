package nl.jacobras.cloudbridge.internal

@RequiresOptIn(
    message = "Internal CloudBridge API, may change or be removed without notice.",
    level = RequiresOptIn.Level.ERROR
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
public annotation class InternalCloudBridgeApi
