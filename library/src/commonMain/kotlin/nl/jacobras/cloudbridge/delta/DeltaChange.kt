package nl.jacobras.cloudbridge.delta

import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.Path
import kotlin.time.Instant

/**
 * A change in a cloud service. It could be a creation/update: [UpdatedDeltaChange]
 * or a deletion [DeletedDeltaChange].
 */
public sealed interface DeltaChange {
    public val itemId: CloudItemId?
    public val path: Path
}

/**
 * An item that was either created or updated.
 *
 * @param revision The item's revision, or `null` if the service doesn't provide one
 * (for example: folders on Dropbox).
 * @param modified Last time the item was modified, or `null` if the service doesn't
 * provide it (for example: folders on Dropbox).
 */
public data class UpdatedDeltaChange(
    override val itemId: CloudItemId,
    override val path: Path,
    val revision: String?,
    val modified: Instant?
) : DeltaChange

/**
 * An item that was deleted.
 *
 * @param itemId The id of the deleted item, or `null` if the service doesn't provide
 * ids for deleted items (for example: Dropbox).
 */
public data class DeletedDeltaChange(
    override val itemId: CloudItemId?,
    override val path: Path
) : DeltaChange