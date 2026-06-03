package nl.jacobras.cloudbridge.model

import kotlin.jvm.JvmInline

/**
 * Wrapper around a string ID to prevent accidentally passing in
 * a path where an ID is expected, and vice versa.
 */
@JvmInline
public value class CloudItemId(public val value: String)