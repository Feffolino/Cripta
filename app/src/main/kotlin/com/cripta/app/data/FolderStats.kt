package com.cripta.app.data

import com.cripta.app.data.db.FolderAgg
import com.cripta.app.data.db.FolderEntity

/** Recursive folder totals (file count + total bytes, including all descendant folders). */
data class FolderStat(val count: Int, val bytes: Long)

/**
 * Fold each folder's direct aggregate over its entire subtree.
 * Cycle-safe via a visiting guard and memoized so it stays O(folders).
 */
fun computeFolderStats(folders: List<FolderEntity>, aggs: List<FolderAgg>): Map<Long, FolderStat> {
    val direct = aggs.associateBy { it.folderId }
    val childrenOf = folders.groupBy { it.parentId }
    val memo = HashMap<Long, FolderStat>()
    val visiting = HashSet<Long>()
    fun dfs(id: Long): FolderStat {
        memo[id]?.let { return it }
        if (!visiting.add(id)) return FolderStat(0, 0)
        var count = direct[id]?.cnt ?: 0
        var bytes = direct[id]?.bytes ?: 0L
        childrenOf[id]?.forEach { child ->
            val s = dfs(child.id)
            count += s.count
            bytes += s.bytes
        }
        visiting.remove(id)
        return FolderStat(count, bytes).also { memo[id] = it }
    }
    folders.forEach { dfs(it.id) }
    return memo
}
