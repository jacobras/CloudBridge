package nl.jacobras.cloudbridge.model

import nl.jacobras.cloudbridge.CloudService

/**
 * A single page of a [CloudService.listFiles] call.
 *
 * @param items The files and folders on this page.
 * @param nextCursor Cursor to fetch the next page, or `null` if this is the last page.
 */
public data class ListResult(
    val items: List<CloudItem>,
    val nextCursor: ListCursor?
)