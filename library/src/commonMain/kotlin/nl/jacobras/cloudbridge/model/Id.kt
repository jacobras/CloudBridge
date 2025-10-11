package nl.jacobras.cloudbridge.model

/**
 * Wrapper around a string ID to prevent accidentally passing in
 * a path where an ID is expected, and vice versa.
 */
public value class Id(public val value: String)