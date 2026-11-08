package com.cripta.app.data

import com.cripta.app.R
import com.cripta.app.str
import com.cripta.app.plural

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * What a backup carries, chosen before it is made (remembered in the settings). Every group is on
 * by default: a backup is the whole vault unless the user leaves something out.
 */
data class BackupScope(
    /** Photos, videos, notes and other files, with their folders. */
    val files: Boolean = true,
    /** Top-level folders left out, with everything under them (ids of this device). */
    val excludedFolders: Set<Long> = emptySet(),
    /** Files that are in no folder. */
    val rootFiles: Boolean = true,
    /** Tags (names, aliases, colours, icons, pins) and saved filters; files keep their tags. */
    val tags: Boolean = true,
    /** People and faces. */
    val people: Boolean = true,
    /** Intro/outro analysis and the similar-media fingerprints. */
    val analysis: Boolean = true,
    /** The app's settings, except the security and device ones ([BackupSettings.EXCLUDED]). */
    val settings: Boolean = true,
    /** Video covers the user picked by hand. */
    val covers: Boolean = true,
) {
    /** Nothing at all would go in: the backup cannot be made. */
    val isEmpty: Boolean get() = !files && !tags && !people && !analysis && !settings

    /** The top-level folder holding [folderId] (itself when it has no parent, or the chain breaks). */
    fun topOf(folderId: Long, parents: Map<Long, Long?>): Long {
        var id = folderId
        var guard = 0
        while (guard++ < 1000) {
            val p = parents[id] ?: return id
            id = p
        }
        return id
    }

    /** The folder goes in the backup. */
    fun includesFolder(folderId: Long, parents: Map<Long, Long?>): Boolean =
        files && topOf(folderId, parents) !in excludedFolders

    /** A file in [folderId] (null: in no folder) goes in the backup. */
    fun includesFile(folderId: Long?, parents: Map<Long, Long?>): Boolean =
        files && if (folderId == null) rootFiles else includesFolder(folderId, parents)

    companion object {
        /** Position in the archive of each file id: ids change on restore, positions do not. */
        fun positions(ids: List<String>): Map<String, Int> = ids.withIndex().associate { (i, id) -> id to i }

        /** Ids stored as "1,5,9" in the settings. */
        fun parseIds(s: String?): Set<Long> = s.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
        fun formatIds(ids: Set<Long>): String = ids.sorted().joinToString(",")
    }
}

/**
 * The app's settings in a backup: every stored preference by name, type and value, so a setting
 * added later travels without touching this code. Left out are the ones that protect or belong to
 * this device ([EXCLUDED]): a restore must never weaken the protection of the device it runs on,
 * and ids of folders / tags change on restore.
 */
object BackupSettings {
    /**
     * - allow_screenshots, auto_lock_minutes: the protection of the device the vault is opened on;
     *   restored, a backup could switch screenshots on or lengthen the auto-lock unasked.
     * - viewer_rotation_lock: an orientation of this phone's screen.
     * - dl_folder, dl_tag_ids, backup_excluded_folders: folder / tag ids, which change on restore.
     */
    val EXCLUDED = setOf(
        "allow_screenshots", "auto_lock_minutes", "viewer_rotation_lock",
        "dl_folder", "dl_tag_ids", "backup_excluded_folders",
    )

    private const val VERSION = 1
    private const val T_BOOL = 1
    private const val T_INT = 2
    private const val T_LONG = 3
    private const val T_FLOAT = 4
    private const val T_DOUBLE = 5
    private const val T_STRING = 6
    private const val T_STRINGS = 7

    /** [values] (preference name to value) as bytes; excluded names and unknown types left out. */
    fun encode(values: Map<String, Any>): ByteArray {
        val kept = values.filterKeys { it !in EXCLUDED }.mapNotNull { (k, v) ->
            when (v) {
                is Boolean, is Int, is Long, is Float, is Double, is String -> k to v
                is Set<*> -> if (v.all { it is String }) k to v else null
                else -> null
            }
        }.sortedBy { it.first }
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { out ->
            out.writeInt(VERSION)
            out.writeInt(kept.size)
            for ((k, v) in kept) {
                out.writeUTF(k)
                when (v) {
                    is Boolean -> { out.writeByte(T_BOOL); out.writeBoolean(v) }
                    is Int -> { out.writeByte(T_INT); out.writeInt(v) }
                    is Long -> { out.writeByte(T_LONG); out.writeLong(v) }
                    is Float -> { out.writeByte(T_FLOAT); out.writeFloat(v) }
                    is Double -> { out.writeByte(T_DOUBLE); out.writeDouble(v) }
                    is String -> { out.writeByte(T_STRING); out.writeUTF(v) }
                    is Set<*> -> { out.writeByte(T_STRINGS); out.writeInt(v.size); v.forEach { out.writeUTF(it as String) } }
                }
            }
        }
        return bos.toByteArray()
    }

