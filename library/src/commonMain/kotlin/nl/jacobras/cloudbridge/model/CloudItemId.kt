package nl.jacobras.cloudbridge.model

import kotlin.jvm.JvmInline

/**
 * Wrapper to make item IDs type-safe.
 */
@JvmInline
public value class CloudItemId(public val value: String) {
    override fun toString(): String = value
}