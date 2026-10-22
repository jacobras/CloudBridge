package nl.jacobras.cloudbridge.delta

import kotlin.jvm.JvmInline

/**
 * Wrapper to make delta cursors type-safe.
 */
@JvmInline
public value class DeltaCursor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        public val INITIAL: DeltaCursor = DeltaCursor("CloudBridge-INITIAL")
    }
}