    /** What [encode] wrote; excluded names are dropped here too (an archive is not trusted). */
    fun decode(bytes: ByteArray): Map<String, Any> {
        val inp = DataInputStream(ByteArrayInputStream(bytes))
        val version = inp.readInt()
        require(version >= 1) { str(R.string.settings_invalid) }
        val n = inp.readInt()
        require(n in 0..10_000) { str(R.string.settings_invalid) }
        val out = LinkedHashMap<String, Any>()
        repeat(n) {
            val k = inp.readUTF()
            val v: Any = when (val t = inp.readUnsignedByte()) {
                T_BOOL -> inp.readBoolean()
                T_INT -> inp.readInt()
                T_LONG -> inp.readLong()
                T_FLOAT -> inp.readFloat()
                T_DOUBLE -> inp.readDouble()
                T_STRING -> inp.readUTF()
                T_STRINGS -> {
                    val c = inp.readInt()
                    require(c in 0..10_000) { str(R.string.settings_invalid) }
                    (0 until c).map { inp.readUTF() }.toSet()
                }
                else -> throw IllegalArgumentException(str(R.string.settings_unknown_kind, t))
            }
            if (k !in EXCLUDED) out[k] = v
        }
        return out
    }
}

/**
 * The part of a backup after [BackupExtras]: the settings and the hand-picked covers. Written as
 * [MAGIC], then records (kind, length, bytes) ending with kind 0, so a later kind is skipped by
 * this app and an app that predates the part stops before it.
 */
internal object BackupMore {
    const val MAGIC = 0x43524D52 // "CRMR"
    const val END = 0
    const val SETTINGS = 1
    /** Payload: the file's position in the archive (int), then the cover's JPEG. */
    const val COVER = 2
    private const val MAX_RECORD = 64_000_000

    fun begin(out: DataOutputStream) = out.writeInt(MAGIC)

    fun record(out: DataOutputStream, kind: Int, payload: ByteArray) {
        out.writeInt(kind); out.writeInt(payload.size); out.write(payload)
    }

    fun cover(out: DataOutputStream, position: Int, jpeg: ByteArray) {
        out.writeInt(COVER); out.writeInt(4 + jpeg.size); out.writeInt(position); out.write(jpeg)
    }

    fun end(out: DataOutputStream) = out.writeInt(END)

    /**
     * Read the records after the extras. Returns false when the archive has no such part (made
     * before it existed); an unknown kind is skipped.
     */
    fun read(inp: DataInputStream, onSettings: (ByteArray) -> Unit, onCover: (Int, ByteArray) -> Unit): Boolean {
        val magic = try { inp.readInt() } catch (e: java.io.EOFException) { return false }
        if (magic != MAGIC) return false
        while (true) {
            val kind = inp.readInt()
            if (kind == END) return true
            val len = inp.readInt()
            require(len in 0..MAX_RECORD) { str(R.string.file_corrupt) }
            when (kind) {
                SETTINGS -> onSettings(ByteArray(len).also { inp.readFully(it) })
                COVER -> {
                    require(len >= 4) { str(R.string.file_corrupt) }
                    val pos = inp.readInt()
                    onCover(pos, ByteArray(len - 4).also { inp.readFully(it) })
                }
                else -> inp.readFully(ByteArray(len))
            }
        }
    }
}

/** What a restore brought in, told to the user once it is done. */
data class RestoreReport(
    val files: Int,
    val tags: Int = 0,
    val people: Int = 0,
    val analysis: Boolean = false,
    val settings: Boolean = false,
    val covers: Int = 0,
) {
    fun describe(): String {
        val parts = buildList {
            add(plural(R.plurals.restored_files, files))
            if (tags > 0) add(plural(R.plurals.restored_tags, tags))
            if (people > 0) add(plural(R.plurals.restored_people, people))
            if (analysis) add(str(R.string.restored_analysis))
            if (covers > 0) add(plural(R.plurals.restored_covers, covers))
            if (settings) add(str(R.string.restored_settings))
        }
        return str(R.string.restored_list, parts.joinToString(", "))
    }
}
