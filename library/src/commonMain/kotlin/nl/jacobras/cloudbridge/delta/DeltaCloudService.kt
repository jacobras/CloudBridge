package nl.jacobras.cloudbridge.delta

/**
 * Cloud service with support for a "delta" call, which returns the changes since
 * the last time [getDelta] was called.
 */
public interface DeltaCloudService {

    /**
     * Retrieves the changes in the cloud services since [previousCursor].
     *
     * @param previousCursor The cursor returned in [DeltaResult.newCursor] the last time
     * this method was called. For the first time, pass in [DeltaCursor.INITIAL].
     */
    public fun getDelta(previousCursor: DeltaCursor): DeltaResult
}