package com.cripta.app.work

import com.cripta.app.R
import com.cripta.app.str

/**
 * Turns exceptions into short messages for the user, in their language. Raw exception text (often
 * a stack-trace fragment or a yt-dlp log line) must never reach the UI or a notification; the
 * technical detail is logged instead.
 */
object UserErrors {

    /** Generic mapping for vault operations (import, export, conversion, backup, scan). */
    fun of(e: Throwable): String {
        val chain = generateSequence(e) { it.cause }.take(6).toList()
        val text = chain.joinToString(" ") { (it.message ?: "") + " " + it.javaClass.simpleName }.lowercase()
        return when {
            chain.any { it is OutOfMemoryError } -> str(R.string.err_out_of_memory)
            "enospc" in text || "no space" in text -> str(R.string.err_no_space)
            "vault locked" in text -> str(R.string.err_vault_locked_during)
            chain.any { it is java.io.FileNotFoundException } -> str(R.string.err_file_not_found)
            chain.any { it is SecurityException } || "permission denial" in text ->
                str(R.string.err_permission_denied)
            chain.any { it is java.net.UnknownHostException || it is java.net.ConnectException } ->
                str(R.string.err_no_internet)
            chain.any { it is java.net.SocketTimeoutException } -> str(R.string.err_timeout)
            chain.any { it is javax.crypto.AEADBadTagException } || "tag mismatch" in text ->
                str(R.string.err_bad_encrypted)
            chain.any { it is java.security.GeneralSecurityException } -> str(R.string.err_crypto)
            // Only the app's own resource texts pass through; never guess from the words in a message.
            e.message?.let { isOwn(it) } == true -> e.message!!
            chain.any { it is java.io.IOException } -> str(R.string.err_io)
            else -> str(R.string.err_unexpected)
        }
    }

    /** yt-dlp / network failures of the in-app downloader. */
    fun ofDownload(e: Throwable): String {
        if (e.message in downloadMessages) return e.message!!
        if (generateSequence(e) { it.cause }.take(6).any { it is com.cripta.app.media.IncompleteDownloadException }) {
            return str(R.string.dl_err_incomplete)
        }
        // Without the links in it: a word of a link ("/drm/", "login-…") is not the reason.
        val text = generateSequence(e) { it.cause }.take(6)
            .joinToString(" ") { it.message ?: "" }.lowercase().replace(LINK, " ")
        fun has(vararg words: Regex) = words.any { it.containsMatchIn(text) }
        return when {
            "no space" in text || "enospc" in text -> str(R.string.err_no_space)
            has(SSL) || "certificate verify failed" in text -> str(R.string.dl_err_certificate)
            "requested format" in text || "no video formats" in text -> str(R.string.dl_err_no_format)
            "no supported javascript runtime" in text || "javascript challenge" in text || "nsig extraction failed" in text ->
                str(R.string.dl_err_extractor)
            "unsupported url" in text -> str(R.string.dl_err_unsupported)
            has(DRM) -> str(R.string.dl_err_drm)
            "private video" in text || "sign in" in text || has(LOGIN) || "members-only" in text ->
                str(R.string.dl_err_private)
            "not available" in text || "unavailable" in text || has(REMOVED) ->
                str(R.string.dl_err_unavailable)
            has(GEO) && "restrict" in text -> str(R.string.dl_err_geo)
            has(HTTP_429) || "too many requests" in text -> str(R.string.dl_err_too_many)
            has(HTTP_403) || "forbidden" in text -> str(R.string.dl_err_forbidden)
            has(HTTP_404) || "not found" in text -> str(R.string.dl_err_not_found)
            "unable to download webpage" in text || "name or service not known" in text ||
                "failed to resolve" in text || "network is unreachable" in text ||
                "timed out" in text || has(CONNECTION) -> str(R.string.dl_err_connection)
            else -> of(e).let { if (it == str(R.string.err_unexpected)) str(R.string.dl_err_failed) else it }
        }
    }

    // Words, not parts of words or numbers: "14043" is no HTTP 404, "geoffrey" no geo block.
    private val LINK = Regex("""[a-z][a-z0-9+.-]*://\S+""")
    private val SSL = Regex("""\bssl""")
    private val DRM = Regex("""\bdrm\b""")
    private val LOGIN = Regex("""\blog ?in\b""")
    private val REMOVED = Regex("""\bremoved\b""")
    private val GEO = Regex("""\bgeo""")
    private val HTTP_429 = Regex("""\b429\b""")
    private val HTTP_403 = Regex("""\b403\b""")
    private val HTTP_404 = Regex("""\b404\b""")
    private val CONNECTION = Regex("""\bconnection""")

    // Error text goes through this mapper both in the service and again in the queue UI.
    // Keep canonical messages intact instead of replacing useful errors with "Download failed".
    private val downloadMessages get() = setOf(
        str(R.string.dl_err_unsupported), str(R.string.dl_err_drm), str(R.string.dl_err_private),
        str(R.string.dl_err_unavailable), str(R.string.dl_err_geo), str(R.string.dl_err_too_many),
        str(R.string.dl_err_forbidden), str(R.string.dl_err_not_found), str(R.string.dl_err_connection),
        str(R.string.dl_err_no_format), str(R.string.dl_err_incomplete),
        str(R.string.dl_err_extractor), str(R.string.dl_err_player_format),
        str(R.string.err_no_space), str(R.string.dl_err_certificate), str(R.string.dl_err_failed),
    )

    /** The app's own messages (thrown in the user's language), shown as they are. */
    private val OWN = intArrayOf(
        R.string.err_open_import, R.string.err_write_converted, R.string.err_write_gallery,
        R.string.err_create_in_folder, R.string.err_write_folder, R.string.err_write_backup, R.string.err_read_incomplete,
        R.string.err_open_backup, R.string.err_bad_format, R.string.err_wrong_passphrase_or_corrupt, R.string.file_corrupt,
        R.string.err_backup_truncated, R.string.err_wrong_passphrase_or_invalid, R.string.err_note_save, R.string.settings_invalid,
        R.string.settings_unknown_kind, R.string.update_checksum_missing, R.string.update_checksum_invalid, R.string.update_checksum_mismatch,
        R.string.update_checksum_required, R.string.update_wrong_package,
        R.string.dl_err_analyse_downloaded, R.string.err_old_backup_too_large,
    )

    private fun isOwn(msg: String): Boolean = OWN.any { id ->
        // Unformatted: a placeholder stays as "%1$s", so a message with an argument matches by its start.
        val raw = com.cripta.app.Strings.context.getString(id)
        val head = raw.substringBefore('%')
        if (head.length == raw.length) msg == raw else head.isNotEmpty() && msg.startsWith(head)
    }
}
