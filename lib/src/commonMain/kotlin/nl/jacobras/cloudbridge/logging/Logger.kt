package nl.jacobras.cloudbridge.logging

public interface Logger {
    public fun log(message: String)
}

internal object EmptyLogger : Logger {
    override fun log(message: String) {}
}