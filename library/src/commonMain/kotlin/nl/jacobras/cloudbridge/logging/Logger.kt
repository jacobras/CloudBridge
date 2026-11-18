package nl.jacobras.cloudbridge.logging

public interface Logger {

    /**
     * Logs a debug message.
     */
    public fun log(message: String)

    /**
     * Logs a warning message.
     *
     * @param throwable The underlying exception, if any. Never contains tokens.
     */
    public fun warn(message: String, throwable: Throwable? = null)
}

internal object EmptyLogger : Logger {
    override fun log(message: String) = Unit
    override fun warn(message: String, throwable: Throwable?) = Unit
}