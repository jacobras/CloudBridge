package nl.jacobras.cloudbridge.delta

import nl.jacobras.cloudbridge.model.CloudItemId
import nl.jacobras.cloudbridge.model.Path
import kotlin.time.Instant

public sealed interface DeltaChange {
    public val fileId: CloudItemId
    public val path: Path
}

public data class UpdatedDeltaChange(
    override val fileId: CloudItemId,
    override val path: Path,
    val revision: String,
    val modified: Instant
) : DeltaChange

public data class DeletedDeltaChange(
    override val fileId: CloudItemId,
    override val path: Path
) : DeltaChange