package com.cripta.app.data

import android.app.Application
import com.cripta.app.Strings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class SkipReasonsTest {
    @Before
    fun texts() = Strings.init(RuntimeEnvironment.getApplication())

    private val codes = listOf(
        "nessun fotogramma leggibile",
        "immagine non decodificabile",
        "troppo grande per la memoria",
        "volti eliminati da te",
        "durata non leggibile",
        "audio non decodificabile",
        "video più corto di 20 secondi",
        "nessuna traccia audio",
    )

    @Test
    fun legacyDatabaseCodesArePresentedInEnglishWithoutRewritingThem() {
        val expected = listOf(
            "no readable frame", "image can't be decoded", "too big for the memory",
            "faces deleted by you", "length not readable", "audio can't be decoded",
            "video shorter than 20 seconds", "no audio track",
        )
        for ((code, translation) in codes.zip(expected)) {
            assertEquals(translation, SkipReasons.text(code))
            assertEquals(translation, SkipReasons.text("skip:$code"))
            assertEquals(translation, SkipReasons.text("skip:  $code  "))
        }
        for (blank in listOf("", " ", "skip", "skip:", "skip:  ")) {
            assertEquals("unknown reason", SkipReasons.text(blank))
        }
    }

    /**
     * The stored codes are the Italian texts, so in Italian each reads back as itself. This only checks
     * the Italian resources still match the codes: a branch missing from SkipReasons would also give the
     * code back here, and is caught by the English test above.
     */
    @Test
    @Config(qualifiers = "it")
    fun italianResourcesMatchTheStoredCodes() {
        for (code in codes) {
            assertEquals(code, SkipReasons.text(code))
            assertEquals(code, SkipReasons.text("skip:$code"))
        }
        assertEquals("motivo sconosciuto", SkipReasons.text("skip:"))
    }

    @Test
    fun structuredReasonsKeepTheirDetailsAndTranslateOnlyTheSurroundingText() {
        assertEquals("read error (decoder (code 7))",
            SkipReasons.text("skip:errore di lettura (decoder (code 7))"))
        assertEquals("AC-3 audio not supported by the phone",
            SkipReasons.text("audio AC-3 non supportato dal telefono"))
        val expected = "analysis stuck (no frame in 120 s): try again from the media's details, with no time limit"
        assertEquals(expected,
            SkipReasons.text("skip:analisi bloccata (nessun fotogramma in 120 s)"))
        assertEquals(expected,
            SkipReasons.text("analisi bloccata (nessun fotogramma in 120 s): legacy retry instructions"))
    }

    @Test
    @Config(qualifiers = "it")
    fun structuredReasonsUseItalianResourcesToo() {
        assertEquals("errore di lettura (decoder (code 7))",
            SkipReasons.text("skip:errore di lettura (decoder (code 7))"))
        assertEquals("audio AC-3 non supportato dal telefono",
            SkipReasons.text("skip:audio AC-3 non supportato dal telefono"))
        assertEquals("analisi bloccata (nessun fotogramma in 120 s): riprova dai dettagli del media, senza limite di tempo",
            SkipReasons.text("skip:analisi bloccata (nessun fotogramma in 120 s): old instructions"))
    }

    @Test
    fun unknownAndMalformedReasonsRemainReadableInsteadOfMatchingPartOfAKnownCode() {
        val unknown = listOf(
            "decoder from a newer version", "errore di lettura (unterminated",
            "prefix audio AAC non supportato dal telefono",
            "analisi bloccata (nessun fotogramma in abc s)",
        )
        for (reason in unknown) {
            assertEquals(reason, SkipReasons.text(reason))
            assertEquals(reason, SkipReasons.text("skip:$reason"))
        }
    }

    // Regression: "skip" was stripped even when it was not the stored "skip:" prefix.
    @Test
    fun unknownReasonBeginningWithSkipIsNotMistakenForTheDatabasePrefix() {
        assertEquals("skipped by newer decoder", SkipReasons.text("skipped by newer decoder"))
    }
}
