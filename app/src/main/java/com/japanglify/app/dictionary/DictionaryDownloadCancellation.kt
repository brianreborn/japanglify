package com.japanglify.app.dictionary

import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap

/**
 * In-process registry letting [DictionaryDownloadService]'s cancel action reach
 * into whichever download manager (JMdict/emoji/WordNet) currently owns the
 * in-flight [HttpURLConnection] for a given [DictionarySource.id], from a
 * different call stack than the one blocked in `input.read()`.
 *
 * [cancel] both flags the id (checked between buffer reads so the copy loop
 * exits cleanly) and calls `disconnect()` on the live connection, if any --
 * `disconnect()` closes the underlying socket and is documented as safe to
 * call from another thread, which is what actually unblocks a `read()` that's
 * currently stalled rather than between reads. Belt-and-suspenders: either
 * one alone is enough to stop the download, but a `read()` blocked deep in a
 * slow/stalled response needs the `disconnect()` half specifically.
 */
object DictionaryDownloadCancellation {
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()
    private val activeConnections = ConcurrentHashMap<String, HttpURLConnection>()

    fun beginAttempt(sourceId: String, connection: HttpURLConnection) {
        activeConnections[sourceId] = connection
    }

    fun endAttempt(sourceId: String, connection: HttpURLConnection) {
        activeConnections.remove(sourceId, connection)
    }

    fun isCancelled(sourceId: String): Boolean = sourceId in cancelledIds

    fun cancel(sourceId: String) {
        cancelledIds += sourceId
        activeConnections[sourceId]?.disconnect()
    }

    /** Call once a download either finishes or fails, so a later re-download of the same id isn't born pre-cancelled. */
    fun reset(sourceId: String) {
        cancelledIds.remove(sourceId)
        activeConnections.remove(sourceId)
    }
}

class DownloadCancelledException : Exception("Cancelled")
