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
        if (e.message in downloadMessages) return e.message!!
        val text = generateSequence(e) { it.cause }.take(6)
            .joinToString(" ") { it.message ?: "" }.lowercase()
        return when {
            "no space" in text || "enospc" in text -> "Spazio di archiviazione esaurito"
            "ssl" in text || "certificate verify failed" in text -> "Impossibile verificare il certificato del sito"
            "requested format" in text || "no video formats" in text -> "Nessun formato video disponibile alla qualità scelta"
            "no supported javascript runtime" in text || "javascript challenge" in text || "nsig extraction failed" in text ->
                "Il sito richiede un estrattore aggiornato. Riprova tra qualche minuto"
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
            "non ha prodotto" in text || "nessun file completo" in text || "nessun video completo" in text ->
                "Nessun file completo scaricato. Riprova"
            else -> of(e).let { if (it == "Si è verificato un errore imprevisto") "Download non riuscito" else it }
        }
    }

    // Error text goes through this mapper both in the service and again in the queue UI.
    // Keep canonical messages intact instead of replacing useful errors with "Download failed".
    private val downloadMessages = setOf(
        "Link non supportato", "Video protetto da DRM: non scaricabile", "Video privato o che richiede l'accesso",
        "Video non disponibile", "Video non disponibile nel tuo paese", "Troppe richieste al sito, riprova più tardi",
        "Accesso negato dal sito", "Pagina o video non trovato", "Connessione assente o sito non raggiungibile",
        "Nessun formato video disponibile alla qualità scelta", "Nessun file completo scaricato. Riprova",
        "Il sito richiede un estrattore aggiornato. Riprova tra qualche minuto",
        "Spazio di archiviazione esaurito", "Impossibile verificare il certificato del sito", "Download non riuscito",
    )

    private fun looksItalian(msg: String): Boolean {
        val m = msg.lowercase()
        return listOf("impossibile", "formato", "passphrase", "corrott", "incomplet", "troncato",
            "non valid", "scrittura", "lettura", "vault").any { it in m } &&
            listOf("exception", "failed", "error:", "null").none { it in m }
    }
}
