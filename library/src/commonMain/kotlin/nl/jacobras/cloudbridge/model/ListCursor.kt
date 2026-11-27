package nl.jacobras.cloudbridge.model

import kotlin.jvm.JvmInline

/**
 * Wrapper to make list cursors type-safe.
 */
@JvmInline
public value class ListCursor(public val value: String) {
    override fun toString(): String = value
}