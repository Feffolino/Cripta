package com.cripta.app.data.dedup

import com.cripta.app.data.db.FileEntity

/**
 * What a duplicate scan proved about a file, kept apart from what the file is now. Results are
 * kept and can be opened later: a copy edited (a note), deleted or trashed since then is no longer
 * the copy the scan saw, and a refresh of the results must not take its new state as the proof.
 */
object DupIdentity {
    /**
     * [now] as a candidate of the result that saw [scanned], with the size and hash the scan proved;
     * null when it is no longer that copy (gone, in the trash, another size, or another hash).
     * With no hash seen by the scan (a similar group: photos and videos, which are never rewritten),
     * one gained since is fine; the scan's hash is never replaced by the file's new one.
     */
    fun stillScanned(scanned: FileEntity, now: FileEntity?): FileEntity? {
        if (now == null || now.deletedAt != null || now.sizeBytes != scanned.sizeBytes) return null
        if (scanned.contentHash != null && now.contentHash != scanned.contentHash) return null
        return now.copy(sizeBytes = scanned.sizeBytes, contentHash = scanned.contentHash)
    }
}
