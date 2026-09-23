package com.cripta.app.work

/**
 * Turns exceptions into short Italian messages for the user. Raw exception text (English, often
 * a stack-trace fragment or a yt-dlp log line) must never reach the UI or a notification; the
 * technical detail is logged instead.
 */
object UserErrors {

    /** Generic mapping for vault operations (import, export, conversion, backup, scan). */
    fun of(e: Throwable): String {
        val chain = generateSequence(e) { it.cause }.take(6).toList()
        val text = chain.joinToString(" ") { (it.message ?: "") + " " + it.javaClass.simpleName }.lowercase()
        return when {
            chain.any { it is OutOfMemoryError } -> "Memoria insufficiente per completare l'operazione"
            "enospc" in text || "no space" in text -> "Spazio di archiviazione esaurito"
            "vault locked" in text -> "Il vault è stato bloccato durante l'operazione"
            chain.any { it is java.io.FileNotFoundException } -> "File non trovato o non più accessibile"
            chain.any { it is SecurityException } || "permission denial" in text ->
                "Permesso negato dall'app di origine del file"
            chain.any { it is java.net.UnknownHostException || it is java.net.ConnectException } ->
                "Nessuna connessione a Internet"
            chain.any { it is java.net.SocketTimeoutException } -> "La connessione è scaduta, riprova"
            chain.any { it is javax.crypto.AEADBadTagException } || "tag mismatch" in text ->
                "Dati cifrati non validi o passphrase errata"
            chain.any { it is java.security.GeneralSecurityException } -> "Errore di cifratura"
            // Our own Italian messages (IOException("Impossibile ..."), require { "Formato ..." }).
            e.message?.let { looksItalian(it) } == true -> e.message!!
            chain.any { it is java.io.IOException } -> "Errore di lettura o scrittura del file"
            else -> "Si è verificato un errore imprevisto"
        }
    }

    /** yt-dlp / network failures of the in-app downloader. */
    fun ofDownload(e: Throwable): String {
        val text = generateSequence(e) { it.cause }.take(6)
            .joinToString(" ") { it.message ?: "" }.lowercase()
        return when {
            "unsupported url" in text -> "Link non supportato"
            "drm" in text -> "Video protetto da DRM: non scaricabile"
            "private video" in text || "sign in" in text || "login" in text || "members-only" in text ->
                "Video privato o che richiede l'accesso"
            "not available" in text || "unavailable" in text || "removed" in text ->
                "Video non disponibile"
            "geo" in text && "restrict" in text -> "Video non disponibile nel tuo paese"
            "429" in text || "too many requests" in text -> "Troppe richieste al sito, riprova più tardi"
            "403" in text || "forbidden" in text -> "Accesso negato dal sito"
            "404" in text || "not found" in text -> "Pagina o video non trovato"
            "unable to download webpage" in text || "name or service not known" in text ||
                "failed to resolve" in text || "network is unreachable" in text ||
                "timed out" in text || "connection" in text -> "Connessione assente o sito non raggiungibile"
            "no video formats" in text || "requested format" in text -> "Nessun formato video disponibile"
            "non ha prodotto" in text -> "Nessun file scaricato (link errato?)"
            else -> of(e).let { if (it == "Si è verificato un errore imprevisto") "Download non riuscito" else it }
        }
    }

    private fun looksItalian(msg: String): Boolean {
        val m = msg.lowercase()
        return listOf("impossibile", "formato", "passphrase", "corrott", "incomplet", "troncato",
            "non valid", "scrittura", "lettura", "vault").any { it in m } &&
            listOf("exception", "failed", "error:", "null").none { it in m }
    }
}
