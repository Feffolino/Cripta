package com.cripta.app.data

import com.cripta.app.R
import com.cripta.app.str

/**
 * Why a media was not analysed (faces, intro) is stored in the database as "skip:<reason>", the
 * reason in Italian words since the first versions, so databases and backups everywhere hold these
 * same words. They are data: written unchanged, matched here and shown in the user's language.
 */
object SkipReasons {
    private val READ_ERROR = Regex("""^errore di lettura \((.*)\)$""")
    private val STALLED = Regex("""^analisi bloccata \(nessun fotogramma in (\d+) s\).*$""")
    private val UNSUPPORTED = Regex("""^audio (.*) non supportato dal telefono$""")

    /** [reason], with or without its "skip:" prefix, in words; an unknown reason as it is. */
    fun text(reason: String): String {
        val r = reason.removePrefix("skip").removePrefix(":").trim()
        READ_ERROR.matchEntire(r)?.let { return str(R.string.reason_read_error, it.groupValues[1]) }
        STALLED.matchEntire(r)?.let { return str(R.string.reason_stalled, it.groupValues[1]) }
        UNSUPPORTED.matchEntire(r)?.let { return str(R.string.reason_audio_unsupported, it.groupValues[1]) }
        return when (r) {
            "" -> str(R.string.reason_unknown)
            "nessun fotogramma leggibile" -> str(R.string.reason_no_frame)
            "immagine non decodificabile" -> str(R.string.reason_image_undecodable)
            "troppo grande per la memoria" -> str(R.string.reason_too_big)
            "volti eliminati da te" -> str(R.string.reason_faces_cleared)
            "durata non leggibile" -> str(R.string.reason_no_duration)
            "audio non decodificabile" -> str(R.string.reason_audio_undecodable)
            "video più corto di 20 secondi" -> str(R.string.reason_too_short)
            "nessuna traccia audio" -> str(R.string.reason_no_audio_track)
            else -> r
        }
    }
}
