package nl.jacobras.cloudbridge.delta

/**
 * The result of a [DeltaCloudService.getDelta] call.
 *
 * @param newCursor A cursor to pass in with the next delta call, so the cloud services knows
 * where to continue.
 * @param changes The changes that occurred between the passed in cursor and [newCursor].
 */
public data class DeltaResult(
    val newCursor: String,
    val changes: List<DeltaChange>
)