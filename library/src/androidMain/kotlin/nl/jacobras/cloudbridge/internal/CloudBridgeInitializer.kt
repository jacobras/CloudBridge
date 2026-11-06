package nl.jacobras.cloudbridge.internal

import android.content.Context
import androidx.startup.Initializer

/**
 * Holds the application context, set by [CloudBridgeInitializer] at app start.
 */
internal object CloudBridgeContext {
    var applicationContext: Context? = null
}

/**
 * Stores the application context in [CloudBridgeContext] at app start, through androidx.startup.
 */
internal class CloudBridgeInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        CloudBridgeContext.applicationContext = context.applicationContext
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}