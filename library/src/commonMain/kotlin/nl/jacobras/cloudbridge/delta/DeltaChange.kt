package nl.jacobras.cloudbridge.delta

import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.Path
import kotlin.time.Instant

/**
 * A change in a cloud service. It could be a creation/update: [UpdatedDeltaChange]
 * or a deletion [DeletedDeltaChange].
 */
public sealed interface DeltaChange {
    public val itemId: CloudItemId
    public val path: Path
}

/**
 * An item that was either created or updated.
 */
public data class UpdatedDeltaChange(
    override val itemId: CloudItemId,
    override val path: Path,
    val revision: String,
    val modified: Instant
) : DeltaChange

/**
 * An item that was deleted.
 */
public data class DeletedDeltaChange(
    override val itemId: CloudItemId,
    override val path: Path
) : DeltaChange