package nl.jacobras.cloudbridge.service

/**
 * Number of items requested per page for `listFiles()`. Services may return fewer (or, like
 * Dropbox, slightly more) items per page.
 */
internal const val LIST_PAGE_SIZE = 200