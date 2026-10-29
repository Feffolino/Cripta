# Intro/outro: rilevamento e skip — piano di implementazione

> **Per chi esegue:** usare superpowers:executing-plans, **inline** (l'utente ha chiesto di non usare
> subagent). Gli step usano checkbox (`- [ ]`).

**Obiettivo:** riconoscere intro e finali dei video confrontando l'audio di tutto il vault, segnarli a
mano, scartare i falsi positivi e saltarli nel player con un pulsante o in automatico.

**Architettura:** parti pure e testate con JUnit (`MonoResampler`, `Fingerprint`, `SegmentMatcher`,
`IntroDetector`, `SegmentSkipper`) in `media/intro/` e `ui/viewer/`. Decodifica audio Android
(`AudioDecoder`) sul flusso cifrato. Dati in tre tabelle Room/SQLCipher (DB v16). Scansione come
modalità di `ConversionService`. Player e impostazioni in Compose.

**Stack:** Kotlin, MediaExtractor/MediaCodec, Room + SQLCipher, Hilt, Jetpack Compose, Media3,
JUnit 4 + Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-29-intro-outro-skip-design.md`

---

## Comandi

Build e test locali (Gradle 8.9 e JDK 17 dalla cache di Gradle, come in CI). In PowerShell:

```powershell
$env:JAVA_HOME="C:\Users\stefy_zgbvz6k\.gradle\jdks\adoptium-17-x64-hotspot-windows\jdk-17.0.14+7"
& "C:\Users\stefy_zgbvz6k\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat" :app:testDebugUnitTest --tests "<classe di test>" --console=plain -q
```

In questo piano `GRADLE` indica le due righe sopra. Esito: l'XML in
`app/build/test-results/testDebugUnitTest/TEST-<classe>.xml` riporta `failures="0" errors="0"`.

## Mappa dei file

| File | Ruolo |
|---|---|
| `app/src/main/kotlin/com/cripta/app/media/intro/PrintCodec.kt` (nuovo) | `IntArray` ↔ `ByteArray` per i BLOB |
| `app/src/main/kotlin/com/cripta/app/media/intro/MonoResampler.kt` (nuovo) | PCM interleaved → mono 5512 Hz, in streaming |
| `app/src/main/kotlin/com/cripta/app/media/intro/Fingerprint.kt` (nuovo) | PCM → hash da 32 bit (FFT, bande, bit) |
| `app/src/main/kotlin/com/cripta/app/media/intro/SegmentMatcher.kt` (nuovo) | tratto comune più lungo tra due sequenze |
| `app/src/main/kotlin/com/cripta/app/media/intro/IntroDetector.kt` (nuovo) | `SegmentKind`, `Print`, `Found`, regole |
| `app/src/main/kotlin/com/cripta/app/media/intro/AudioDecoder.kt` (nuovo) | decodifica audio dal flusso cifrato |
| `app/src/main/kotlin/com/cripta/app/media/ChannelMediaDataSource.kt` (nuovo) | estratto da `ThumbnailLoader.kt` |
| `app/src/main/kotlin/com/cripta/app/data/intro/IntroRepository.kt` (nuovo) | impronte, match, segni manuali, scarti |
| `app/src/main/kotlin/com/cripta/app/data/db/Entities.kt` | 3 entità nuove |
| `app/src/main/kotlin/com/cripta/app/data/db/Daos.kt` | `IntroDao` |
| `app/src/main/kotlin/com/cripta/app/data/db/CriptaDatabase.kt` | v16, `MIGRATION_15_16` |
| `app/src/main/kotlin/com/cripta/app/data/VaultRepository.kt` | cascata, segni manuali su nuova versione, backup |
| `app/src/main/kotlin/com/cripta/app/data/SettingsStore.kt` | 3 impostazioni |
| `app/src/main/kotlin/com/cripta/app/work/ConversionService.kt` | modalità scansione + avvio dopo import |
| `app/src/main/kotlin/com/cripta/app/ui/settings/SettingsViewModel.kt`, `SettingsScreen.kt` | sezione "Intro e finale" |
| `app/src/main/kotlin/com/cripta/app/ui/viewer/SegmentSkipper.kt` (nuovo) | logica pura del pulsante/skip |
| `app/src/main/kotlin/com/cripta/app/ui/viewer/IntroOutroSheet.kt` (nuovo) | sheet "Intro e finale" |
| `app/src/main/kotlin/com/cripta/app/ui/viewer/ViewerViewModel.kt`, `ViewerScreen.kt` | integrazione player |
| `app/src/test/kotlin/com/cripta/app/media/intro/*Test.kt`, `.../ui/viewer/SegmentSkipperTest.kt`, `.../data/db/IntroMigrationTest.kt` (nuovi) | test |

---

### Task 1: PrintCodec e MonoResampler

**Files:**
- Create: `app/src/main/kotlin/com/cripta/app/media/intro/PrintCodec.kt`
- Create: `app/src/main/kotlin/com/cripta/app/media/intro/MonoResampler.kt`
- Test: `app/src/test/kotlin/com/cripta/app/media/intro/MonoResamplerTest.kt`

- [ ] **Step 1: test che falliscono**

```kotlin
package com.cripta.app.media.intro

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonoResamplerTest {
    @Test
    fun halvesTheRateByAveragingPairs() {
        val r = MonoResampler(MonoResampler.OUT_RATE * 2, channels = 1)
        r.push(floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f))
        assertArrayEquals(floatArrayOf(0.5f, 2.5f, 4.5f), r.result(), 1e-6f)
    }

    @Test
    fun averagesChannels() {
        val r = MonoResampler(MonoResampler.OUT_RATE, channels = 2)
        r.push(floatArrayOf(1f, 3f, -1f, -3f))
        assertArrayEquals(floatArrayOf(2f, -2f), r.result(), 1e-6f)
    }

    @Test
    fun oneSecondAt48kGivesOneSecondOut() {
        val r = MonoResampler(48_000, channels = 1)
        val chunk = FloatArray(4_800)
        repeat(10) { r.push(chunk) }
        assertTrue(kotlin.math.abs(r.size - MonoResampler.OUT_RATE) <= 1)
    }

    @Test
    fun codecRoundTrips() {
        val h = intArrayOf(0, 1, -1, Int.MAX_VALUE, Int.MIN_VALUE, 123456789)
        assertArrayEquals(h, PrintCodec.decode(PrintCodec.encode(h)))
        assertEquals(24, PrintCodec.encode(h).size)
    }
}
```

- [ ] **Step 2: eseguire, deve fallire** — `GRADLE ... --tests "com.cripta.app.media.intro.MonoResamplerTest"`. Atteso: errore di compilazione (classi assenti).

- [ ] **Step 3: implementazione**

`PrintCodec.kt`:

```kotlin
package com.cripta.app.media.intro

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Fingerprint hashes as little-endian bytes, the form stored in the database. */
object PrintCodec {
    fun encode(hashes: IntArray): ByteArray {
        val bb = ByteBuffer.allocate(hashes.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        bb.asIntBuffer().put(hashes)
        return bb.array()
    }

    fun decode(bytes: ByteArray): IntArray {
        val ib = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        return IntArray(ib.remaining()).also { ib.get(it) }
    }
}
```

`MonoResampler.kt`:

```kotlin
package com.cripta.app.media.intro

/**
 * Streaming downmix and downsample of interleaved PCM to mono [OUT_RATE] Hz. Each output sample is
 * the mean of the input samples in its interval: a crude low-pass, but the same for every video,
 * which is all the fingerprint comparison needs.
 */
class MonoResampler(srcRate: Int, private val channels: Int) {
    private val step = srcRate.toDouble() / OUT_RATE
    private var next = step
    private var pos = 0L
    private var acc = 0.0
    private var cnt = 0
    private var ch = 0
    private var frame = 0.0
    private var out = FloatArray(1 shl 16)

    /** Output samples so far. */
    var size = 0
        private set

    /** [samples] in -1..1, interleaved by channel; only the first [count] are read. */
    fun push(samples: FloatArray, count: Int = samples.size) {
        for (i in 0 until count) {
            frame += samples[i]
            if (++ch < channels) continue
            ch = 0
            acc += frame / channels; frame = 0.0
            cnt++; pos++
            if (pos >= next) {
                if (size == out.size) out = out.copyOf(size * 2)
                out[size++] = (acc / cnt).toFloat()
                acc = 0.0; cnt = 0
                next += step
            }
        }
    }

    fun result(): FloatArray = out.copyOf(size)

    companion object {
        const val OUT_RATE = 5512
    }
}
```

- [ ] **Step 4: eseguire, deve passare** — stesso comando. Atteso: 4 test, 0 failure.

- [ ] **Step 5: commit**

```bash
git add app/src/main/kotlin/com/cripta/app/media/intro/PrintCodec.kt app/src/main/kotlin/com/cripta/app/media/intro/MonoResampler.kt app/src/test/kotlin/com/cripta/app/media/intro/MonoResamplerTest.kt
git commit -m "Intro: mono resampler and hash codec for audio fingerprints"
```

---

### Task 2: Fingerprint

**Files:**
- Create: `app/src/main/kotlin/com/cripta/app/media/intro/Fingerprint.kt`
- Test: `app/src/test/kotlin/com/cripta/app/media/intro/FingerprintTest.kt`

- [ ] **Step 1: test che falliscono**

```kotlin
package com.cripta.app.media.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintTest {
    private fun noise(seconds: Double, seed: Long, amp: Float = 1f): FloatArray {
        val r = java.util.Random(seed)
        return FloatArray((seconds * MonoResampler.OUT_RATE).toInt()) { (r.nextFloat() * 2 - 1) * amp }
    }

    /** Mean share of differing bits between two hash sequences (over the shorter one). */
    private fun ber(a: IntArray, b: IntArray): Double {
        val n = minOf(a.size, b.size)
        return (0 until n).sumOf { Integer.bitCount(a[it] xor b[it]) } / (32.0 * n)
    }

    @Test
    fun sameAudioSameHashes() {
        val a = Fingerprint.hashes(noise(20.0, 1))
        val b = Fingerprint.hashes(noise(20.0, 1))
        assertTrue(a.contentEquals(b))
        assertEquals((20 * MonoResampler.OUT_RATE - Fingerprint.FRAME) / Fingerprint.HOP, a.size)
    }

    @Test
    fun lightNoiseFlipsFewBits() {
        val clean = noise(20.0, 1)
        val extra = noise(20.0, 99, amp = 0.02f)
        val noisy = FloatArray(clean.size) { clean[it] + extra[it] }
        assertTrue(ber(Fingerprint.hashes(clean), Fingerprint.hashes(noisy)) < 0.2)
    }

    @Test
    fun offsetOfAQuarterHopStaysClose() {
        val src = noise(20.0, 1)
        val shifted = src.copyOfRange(Fingerprint.HOP / 4, src.size)
        assertTrue(ber(Fingerprint.hashes(src), Fingerprint.hashes(shifted)) < 0.3)
    }

    @Test
    fun differentAudioFlipsAboutHalf() {
        assertTrue(ber(Fingerprint.hashes(noise(20.0, 1)), Fingerprint.hashes(noise(20.0, 2))) > 0.35)
    }

    @Test
    fun silenceGivesZeroHashes() {
        assertTrue(Fingerprint.hashes(FloatArray(5 * MonoResampler.OUT_RATE)).all { it == 0 })
    }
}
```

- [ ] **Step 2: eseguire, deve fallire** — `--tests "com.cripta.app.media.intro.FingerprintTest"`. Atteso: compilazione fallita.

- [ ] **Step 3: implementazione**

```kotlin
package com.cripta.app.media.intro

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Chromaprint-style audio fingerprint: one 32-bit hash every [HOP] samples of mono
 * [MonoResampler.OUT_RATE] Hz audio (about 8 per second). Bit b says whether the energy difference
 * between bands b and b+1 grew since the previous frame (Haitsma–Kalker). A re-encode of the same
 * audio flips few bits, different audio about half. Digital silence gives 0, which the matcher
 * never counts as a match.
 */
object Fingerprint {
    const val FRAME = 2048
    const val HOP = 690

    /** Milliseconds between consecutive hashes. */
    const val HOP_MS = HOP * 1000.0 / MonoResampler.OUT_RATE

    private const val BANDS = 33
    private const val MIN_HZ = 300.0
    private const val MAX_HZ = 2000.0

    private val window = DoubleArray(FRAME) { 0.5 - 0.5 * cos(2 * PI * it / (FRAME - 1)) }

    /** FFT bin edges of the log-spaced bands. */
    private val edges = IntArray(BANDS + 1) { b ->
        val hz = MIN_HZ * (MAX_HZ / MIN_HZ).pow(b.toDouble() / BANDS)
        (hz * FRAME / MonoResampler.OUT_RATE).roundToInt()
    }

    fun hashes(pcm: FloatArray): IntArray {
        if (pcm.size < FRAME) return IntArray(0)
        val frames = (pcm.size - FRAME) / HOP + 1
        val out = IntArray(frames - 1)
        val re = DoubleArray(FRAME)
        val im = DoubleArray(FRAME)
        var prev: DoubleArray? = null
        for (f in 0 until frames) {
            val off = f * HOP
            for (i in 0 until FRAME) { re[i] = pcm[off + i] * window[i]; im[i] = 0.0 }
            fft(re, im)
            val e = DoubleArray(BANDS) { b ->
                var s = 0.0
                for (k in edges[b] until maxOf(edges[b + 1], edges[b] + 1)) s += re[k] * re[k] + im[k] * im[k]
                s
            }
            val p = prev
            if (p != null) {
                var h = 0
                for (b in 0 until 32) if ((e[b] - e[b + 1]) - (p[b] - p[b + 1]) > 0) h = h or (1 shl b)
                out[f - 1] = h
            }
            prev = e
        }
        return out
    }

    /** In-place iterative radix-2 FFT; the size must be a power of two. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
```

- [ ] **Step 4: eseguire, deve passare.** Atteso: 5 test verdi. Se fallisce solo
  `offsetOfAQuarterHopStaysClose`, portare `HOP` a 345 (16 hash al secondo, disallineamento massimo
  dimezzato) e rieseguire; aggiornare la spec ("circa 16 hash al secondo", "circa 60 KB a video").

- [ ] **Step 5: commit**

```bash
git add app/src/main/kotlin/com/cripta/app/media/intro/Fingerprint.kt app/src/test/kotlin/com/cripta/app/media/intro/FingerprintTest.kt
git commit -m "Intro: Chromaprint-style audio fingerprint"
```

---

### Task 3: SegmentMatcher

**Files:**
- Create: `app/src/main/kotlin/com/cripta/app/media/intro/SegmentMatcher.kt`
- Test: `app/src/test/kotlin/com/cripta/app/media/intro/SegmentMatcherTest.kt`

- [ ] **Step 1: test che falliscono**

```kotlin
package com.cripta.app.media.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Random

class SegmentMatcherTest {
    private fun random(n: Int, seed: Long) = Random(seed).let { r -> IntArray(n) { r.nextInt() or 1 } }

    /** [src] with [bits] random bits flipped in each hash (a re-encode). */
    private fun noisy(src: IntArray, seed: Long, bits: Int = 2): IntArray {
        val r = Random(seed)
        return IntArray(src.size) { i -> var x = src[i]; repeat(bits) { x = x xor (1 shl r.nextInt(32)) }; x }
    }

    @Test
    fun findsSharedStretchAtAnyOffset() {
        val a = random(400, 1)
        val b = random(300, 2)
        noisy(a.copyOfRange(100, 250), 3).copyInto(b, destinationOffset = 40)
        val m = SegmentMatcher.best(a, b, minLength = 120)
        assertNotNull(m); m!!
        assertEquals(100.0, m.aStart.toDouble(), 2.0)
        assertEquals(250.0, m.aEnd.toDouble(), 2.0)
        assertEquals(m.aStart - 60, m.bStart)
        assertEquals(m.length, m.bEnd - m.bStart)
    }

    @Test
    fun nothingShared() {
        assertNull(SegmentMatcher.best(random(400, 1), random(400, 2), minLength = 120))
    }

    @Test
    fun sharedStretchShorterThanTheMinimum() {
        val a = random(400, 1)
        val b = random(300, 2)
        a.copyOfRange(100, 150).copyInto(b, destinationOffset = 40)
        assertNull(SegmentMatcher.best(a, b, minLength = 120))
    }

    @Test
    fun sharedSilenceIsNotAMatch() {
        val a = random(400, 1).also { IntArray(200).copyInto(it, 100) }
        val b = random(400, 2).also { IntArray(200).copyInto(it, 50) }
        assertNull(SegmentMatcher.best(a, b, minLength = 120))
    }
}
```

- [ ] **Step 2: eseguire, deve fallire** — `--tests "com.cripta.app.media.intro.SegmentMatcherTest"`.

- [ ] **Step 3: implementazione**

```kotlin
package com.cripta.app.media.intro

/** A stretch of audio two hash sequences share, as hash indices (ends exclusive). */
data class Match(val aStart: Int, val aEnd: Int, val bStart: Int, val bEnd: Int) {
    val length: Int get() = aEnd - aStart
}

/**
 * Finds the longest stretch two fingerprints share. Hashes whose top bits agree vote for an
 * alignment (b index − a index); the best few alignments are then walked hash by hash.
 */
object SegmentMatcher {
    /** Votes use the top 20 bits: the low bits flip most under re-encoding. */
    private const val KEY_SHIFT = 12
    /** Mean differing bits (of 32) over [WINDOW] hashes still taken as the same audio. */
    private const val MAX_BITS = 10
    private const val WINDOW = 8
    private const val MAX_OFFSETS = 5
    private const val MIN_VOTES = 3

    fun best(a: IntArray, b: IntArray, minLength: Int): Match? {
        if (a.isEmpty() || b.isEmpty()) return null
        val index = HashMap<Int, MutableList<Int>>()
        for (j in b.indices) if (b[j] != 0) index.getOrPut(b[j] ushr KEY_SHIFT) { mutableListOf() }.add(j)
        val votes = HashMap<Int, Int>()
        for (i in a.indices) {
            if (a[i] == 0) continue
            index[a[i] ushr KEY_SHIFT]?.forEach { j -> votes.merge(j - i, 1, Int::plus) }
        }
        return votes.entries.filter { it.value >= MIN_VOTES }
            .sortedByDescending { it.value }.take(MAX_OFFSETS)
            .mapNotNull { runAt(a, b, it.key, minLength) }
            .maxByOrNull { it.length }
    }

    /** Longest run of [a] against [b] shifted by [offset] (b index = a index + offset). */
    private fun runAt(a: IntArray, b: IntArray, offset: Int, minLength: Int): Match? {
        val from = maxOf(0, -offset)
        val to = minOf(a.size, b.size - offset)
        if (to - from < maxOf(minLength, WINDOW)) return null
        val err = IntArray(to - from) { k ->
            val x = a[from + k]; val y = b[from + k + offset]
            if (x == 0 || y == 0) 32 else Integer.bitCount(x xor y)
        }
        // A window is good when its hashes average at most MAX_BITS; a run of good windows covers
        // from its first window's start to its last window's end.
        var bestStart = 0; var bestLen = 0; var runStart = -1
        var sum = 0
        for (k in 0 until WINDOW) sum += err[k]
        val n = err.size - WINDOW + 1
        for (k in 0 until n) {
            if (k > 0) sum += err[k + WINDOW - 1] - err[k - 1]
            val good = sum <= MAX_BITS * WINDOW
            if (good && runStart < 0) runStart = k
            if ((!good || k == n - 1) && runStart >= 0) {
                val end = (if (good) k else k - 1) + WINDOW
                if (end - runStart > bestLen) { bestLen = end - runStart; bestStart = runStart }
                runStart = -1
            }
        }
        // Windows reach a few hashes past the shared audio on each side: trim those.
        while (bestLen > 0 && err[bestStart] > MAX_BITS) { bestStart++; bestLen-- }
        while (bestLen > 0 && err[bestStart + bestLen - 1] > MAX_BITS) bestLen--
        if (bestLen < minLength || bestLen == 0) return null
        val s = from + bestStart
        return Match(s, s + bestLen, s + offset, s + offset + bestLen)
    }
}
```

- [ ] **Step 4: eseguire, deve passare.** Atteso: 4 test verdi.

- [ ] **Step 5: commit**

```bash
git add app/src/main/kotlin/com/cripta/app/media/intro/SegmentMatcher.kt app/src/test/kotlin/com/cripta/app/media/intro/SegmentMatcherTest.kt
git commit -m "Intro: find the longest stretch two fingerprints share"
```

---

### Task 4: Database v16

**Files:**
- Modify: `app/src/main/kotlin/com/cripta/app/data/db/Entities.kt` (in fondo)
- Modify: `app/src/main/kotlin/com/cripta/app/data/db/Daos.kt` (in fondo)
- Modify: `app/src/main/kotlin/com/cripta/app/data/db/CriptaDatabase.kt:12-29,129-136,155`
- Modify: `app/src/main/kotlin/com/cripta/app/data/VaultRepository.kt:1064-1070` (`secureDelete`)
- Test: `app/src/test/kotlin/com/cripta/app/data/db/IntroMigrationTest.kt`

- [ ] **Step 1: test che fallisce**

```kotlin
package com.cripta.app.data.db

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class IntroMigrationTest {
    private fun columns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            buildList {
                while (c.moveToNext()) add("${c.getString(1)}|${c.getString(2)}|${c.getInt(3)}|${c.getInt(5)}")
            }.sorted()
        }

    @Test
    fun migration15to16CreatesTheTablesRoomExpects() {
        val app = RuntimeEnvironment.getApplication()
        val fresh = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val expected = fresh.openHelper.writableDatabase
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app).name(null).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }).build()
        )
        val migrated = helper.writableDatabase
        CriptaDatabase.MIGRATION_15_16.migrate(migrated)
        for (t in listOf("audio_prints", "media_segments", "rejected_prints")) {
            assertEquals(t, columns(expected, t), columns(migrated, t))
        }
        fresh.close(); helper.close()
    }
}
```

- [ ] **Step 2: eseguire, deve fallire** — `--tests "com.cripta.app.data.db.IntroMigrationTest"`. Atteso: compilazione fallita (`MIGRATION_15_16` assente).

- [ ] **Step 3: entità** — in fondo a `Entities.kt`:

```kotlin
/** Audio fingerprint of a video's first minutes ("head") or last minutes ("tail"), for intros and outros. */
@Entity(tableName = "audio_prints", primaryKeys = ["fileId", "part"])
data class AudioPrintEntity(
    val fileId: String,
    /** "head" or "tail". A video that cannot be analysed has one "head" row with status "skip". */
    val part: String,
    /** Time of the first hash in the video (ms). */
    val startMs: Long,
    /** Hashes encoded by [com.cripta.app.media.intro.PrintCodec]. */
    val hashes: ByteArray,
    /** "ok" or "skip" (too short, no audio track, undecodable). */
    val status: String,
)

/** A video's intro or outro. */
@Entity(tableName = "media_segments", primaryKeys = ["fileId", "kind"])
data class MediaSegmentEntity(
    val fileId: String,
    /** "intro" or "outro". */
    val kind: String,
    val startMs: Long,
    val endMs: Long,
    /** "auto" (detected), "manual" (marked by the user) or "none" (the user said there is none). */
    val source: String,
)

/** Audio the user said is no intro/outro in any video: stretches matching it never become segments. */
@Entity(tableName = "rejected_prints")
data class RejectedPrintEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    /** The video it was rejected on (it may be gone since) and where in it. */
    val originFileId: String,
    val originName: String,
    val startMs: Long,
    val endMs: Long,
    val hashes: ByteArray,
    val createdAt: Long,
)
```

- [ ] **Step 4: DAO** — in fondo a `Daos.kt` (gli import `Dao`, `Insert`, `OnConflictStrategy`, `Query`,
  `Flow` ci sono già):

```kotlin
@Dao
interface IntroDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPrint(p: AudioPrintEntity)

    @Query("SELECT * FROM audio_prints WHERE fileId = :fileId")
    suspend fun printsOf(fileId: String): List<AudioPrintEntity>

    @Query("SELECT * FROM audio_prints WHERE status = 'ok' AND part = :part")
    suspend fun allPrints(part: String): List<AudioPrintEntity>

    @Query("SELECT DISTINCT fileId FROM audio_prints")
    suspend fun printedIds(): List<String>

    @Query("DELETE FROM audio_prints WHERE fileId = :fileId")
    suspend fun clearPrints(fileId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSegment(s: MediaSegmentEntity)

    @Query("SELECT * FROM media_segments WHERE fileId = :fileId")
    suspend fun segmentsOf(fileId: String): List<MediaSegmentEntity>

    @Query("SELECT * FROM media_segments WHERE fileId = :fileId")
    fun observeSegments(fileId: String): Flow<List<MediaSegmentEntity>>

    @Query("SELECT * FROM media_segments WHERE kind = :kind")
    suspend fun segmentsOfKind(kind: String): List<MediaSegmentEntity>

    @Query("DELETE FROM media_segments WHERE fileId = :fileId AND kind = :kind")
    suspend fun deleteSegment(fileId: String, kind: String)

    @Query("DELETE FROM media_segments WHERE fileId = :fileId")
    suspend fun clearSegments(fileId: String)

    @Insert
    suspend fun addRejected(r: RejectedPrintEntity): Long

    @Query("SELECT * FROM rejected_prints ORDER BY createdAt DESC")
    fun observeRejected(): Flow<List<RejectedPrintEntity>>

    @Query("SELECT * FROM rejected_prints WHERE kind = :kind")
    suspend fun rejectedOfKind(kind: String): List<RejectedPrintEntity>

    @Query("SELECT * FROM rejected_prints WHERE id = :id")
    suspend fun rejectedById(id: Long): RejectedPrintEntity?

    @Query("DELETE FROM rejected_prints WHERE id = :id")
    suspend fun deleteRejected(id: Long)
}
```

- [ ] **Step 5: database** — in `CriptaDatabase.kt`:
  - in `entities` aggiungere `AudioPrintEntity::class, MediaSegmentEntity::class, RejectedPrintEntity::class`;
  - `version = 16`;
  - dopo `pendingJobDao()`: `abstract fun introDao(): IntroDao`;
  - dopo `MIGRATION_14_15`:

```kotlin
        /** Intro/outro detection: fingerprints, segments, rejected samples. */
        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audio_prints` (`fileId` TEXT NOT NULL, `part` TEXT NOT NULL, " +
                        "`startMs` INTEGER NOT NULL, `hashes` BLOB NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`fileId`, `part`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_segments` (`fileId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`fileId`, `kind`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rejected_prints` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `originFileId` TEXT NOT NULL, `originName` TEXT NOT NULL, `startMs` INTEGER NOT NULL, " +
                        "`endMs` INTEGER NOT NULL, `hashes` BLOB NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }
```

  - in `addMigrations(...)` aggiungere `MIGRATION_15_16` in coda.

- [ ] **Step 6: cascata** — in `VaultRepository.secureDelete`, dentro la transazione, dopo
  `db.tagDao().clearTagsOf(fileId)`:

```kotlin
            db.introDao().clearPrints(fileId)
            db.introDao().clearSegments(fileId)
```

- [ ] **Step 7: eseguire, deve passare.** Atteso: 1 test verde.

- [ ] **Step 8: commit**

```bash
git add app/src/main/kotlin/com/cripta/app/data/db app/src/main/kotlin/com/cripta/app/data/VaultRepository.kt app/src/test/kotlin/com/cripta/app/data/db/IntroMigrationTest.kt
git commit -m "Intro: database tables for fingerprints, segments and rejected samples"
```

---

### Task 5: IntroDetector

**Files:**
- Create: `app/src/main/kotlin/com/cripta/app/media/intro/IntroDetector.kt`
- Test: `app/src/test/kotlin/com/cripta/app/media/intro/IntroDetectorTest.kt`

- [ ] **Step 1: test che falliscono**

```kotlin
package com.cripta.app.media.intro

import com.cripta.app.data.db.MediaSegmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroDetectorTest {
    private fun noise(seconds: Double, seed: Long): FloatArray {
        val r = java.util.Random(seed)
        return FloatArray((seconds * MonoResampler.OUT_RATE).toInt()) { r.nextFloat() * 2 - 1 }
    }

    private fun episode(id: String, coldOpen: Double, seed: Long, intro: FloatArray): Print {
        val pcm = noise(coldOpen, seed) + intro + noise(40.0, seed + 100)
        return Print(id, 0, Fingerprint.hashes(pcm))
    }

    private val intro = noise(20.0, 9)

    @Test
    fun findsTheSameIntroAtDifferentTimes() {
        val a = episode("a", 5.0, 1, intro)
        val b = episode("b", 7.3, 2, intro)
        val found = IntroDetector.detect(SegmentKind.INTRO, a, listOf(b), rejected = emptyList())
        val fa = found.single { it.fileId == "a" }
        val fb = found.single { it.fileId == "b" }
        assertEquals(5_000.0, fa.startMs.toDouble(), 700.0)
        assertEquals(25_000.0, fa.endMs.toDouble(), 700.0)
        assertEquals(7_300.0, fb.startMs.toDouble(), 700.0)
        assertEquals(27_300.0, fb.endMs.toDouble(), 700.0)
    }

    @Test
    fun nothingWithoutSharedAudio() {
        val a = episode("a", 5.0, 1, noise(20.0, 10))
        val b = episode("b", 5.0, 2, noise(20.0, 11))
        assertTrue(IntroDetector.detect(SegmentKind.INTRO, a, listOf(b), emptyList()).isEmpty())
    }

    @Test
    fun rejectedAudioIsIgnored() {
        val a = episode("a", 5.0, 1, intro)
        val b = episode("b", 7.3, 2, intro)
        val sample = a.slice(5_000, 25_000)
        assertTrue(IntroDetector.detect(SegmentKind.INTRO, a, listOf(b), listOf(sample)).isEmpty())
    }

    @Test
    fun searchFindsAMarkedStretch() {
        val a = episode("a", 5.0, 1, intro)
        val b = episode("b", 7.3, 2, intro)
        val f = IntroDetector.search(a.slice(6_000, 22_000), listOf(b)).single()
        assertEquals("b", f.fileId)
        assertEquals(8_300.0, f.startMs.toDouble(), 700.0)
    }

    @Test
    fun storeRules() {
        val f = Found("x", 1_000, 21_000)
        fun seg(source: String, len: Long) = MediaSegmentEntity("x", "intro", 0, len, source)
        assertTrue(IntroDetector.shouldStore(null, f))
        assertTrue(IntroDetector.shouldStore(seg("auto", 10_000), f))
        assertFalse(IntroDetector.shouldStore(seg("auto", 30_000), f))
        assertFalse(IntroDetector.shouldStore(seg("manual", 5_000), f))
        assertFalse(IntroDetector.shouldStore(seg("none", 0), f))
    }

    @Test
    fun manualMarksFollowANewVersionOnlyWithTheSameLength() {
        assertTrue(IntroDetector.carriesManual(1_200_000, 1_201_500))
        assertFalse(IntroDetector.carriesManual(1_200_000, 1_203_000))
        assertFalse(IntroDetector.carriesManual(null, 1_200_000))
    }
}
```

- [ ] **Step 2: eseguire, deve fallire** — `--tests "com.cripta.app.media.intro.IntroDetectorTest"`.

- [ ] **Step 3: implementazione**

```kotlin
package com.cripta.app.media.intro

import com.cripta.app.data.db.MediaSegmentEntity
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Intro or outro, the print part it is searched in and its minimum length (in hashes). */
enum class SegmentKind(val id: String, val part: String, val minHashes: Int) {
    INTRO("intro", "head", (15_000 / Fingerprint.HOP_MS).toInt()),
    OUTRO("outro", "tail", (10_000 / Fingerprint.HOP_MS).toInt());

    companion object {
        fun of(id: String): SegmentKind = entries.first { it.id == id }
    }
}

/** One part of a video's fingerprint, decoded. */
class Print(val fileId: String, val startMs: Long, val hashes: IntArray) {
    fun timeOf(index: Int): Long = startMs + (index * Fingerprint.HOP_MS).roundToLong()
    fun indexOf(ms: Long): Int = ((ms - startMs) / Fingerprint.HOP_MS).roundToInt().coerceIn(0, hashes.size)
    fun slice(fromMs: Long, toMs: Long): IntArray = hashes.copyOfRange(indexOf(fromMs), maxOf(indexOf(fromMs), indexOf(toMs)))
}

/** A segment found in a video. */
data class Found(val fileId: String, val startMs: Long, val endMs: Long)

object IntroDetector {
    /** A new version of a video keeps the old one's manual marks only within this length difference. */
    private const val CARRY_TOLERANCE_MS = 2_000L

    /**
     * Stretches [target] shares with each of [others]: for the target the longest, for each other
     * video the stretch that matched it. Stretches covered by a [rejected] sample are dropped.
     */
    fun detect(kind: SegmentKind, target: Print, others: List<Print>, rejected: List<IntArray>): List<Found> {
        var best: Found? = null
        val found = mutableListOf<Found>()
        for (o in others) {
            if (o.fileId == target.fileId) continue
            val m = SegmentMatcher.best(target.hashes, o.hashes, kind.minHashes) ?: continue
            if (isRejected(target.hashes.copyOfRange(m.aStart, m.aEnd), rejected)) continue
            val t = Found(target.fileId, target.timeOf(m.aStart), target.timeOf(m.aEnd))
            if (best == null || t.endMs - t.startMs > best.endMs - best.startMs) best = t
            found += Found(o.fileId, o.timeOf(m.bStart), o.timeOf(m.bEnd))
        }
        return listOfNotNull(best) + found
    }

    /** Where a stretch the user marked ([sample]) appears in each of [others]. */
    fun search(sample: IntArray, others: List<Print>): List<Found> {
        val min = maxOf((sample.size * 0.8).toInt(), SegmentKind.OUTRO.minHashes / 2)
        return others.mapNotNull { o ->
            SegmentMatcher.best(sample, o.hashes, min)?.let { Found(o.fileId, o.timeOf(it.bStart), o.timeOf(it.bEnd)) }
        }
    }

    /** Whether at least half of [stretch] is audio of one of the [rejected] samples. */
    fun isRejected(stretch: IntArray, rejected: List<IntArray>): Boolean = rejected.any { r ->
        val m = SegmentMatcher.best(stretch, r, 1) ?: return@any false
        m.length * 2 >= stretch.size
    }

    /** Never over the user's own choice ("manual" or "none"); over an automatic one only when longer. */
    fun shouldStore(existing: MediaSegmentEntity?, found: Found): Boolean = when (existing?.source) {
        null -> true
        "auto" -> found.endMs - found.startMs > existing.endMs - existing.startMs
        else -> false
    }

    fun carriesManual(fromDurationMs: Long?, toDurationMs: Long?): Boolean =
        fromDurationMs != null && toDurationMs != null && abs(fromDurationMs - toDurationMs) <= CARRY_TOLERANCE_MS
}
```

- [ ] **Step 4: eseguire, deve passare.** Atteso: 6 test verdi. Se fallisce
  `findsTheSameIntroAtDifferentTimes` (7,3 s non è allineato agli hash), applicare la stessa
  correzione del Task 2 (`HOP = 345`) e rieseguire Task 2, 3 e 5.

- [ ] **Step 5: commit**

```bash
git add app/src/main/kotlin/com/cripta/app/media/intro/IntroDetector.kt app/src/test/kotlin/com/cripta/app/media/intro/IntroDetectorTest.kt
git commit -m "Intro: detection rules, marked-stretch search and rejected samples"
```

---

### Task 6: AudioDecoder

**Files:**
- Create: `app/src/main/kotlin/com/cripta/app/media/ChannelMediaDataSource.kt`
- Modify: `app/src/main/kotlin/com/cripta/app/media/ThumbnailLoader.kt:739-757` (togliere la classe privata)
- Create: `app/src/main/kotlin/com/cripta/app/media/intro/AudioDecoder.kt`

Nessun test JUnit (MediaCodec richiede un dispositivo): verifica in Task 12.

- [ ] **Step 1: estrarre `ChannelMediaDataSource`** — spostare la classe da `ThumbnailLoader.kt`
  (righe 739-757) nel nuovo file, `internal` invece di `private`. Gli usi in `ThumbnailLoader`
  restano invariati (stesso package).

```kotlin
package com.cripta.app.media

import android.media.MediaDataSource
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/** A decrypting channel seen as a [MediaDataSource], for MediaExtractor (no plaintext on disk). */
internal class ChannelMediaDataSource(
    private val channel: SeekableByteChannel,
    private val length: Long,
) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= length) return -1
        channel.position(position)
        val bb = ByteBuffer.wrap(buffer, offset, size)
        var read = 0
        while (bb.hasRemaining()) {
            val n = channel.read(bb)
            if (n <= 0) break
            read += n
        }
        return if (read == 0) -1 else read
    }
    override fun getSize(): Long = length
    override fun close() { runCatching { channel.close() } }
}
```

- [ ] **Step 2: decoder**

```kotlin
package com.cripta.app.media.intro

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FileEntity
import com.cripta.app.media.ChannelMediaDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Decodes a stretch of a video's audio track to mono [MonoResampler.OUT_RATE] Hz, from the encrypted file. */
@Singleton
class AudioDecoder @Inject constructor(private val repo: VaultRepository) {
    /** [samples] start at [startMs] in the video. */
    class Pcm(val startMs: Long, val samples: FloatArray)

    /** Audio of [file] from [fromMs] to [toMs]; null when it has no audio track or nothing decoded. */
    suspend fun decode(file: FileEntity, fromMs: Long, toMs: Long): Pcm? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var channel: SeekableByteChannel? = null
        try {
            channel = repo.seekableChannel(file)
            extractor.setDataSource(ChannelMediaDataSource(channel, file.sizeBytes))
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext null
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            extractor.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            val info = MediaCodec.BufferInfo()
            var resampler: MonoResampler? = null
            var floatPcm = false
            var startUs = -1L
            var inputDone = false
            var buf = FloatArray(0)
            val endUs = toMs * 1000
            while (true) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = extractor.readSampleData(c.getInputBuffer(i)!!, 0)
                        if (n < 0 || extractor.sampleTime > endUs) {
                            c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = c.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = c.outputFormat
                    resampler = MonoResampler(f.getInteger(MediaFormat.KEY_SAMPLE_RATE), f.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    val r = resampler ?: MonoResampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)).also { resampler = it }
                    if (info.size > 0) {
                        if (startUs < 0) startUs = info.presentationTimeUs
                        val out = c.getOutputBuffer(o)!!
                        out.position(info.offset); out.limit(info.offset + info.size)
                        out.order(ByteOrder.nativeOrder())
                        val n = if (floatPcm) info.size / 4 else info.size / 2
                        if (buf.size < n) buf = FloatArray(n)
                        if (floatPcm) out.asFloatBuffer().get(buf, 0, n)
                        else { val sb = out.asShortBuffer(); for (k in 0 until n) buf[k] = sb.get(k) / 32768f }
                        r.push(buf, n)
                    }
                    c.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            val r = resampler
            if (r == null || startUs < 0 || r.size == 0) null else Pcm(startUs / 1000, r.result())
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
            runCatching { channel?.close() }
        }
    }
}
```

- [ ] **Step 3: compilazione** — `GRADLE` con `:app:compileDebugKotlin` al posto del task di test.
  Atteso: BUILD SUCCESSFUL.

- [ ] **Step 4: commit**

```bash
git add app/src/main/kotlin/com/cripta/app/media/ChannelMediaDataSource.kt app/src/main/kotlin/com/cripta/app/media/ThumbnailLoader.kt app/src/main/kotlin/com/cripta/app/media/intro/AudioDecoder.kt
git commit -m "Intro: decode a video's audio from the encrypted file"
```

---

### Task 7: IntroRepository

**Files:**
- Create: `app/src/main/kotlin/com/cripta/app/data/intro/IntroRepository.kt`

Logica già testata in `IntroDetector`; qui solo DB e orchestrazione.

- [ ] **Step 1: implementazione**

```kotlin
package com.cripta.app.data.intro

import androidx.room.withTransaction
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.AudioPrintEntity
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.MediaSegmentEntity
import com.cripta.app.data.db.RejectedPrintEntity
import com.cripta.app.media.intro.AudioDecoder
import com.cripta.app.media.intro.Fingerprint
import com.cripta.app.media.intro.Found
import com.cripta.app.media.intro.IntroDetector
import com.cripta.app.media.intro.Print
import com.cripta.app.media.intro.PrintCodec
import com.cripta.app.media.intro.SegmentKind
import com.cripta.app.security.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Intros and outros: fingerprints the videos, matches them across the whole vault, stores the
 * segments and the user's own marks and rejections.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class IntroRepository @Inject constructor(
    private val session: SessionManager,
    private val repo: VaultRepository,
    private val decoder: AudioDecoder,
) {
    private val db get() = session.requireDb()
    private val dao get() = db.introDao()

    fun segments(fileId: String): Flow<List<MediaSegmentEntity>> =
        session.database.flatMapLatest { d -> if (d == null) emptyFlow() else d.introDao().observeSegments(fileId) }

    fun rejected(): Flow<List<RejectedPrintEntity>> =
        session.database.flatMapLatest { d -> if (d == null) emptyFlow() else d.introDao().observeRejected() }

    /** Live videos never analysed. */
    suspend fun pending(): List<FileEntity> {
        val done = dao.printedIds().toHashSet()
        return repo.allFilesSnapshot().filter { VaultRepository.isVideo(it.mimeType) && it.id !in done }
    }

    /** Fingerprint [file]'s head and tail; a video that cannot be analysed gets a "skip" row. True when analysed. */
    suspend fun fingerprint(file: FileEntity): Boolean {
        val dur = file.durationMs ?: 0L
        val parts = if (dur < MIN_DURATION_MS) null else try {
            val head = decoder.decode(file, 0, minOf(dur, HEAD_MS))
            val tail = decoder.decode(file, maxOf(0L, dur - TAIL_MS), dur)
            if (head == null || tail == null) null else head to tail
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("IntroRepository", "audio not decodable (${e.javaClass.simpleName})")
            null
        }
        if (parts == null) {
            dao.putPrint(AudioPrintEntity(file.id, SegmentKind.INTRO.part, 0, ByteArray(0), "skip"))
            return false
        }
        val (head, tail) = parts
        val headHashes = withContext(Dispatchers.Default) { Fingerprint.hashes(head.samples) }
        val tailHashes = withContext(Dispatchers.Default) { Fingerprint.hashes(tail.samples) }
        db.withTransaction {
            dao.putPrint(AudioPrintEntity(file.id, SegmentKind.INTRO.part, head.startMs, PrintCodec.encode(headHashes), "ok"))
            dao.putPrint(AudioPrintEntity(file.id, SegmentKind.OUTRO.part, tail.startMs, PrintCodec.encode(tailHashes), "ok"))
        }
        return true
    }

    /** Match the videos [ids] (just fingerprinted) against every other video. Returns how many videos got a segment. */
    suspend fun matchNew(ids: Collection<String>): Int = withContext(Dispatchers.Default) {
        val touched = HashSet<String>()
        for (kind in SegmentKind.entries) {
            val prints = livePrints(kind)
            val byId = prints.associateBy { it.fileId }
            val rejected = dao.rejectedOfKind(kind.id).map { PrintCodec.decode(it.hashes) }
            val done = HashSet<String>()
            for (id in ids) {
                coroutineContext.ensureActive()
                val target = byId[id] ?: continue
                // A pair of two new videos is compared once: detect() reports both sides.
                val others = prints.filter { it.fileId != id && it.fileId !in done }
                done += id
                for (f in IntroDetector.detect(kind, target, others, rejected)) if (store(kind, f)) touched += f.fileId
            }
        }
        touched.size
    }

    suspend fun setManual(fileId: String, kind: SegmentKind, startMs: Long, endMs: Long) =
        dao.putSegment(MediaSegmentEntity(fileId, kind.id, startMs, endMs, "manual"))

    /** "Rimuovi" or "Non è un'intro · Solo qui": no segment of [kind] here, kept across scans. */
    suspend fun setNone(fileId: String, kind: SegmentKind) =
        dao.putSegment(MediaSegmentEntity(fileId, kind.id, 0, 0, "none"))

    /** Search [fileId]'s stretch in every other video; null when too short or the video has no fingerprint. */
    suspend fun searchOthers(fileId: String, kind: SegmentKind, startMs: Long, endMs: Long): Int? = withContext(Dispatchers.Default) {
        val sample = printOf(fileId, kind)?.slice(startMs, endMs) ?: return@withContext null
        if (sample.size < SegmentKind.OUTRO.minHashes / 2) return@withContext null
        IntroDetector.search(sample, livePrints(kind).filter { it.fileId != fileId }).count { store(kind, it) }
    }

    /** "Non è un'intro · In tutti i video". Returns how many other videos lost the segment. */
    suspend fun rejectEverywhere(file: FileEntity, kind: SegmentKind, startMs: Long, endMs: Long): Int = withContext(Dispatchers.Default) {
        dao.deleteSegment(file.id, kind.id)
        val sample = printOf(file.id, kind)?.slice(startMs, endMs)?.takeIf { it.isNotEmpty() } ?: return@withContext 0
        dao.addRejected(RejectedPrintEntity(kind = kind.id, originFileId = file.id, originName = file.originalName,
            startMs = startMs, endMs = endMs, hashes = PrintCodec.encode(sample), createdAt = System.currentTimeMillis()))
        var removed = 0
        for (seg in dao.segmentsOfKind(kind.id)) {
            if (seg.source != "auto" || seg.fileId == file.id) continue
            val stretch = printOf(seg.fileId, kind)?.slice(seg.startMs, seg.endMs) ?: continue
            if (IntroDetector.isRejected(stretch, listOf(sample))) { dao.deleteSegment(seg.fileId, kind.id); removed++ }
        }
        removed
    }

    /** Delete a rejected sample and put back at once the segments it was blocking. */
    suspend fun restoreRejected(id: Long): Int = withContext(Dispatchers.Default) {
        val r = dao.rejectedById(id) ?: return@withContext 0
        dao.deleteRejected(id)
        val kind = SegmentKind.of(r.kind)
        var restored = 0
        val origin = repo.fileById(r.originFileId)
        if (origin != null && origin.deletedAt == null && store(kind, Found(origin.id, r.startMs, r.endMs))) restored++
        val sample = PrintCodec.decode(r.hashes)
        restored + IntroDetector.search(sample, livePrints(kind).filter { it.fileId != r.originFileId }).count { store(kind, it) }
    }

    private suspend fun store(kind: SegmentKind, f: Found): Boolean {
        val existing = dao.segmentsOf(f.fileId).firstOrNull { it.kind == kind.id }
        if (!IntroDetector.shouldStore(existing, f)) return false
        dao.putSegment(MediaSegmentEntity(f.fileId, kind.id, f.startMs, f.endMs, "auto"))
        return true
    }

    private suspend fun printOf(fileId: String, kind: SegmentKind): Print? =
        dao.printsOf(fileId).firstOrNull { it.part == kind.part && it.status == "ok" }
            ?.let { Print(it.fileId, it.startMs, PrintCodec.decode(it.hashes)) }

    private suspend fun livePrints(kind: SegmentKind): List<Print> {
        val live = repo.allFilesSnapshot().mapTo(HashSet()) { it.id }
        return dao.allPrints(kind.part).filter { it.fileId in live }.map { Print(it.fileId, it.startMs, PrintCodec.decode(it.hashes)) }
    }

    private companion object {
        const val MIN_DURATION_MS = 120_000L
        const val HEAD_MS = 600_000L
        const val TAIL_MS = 300_000L
    }
}
```

- [ ] **Step 2: compilazione** — `:app:compileDebugKotlin`. Atteso: BUILD SUCCESSFUL.

- [ ] **Step 3: commit**

```bash
git add app/src/main/kotlin/com/cripta/app/data/intro/IntroRepository.kt
git commit -m "Intro: repository for fingerprints, matching, marks and rejections"
```

---

### Task 8: impostazioni e scansione in background

**Files:**
- Modify: `app/src/main/kotlin/com/cripta/app/data/SettingsStore.kt` (data class `Settings`, chiavi, mappatura, setter, `resetPage("VIDEO")`)
- Modify: `app/src/main/kotlin/com/cripta/app/work/ConversionService.kt`

- [ ] **Step 1: impostazioni** — in `Settings`, dopo `controlsTimeoutSec`:

```kotlin
    /** Intro/outro: analyse each new video's audio in the background. */
    val introAnalyzeNew: Boolean = true,
    /** Player: skip a known intro / outro by itself instead of offering the button. */
    val introAutoSkip: Boolean = false,
    val outroAutoSkip: Boolean = false,
```

Chiavi, dopo `quickTagsKey`:

```kotlin
    private val introAnalyzeKey = booleanPreferencesKey("intro_analyze_new")
    private val introAutoKey = booleanPreferencesKey("intro_auto_skip")
    private val outroAutoKey = booleanPreferencesKey("outro_auto_skip")
```

Mappatura, dopo `controlsTimeoutSec = ...`:

```kotlin
            introAnalyzeNew = p[introAnalyzeKey] ?: true,
            introAutoSkip = p[introAutoKey] ?: false,
            outroAutoSkip = p[outroAutoKey] ?: false,
```

Setter, dopo `setControlsTimeout`:

```kotlin
    suspend fun setIntroAnalyzeNew(v: Boolean) { context.dataStore.edit { it[introAnalyzeKey] = v } }
    suspend fun setIntroAutoSkip(v: Boolean) { context.dataStore.edit { it[introAutoKey] = v } }
    suspend fun setOutroAutoSkip(v: Boolean) { context.dataStore.edit { it[outroAutoKey] = v } }
```

In `resetPage`, lista `"VIDEO"`: aggiungere `introAnalyzeKey, introAutoKey, outroAutoKey` in coda.

- [ ] **Step 2: servizio** — in `ConversionService.kt`:
  - iniezione, dopo `updater`: `@Inject lateinit var intro: com.cripta.app.data.intro.IntroRepository`
  - costanti nel companion, dopo `MODE_CANCEL_SCAN`:
    ```kotlin
            private const val MODE_INTRO_SCAN = "intro_scan"
            private const val MODE_CANCEL_INTRO = "cancel_intro"
            private const val EX_MANUAL = "manual"
    ```
  - `ResultKind`: aggiungere `INTRO(4226, R.drawable.ic_notif_scan),` dopo `UPDATE`.
  - in `onStartCommand`, subito dopo il blocco `if (mode == MODE_CANCEL_SCAN) { ... }`:
    ```kotlin
            if (mode == MODE_CANCEL_INTRO) {
                introJob?.cancel()
                stopIfIdle(startId)
                return START_NOT_STICKY
            }
    ```
  - nel `when (mode)` del job, dopo `MODE_DUP_SCAN -> { ... }`:
    ```kotlin
                        MODE_INTRO_SCAN -> if (holdsSession) introScan(intent.getBooleanExtra(EX_MANUAL, false))
    ```
  - nel `finally` del job, prima di `if (holdsSession) session.endWork()`:
    ```kotlin
                    // New videos get their intro/outro analysis right after (when on in Settings).
                    if (mode == MODE_IMPORT || mode == MODE_DOWNLOAD_URL || mode == MODE_CONVERT) withContext(NonCancellable) {
                        if (runCatching { settings.settingsOnce().introAnalyzeNew }.getOrDefault(false)) startIntroScan(this@ConversionService, manual = false)
                    }
    ```
  - dopo `scanDuplicates(...)`:

```kotlin
    @Volatile private var introJob: kotlinx.coroutines.Job? = null
    @Volatile private var introRerun = false
    @Volatile private var introManual = false
    private val introMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Fingerprint every video not analysed yet, then match them against the whole vault. A request
     * arriving while a scan runs is folded into it: the running scan goes round once more.
     * [manual]: started from Settings, so it always reports; an automatic one only when it found something.
     */
    private suspend fun introScan(manual: Boolean) {
        introManual = introManual || manual
        if (!introMutex.tryLock()) { introRerun = true; return }
        introJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
        val label = "Analisi intro e finali"
        var analysed = 0; var skipped = 0; var found = 0
        try {
            do {
                introRerun = false
                val todo = intro.pending()
                if (todo.isEmpty()) break
                val eta = EtaEstimator()
                val newIds = mutableListOf<String>()
                todo.forEachIndexed { i, f ->
                    notify(build(label, i * 100 / todo.size, sub = "Video ${i + 1} di ${todo.size}",
                        eta = if (i > 0) eta.left(i.toFloat() / todo.size) else null,
                        cancelable = true, cancelMode = MODE_CANCEL_INTRO, icon = R.drawable.ic_notif_scan))
                    if (intro.fingerprint(f)) { analysed++; newIds += f.id } else skipped++
                }
                notify(build(label, 100, sub = "Confronto con gli altri video…", indeterminate = true,
                    cancelable = true, cancelMode = MODE_CANCEL_INTRO, icon = R.drawable.ic_notif_scan))
                found += intro.matchNew(newIds)
            } while (introRerun)
            if (introManual || found > 0) notifyResult("Analisi completata", introSummary(analysed, skipped, found), ResultKind.INTRO)
        } catch (e: kotlinx.coroutines.CancellationException) {
            notifyResult("$label annullata", introSummary(analysed, skipped, found), ResultKind.INTRO, state = ResultState.CANCELLED)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ConversionService", "intro scan failed", e)
            notifyResult("$label non riuscita", UserErrors.of(e), ResultKind.INTRO, state = ResultState.FAILED)
        } finally {
            introJob = null
            introManual = false
            introMutex.unlock()
        }
    }

    private fun introSummary(analysed: Int, skipped: Int, found: Int): String =
        "$analysed analizzati" + (if (skipped > 0) ", $skipped saltati" else "") +
            (if (found > 0) " · intro o finali in $found video" else "")
```

  - nel companion, dopo `cancelDupScan`:

```kotlin
        /** Analyse the videos not analysed yet for intros and outros ([manual]: from Settings). */
        fun startIntroScan(ctx: Context, manual: Boolean) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ConversionService::class.java)
                .putExtra(EX_MODE, MODE_INTRO_SCAN).putExtra(EX_MANUAL, manual))
        }

        fun cancelIntroScan(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, ConversionService::class.java).putExtra(EX_MODE, MODE_CANCEL_INTRO)) }
        }
```

  Verificare che `NonCancellable` e `withContext` siano già importati (sono usati a riga ~610);
  altrimenti usare i nomi completi `kotlinx.coroutines.NonCancellable` / `kotlinx.coroutines.withContext`.

- [ ] **Step 3: compilazione** — `:app:compileDebugKotlin`. Atteso: BUILD SUCCESSFUL.

- [ ] **Step 4: commit**

```bash
git add app/src/main/kotlin/com/cripta/app/data/SettingsStore.kt app/src/main/kotlin/com/cripta/app/work/ConversionService.kt
git commit -m "Intro: background analysis of new and existing videos"
```

---

### Task 9: sezione "Intro e finale" nelle impostazioni

**Files:**
- Modify: `app/src/main/kotlin/com/cripta/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/cripta/app/ui/settings/SettingsScreen.kt` (dopo la sezione "Fine video", ~riga 374; indice ricerca ~riga 1175)

- [ ] **Step 1: ViewModel** — nel costruttore aggiungere
  `private val intro: com.cripta.app.data.intro.IntroRepository,` dopo `keyVault`. Dopo `setAutoNextSec`:

```kotlin
    fun setIntroAnalyzeNew(v: Boolean) = viewModelScope.launch { store.setIntroAnalyzeNew(v) }
    fun setIntroAutoSkip(v: Boolean) = viewModelScope.launch { store.setIntroAutoSkip(v) }
    fun setOutroAutoSkip(v: Boolean) = viewModelScope.launch { store.setOutroAutoSkip(v) }
    fun startIntroScan() = com.cripta.app.work.ConversionService.startIntroScan(appContext, manual = true)
    val rejectedSegments: StateFlow<List<com.cripta.app.data.db.RejectedPrintEntity>> =
        intro.rejected().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun restoreRejected(id: Long) = viewModelScope.launch { intro.restoreRejected(id) }
```

- [ ] **Step 2: schermata** — dopo l'`item { Section("Fine video") { ... } }`:

```kotlin
                        item {
                            Section("Intro e finale", "Cripta riconosce sigle e finali confrontando l'audio dei video del vault, in qualunque cartella. " +
                                "Nel video, l'icona ⏭ in alto permette di segnarli a mano o di scartarli.") {
                                ToggleRow("Analizza i nuovi video", s.introAnalyzeNew,
                                    desc = "Dopo ogni importazione, download o conversione, in background.",
                                    icon = Icons.Filled.GraphicEq) { vm.setIntroAnalyzeNew(it) }
                                ToggleRow("Salta l'intro in automatico", s.introAutoSkip,
                                    desc = if (s.introAutoSkip) "Il video salta da solo; «Annulla» torna indietro." else "Compare il pulsante «Salta intro».",
                                    icon = Icons.Filled.SkipNext) { vm.setIntroAutoSkip(it) }
                                ToggleRow("Salta il finale in automatico", s.outroAutoSkip,
                                    desc = "Passa al file successivo, se c'è, altrimenti va alla fine del video.",
                                    icon = Icons.Filled.SkipNext) { vm.setOutroAutoSkip(it) }
                                androidx.compose.material3.FilledTonalButton(onClick = { vm.startIntroScan() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Analizza i video esistenti")
                                }
                                val rejected by vm.rejectedSegments.collectAsState()
                                var showRejected by remember { mutableStateOf(false) }
                                if (rejected.isNotEmpty()) {
                                    androidx.compose.material3.OutlinedButton(onClick = { showRejected = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Segmenti scartati (${rejected.size})")
                                    }
                                }
                                if (showRejected) androidx.compose.material3.AlertDialog(
                                    onDismissRequest = { showRejected = false },
                                    confirmButton = { androidx.compose.material3.TextButton(onClick = { showRejected = false }) { Text("Chiudi") } },
                                    title = { Text("Segmenti scartati") },
                                    text = {
                                        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Questi tratti audio non diventano mai intro o finali. Togline uno per rimettere subito i segmenti che bloccava.",
                                                style = MaterialTheme.typography.bodySmall)
                                            rejected.forEach { r ->
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(r.originName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                        Text((if (r.kind == "intro") "Intro" else "Finale") + " · ${(r.endMs - r.startMs) / 1000} s",
                                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    androidx.compose.material3.TextButton(onClick = { vm.restoreRejected(r.id) }) { Text("Togli") }
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
```

  Import da aggiungere se mancano: `androidx.compose.material.icons.filled.GraphicEq`,
  `androidx.compose.foundation.verticalScroll`, `androidx.compose.runtime.collectAsState`,
  `remember`, `mutableStateOf`, `getValue`, `setValue` (controllare la testa del file).

  Indice di ricerca, dopo la riga `"Prossimo video a fine riproduzione"`:

```kotlin
    Triple("Intro e finale", "salta intro sigla finale titoli coda skip analizza scartati", SettingsPage.VIDEO),
```

- [ ] **Step 3: compilazione** — `:app:compileDebugKotlin`. Atteso: BUILD SUCCESSFUL.

- [ ] **Step 4: commit**

```bash
git add app/src/main/kotlin/com/cripta/app/ui/settings
git commit -m "Settings: intro and outro section"
```

---

### Task 10: SegmentSkipper

**Files:**
- Create: `app/src/main/kotlin/com/cripta/app/ui/viewer/SegmentSkipper.kt`
- Test: `app/src/test/kotlin/com/cripta/app/ui/viewer/SegmentSkipperTest.kt`

- [ ] **Step 1: test che falliscono**

```kotlin
package com.cripta.app.ui.viewer

import com.cripta.app.ui.viewer.SegmentSkipper.Action
import com.cripta.app.ui.viewer.SegmentSkipper.Kind
import com.cripta.app.ui.viewer.SegmentSkipper.Segment
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentSkipperTest {
    private val intro = Segment(Kind.INTRO, 10_000, 40_000)
    private val outro = Segment(Kind.OUTRO, 1_000_000, 1_060_000)
    private val segs = listOf(intro, outro)

    @Test
    fun showsTheButtonInsideASegmentOnly() {
        val s = SegmentSkipper()
        assertEquals(Action.None, s.at(5_000, segs, false, false, false))
        assertEquals(Action.Show(intro), s.at(12_000, segs, false, false, false))
        assertEquals(Action.Show(outro), s.at(1_010_000, segs, false, false, false))
        assertEquals(Action.None, s.at(39_800, segs, false, false, false))
    }

    @Test
    fun dismissHidesForThisViewing() {
        val s = SegmentSkipper()
        s.dismiss(Kind.INTRO)
        assertEquals(Action.None, s.at(12_000, segs, false, false, false))
        assertEquals(Action.Show(outro), s.at(1_010_000, segs, false, false, false))
    }

    @Test
    fun autoSkipsOnceThenShows() {
        val s = SegmentSkipper()
        assertEquals(Action.AutoSkip(intro), s.at(12_000, segs, false, true, false))
        assertEquals(Action.Show(intro), s.at(13_000, segs, false, true, false))
    }

    @Test
    fun undoStopsTheAutoSkip() {
        val s = SegmentSkipper()
        s.undo(Kind.INTRO)
        assertEquals(Action.Show(intro), s.at(12_000, segs, false, true, false))
    }

    @Test
    fun nothingWhileScrubbing() {
        assertEquals(Action.None, SegmentSkipper().at(12_000, segs, true, true, true))
    }
}
```

- [ ] **Step 2: eseguire, deve fallire** — `--tests "com.cripta.app.ui.viewer.SegmentSkipperTest"`.

- [ ] **Step 3: implementazione**

```kotlin
package com.cripta.app.ui.viewer

/**
 * What the player does about a known intro or outro at a playback position: show "Salta intro" /
 * "Salta finale", or skip by itself once when the user turned that on. The button's X hides it for
 * the rest of this viewing; "Annulla" after an automatic skip stops it auto-skipping again.
 */
class SegmentSkipper {
    enum class Kind { INTRO, OUTRO }

    data class Segment(val kind: Kind, val startMs: Long, val endMs: Long)

    sealed interface Action {
        data object None : Action
        data class Show(val segment: Segment) : Action
        data class AutoSkip(val segment: Segment) : Action
    }

    private val dismissed = HashSet<Kind>()
    private val undone = HashSet<Kind>()
    private val autoDone = HashSet<Kind>()

    fun at(positionMs: Long, segments: List<Segment>, scrubbing: Boolean, autoIntro: Boolean, autoOutro: Boolean): Action {
        if (scrubbing) return Action.None
        val s = segments.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs - END_SLACK_MS } ?: return Action.None
        val auto = if (s.kind == Kind.INTRO) autoIntro else autoOutro
        if (auto && s.kind !in undone && s.kind !in autoDone) {
            autoDone += s.kind
            return Action.AutoSkip(s)
        }
        return if (s.kind in dismissed) Action.None else Action.Show(s)
    }

    fun dismiss(kind: Kind) { dismissed += kind }
    fun undo(kind: Kind) { undone += kind }

    companion object {
        /** No button in a segment's last half second: skipping there gains nothing. */
        const val END_SLACK_MS = 500L
    }
}
```

- [ ] **Step 4: eseguire, deve passare.** Atteso: 5 test verdi.

- [ ] **Step 5: commit**

```bash
git add app/src/main/kotlin/com/cripta/app/ui/viewer/SegmentSkipper.kt app/src/test/kotlin/com/cripta/app/ui/viewer/SegmentSkipperTest.kt
git commit -m "Player: skip logic for intros and outros"
```

---

### Task 11: integrazione nel player

**Files:**
- Modify: `app/src/main/kotlin/com/cripta/app/ui/viewer/ViewerViewModel.kt`
- Create: `app/src/main/kotlin/com/cripta/app/ui/viewer/IntroOutroSheet.kt`
- Modify: `app/src/main/kotlin/com/cripta/app/ui/viewer/ViewerScreen.kt` (`VideoPlayer`)

- [ ] **Step 1: ViewModel** — costruttore: aggiungere `private val intro: com.cripta.app.data.intro.IntroRepository,`
  dopo `settingsStore`. In `PlaybackPrefs`, dopo `splitDetails`:

```kotlin
        val introAutoSkip: Boolean = false,
        val outroAutoSkip: Boolean = false,
```

In `toPlayback()`, dopo `splitDetails = splitDetails,`:
`introAutoSkip = introAutoSkip, outroAutoSkip = outroAutoSkip,`

Dopo `playbackPrefs()`:

```kotlin
    fun segments(fileId: String) = intro.segments(fileId)

    /**
     * "Inizio qui": a segment with its end still after [posMs] takes the new start at once; otherwise
     * the start waits for "Fine qui" (returns true).
     */
    fun markStart(file: FileEntity, kind: com.cripta.app.media.intro.SegmentKind, posMs: Long,
                  current: com.cripta.app.data.db.MediaSegmentEntity?): Boolean {
        if (current != null && current.source != "none" && current.endMs > posMs + MIN_MARK_MS) {
            viewModelScope.launch { intro.setManual(file.id, kind, posMs, current.endMs) }
            return false
        }
        return true
    }

    /** "Fine qui". False when the end would not come after the start. */
    fun markEnd(file: FileEntity, kind: com.cripta.app.media.intro.SegmentKind, startMs: Long, posMs: Long): Boolean {
        if (posMs < startMs + MIN_MARK_MS) { _message.value = "La fine deve venire dopo l'inizio"; return false }
        viewModelScope.launch { intro.setManual(file.id, kind, startMs, posMs) }
        return true
    }

    fun removeSegment(file: FileEntity, kind: com.cripta.app.media.intro.SegmentKind) =
        viewModelScope.launch { intro.setNone(file.id, kind) }

    fun searchSegment(file: FileEntity, kind: com.cripta.app.media.intro.SegmentKind, seg: com.cripta.app.data.db.MediaSegmentEntity) =
        viewModelScope.launch {
            val n = intro.searchOthers(file.id, kind, seg.startMs, seg.endMs)
            _message.value = when {
                n == null -> "Tratto troppo corto, o video non ancora analizzato"
                n == 0 -> "Non trovato in altri video"
                else -> "Trovato in $n video"
            }
        }

    fun rejectSegment(file: FileEntity, kind: com.cripta.app.media.intro.SegmentKind,
                      seg: com.cripta.app.data.db.MediaSegmentEntity, everywhere: Boolean) =
        viewModelScope.launch {
            if (!everywhere) { intro.setNone(file.id, kind); return@launch }
            val n = intro.rejectEverywhere(file, kind, seg.startMs, seg.endMs)
            _message.value = if (n > 0) "Scartato qui e in altri $n video" else "Scartato in tutti i video"
        }

    private companion object {
        /** Shortest intro/outro a mark can make. */
        const val MIN_MARK_MS = 1_000L
    }
```

  Se `ViewerViewModel` ha già un `companion object`, aggiungere `MIN_MARK_MS` lì.

- [ ] **Step 2: sheet**

```kotlin
package com.cripta.app.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cripta.app.data.db.MediaSegmentEntity
import com.cripta.app.media.intro.SegmentKind
import com.cripta.app.ui.components.ChipFlowRow

/**
 * "Intro e finale": what the player knows about this video's intro and outro, the manual marks
 * (from the player position when tapped) and the rejection of a wrong detection.
 */
@Composable
internal fun IntroOutroSheet(
    segments: List<MediaSegmentEntity>,
    pendingStart: Map<SegmentKind, Long>,
    onStartHere: (SegmentKind, MediaSegmentEntity?) -> Unit,
    onEndHere: (SegmentKind, MediaSegmentEntity?) -> Unit,
    onRemove: (SegmentKind) -> Unit,
    onSearch: (SegmentKind, MediaSegmentEntity) -> Unit,
    onReject: (SegmentKind, MediaSegmentEntity, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var rejecting by remember { mutableStateOf<Pair<SegmentKind, MediaSegmentEntity>?>(null) }
    com.cripta.app.ui.components.CriptaSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Intro e finale", style = MaterialTheme.typography.titleLarge)
            for (kind in SegmentKind.entries) {
                val seg = segments.firstOrNull { it.kind == kind.id }
                val name = if (kind == SegmentKind.INTRO) "Intro" else "Finale"
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    val pending = pendingStart[kind]
                    Text(
                        when {
                            pending != null -> "Inizio segnato a ${clock(pending)}: ora vai alla fine e tocca «Fine qui»."
                            seg == null -> "Non rilevato."
                            seg.source == "none" -> "Nessuno (scelta tua)."
                            else -> "${clock(seg.startMs)}–${clock(seg.endMs)} · " + if (seg.source == "auto") "rilevato" else "segnato da te"
                        },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChipFlowRow(horizontalGap = 8.dp, verticalGap = 4.dp) {
                        OutlinedButton(onClick = { onStartHere(kind, seg) }) { Text("Inizio qui") }
                        OutlinedButton(onClick = { onEndHere(kind, seg) }) { Text("Fine qui") }
                        if (seg != null && seg.source != "none") {
                            TextButton(onClick = { onSearch(kind, seg) }) { Text("Cerca negli altri video") }
                            if (seg.source == "auto") TextButton(onClick = { rejecting = kind to seg }) {
                                Text(if (kind == SegmentKind.INTRO) "Non è un'intro" else "Non è un finale")
                            } else TextButton(onClick = { onRemove(kind) }) { Text("Rimuovi") }
                        }
                    }
                }
            }
        }
    }
    rejecting?.let { (kind, seg) ->
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text(if (kind == SegmentKind.INTRO) "Non è un'intro" else "Non è un finale") },
            text = { Text("Scartare questo tratto solo in questo video, o in tutti i video in cui compare (anche in quelli futuri)?") },
            confirmButton = { TextButton(onClick = { onReject(kind, seg, true); rejecting = null }) { Text("In tutti i video") } },
            dismissButton = { TextButton(onClick = { onReject(kind, seg, false); rejecting = null }) { Text("Solo qui") } },
        )
    }
}

private fun clock(ms: Long): String = com.cripta.app.ui.components.formatDuration(ms) ?: "0:00"
```

  Prima di scrivere, controllare la firma di `ChipFlowRow` in
  `ui/components/ChipFlowRow.kt` e di `formatDuration` in `ui/components/MediaComponents.kt`:
  se diverse, usare i loro parametri reali (o `androidx.compose.foundation.layout.FlowRow`).

- [ ] **Step 3: player** — in `VideoPlayer` (`ViewerScreen.kt`):

  (a) dopo `val nextThumb by produceState...` (~riga 1292):

```kotlin
    // Intro / outro: "Salta intro" / "Salta finale", or the automatic skip with its "Annulla".
    val segmentRows by remember(file.id) { vm.segments(file.id) }.collectAsState(initial = emptyList())
    val skipSegments = remember(segmentRows) {
        segmentRows.filter { it.source != "none" && it.endMs > it.startMs }.map {
            SegmentSkipper.Segment(if (it.kind == "intro") SegmentSkipper.Kind.INTRO else SegmentSkipper.Kind.OUTRO, it.startMs, it.endMs)
        }
    }
    val skipper = remember(file.id) { SegmentSkipper() }
    var skipAction by remember(file.id) { mutableStateOf<SegmentSkipper.Action>(SegmentSkipper.Action.None) }
    var skippedChip by remember(file.id) { mutableStateOf<SegmentSkipper.Segment?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    var introSheet by remember { mutableStateOf(false) }
    val introPending = remember(file.id) { androidx.compose.runtime.mutableStateMapOf<com.cripta.app.media.intro.SegmentKind, Long>() }
    fun skipSegment(s: SegmentSkipper.Segment) {
        if (s.kind == SegmentSkipper.Kind.OUTRO && nextId != null && !prefs.loop) latestOnNext()
        else player.seekTo(if (s.kind == SegmentSkipper.Kind.OUTRO && player.duration > 0) player.duration else s.endMs)
    }
    LaunchedEffect(file.id, skipSegments, prefs.introAutoSkip, prefs.outroAutoSkip, inPip) {
        while (true) {
            val a = if (inPip) SegmentSkipper.Action.None
                else skipper.at(player.currentPosition, skipSegments, scrubbing, prefs.introAutoSkip, prefs.outroAutoSkip)
            if (a is SegmentSkipper.Action.AutoSkip) { skipSegment(a.segment); skippedChip = a.segment }
            skipAction = a
            delay(250)
        }
    }
    LaunchedEffect(skippedChip) { if (skippedChip != null) { delay(3_000); skippedChip = null } }
```

  (b) nel `factory` dell'`AndroidView`, prima di `playerViewRef = this`:

```kotlin
                    // Dragging the seek bar is never an intro/outro skip moment (see SegmentSkipper).
                    (findViewById<View>(androidx.media3.ui.R.id.exo_progress) as? androidx.media3.ui.TimeBar)
                        ?.addListener(object : androidx.media3.ui.TimeBar.OnScrubListener {
                            override fun onScrubStart(timeBar: androidx.media3.ui.TimeBar, position: Long) { scrubbing = true }
                            override fun onScrubMove(timeBar: androidx.media3.ui.TimeBar, position: Long) {}
                            override fun onScrubStop(timeBar: androidx.media3.ui.TimeBar, position: Long, canceled: Boolean) { scrubbing = false }
                        })
```

  (c) card "Prossimo": sostituire

```kotlin
        val rem = remainingMs
        val window = minOf(prefs.autoNextSec * 1000L, totalMs / 3)
        if (autoNextOn && !nextCancelled && rem != null && window > 0 && rem <= window) {
```

  con

```kotlin
        val rem = remainingMs
        val window = minOf(prefs.autoNextSec * 1000L, totalMs / 3)
        val nextCardShown = autoNextOn && !nextCancelled && rem != null && window > 0 && rem <= window
        // Bottom-right cards ("Prossimo", "Salta intro"): right above the filmstrip while the controls show in portrait.
        val cardBottom = if (controlsVisible && !vLandscape && bottomInset > 0.dp) seekClearHere + 6.dp + bottomInset + 8.dp
            else maxOf(seekBarClearance, seekClearHere) + 8.dp
        if (nextCardShown) {
```

  e nel `padding(...)` della card `bottom = if (...) ... else ...` con `bottom = cardBottom`.

  (d) subito dopo la chiusura del blocco `if (nextCardShown) { ... }`:

```kotlin
        // "Salta intro" / "Salta finale"; the outro button gives way to the "Prossimo" card.
        val skipSeg = (skipAction as? SegmentSkipper.Action.Show)?.segment
        if (skipSeg != null && skippedChip == null && !(nextCardShown && skipSeg.kind == SegmentSkipper.Kind.OUTRO)) {
            Surface(
                onClick = { skipSegment(skipSeg) },
                color = Color.Black.copy(alpha = 0.78f), shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.BottomEnd).then(holdStill).padding(end = 12.dp + sideCut.end, bottom = cardBottom),
            ) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(if (skipSeg.kind == SegmentSkipper.Kind.INTRO) "Salta intro" else "Salta finale",
                        color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                    IconButton(onClick = { skipper.dismiss(skipSeg.kind); skipAction = SegmentSkipper.Action.None }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, "Nascondi", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        skippedChip?.let { s ->
            Surface(color = Color.Black.copy(alpha = 0.78f), shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.BottomEnd).then(holdStill).padding(end = 12.dp + sideCut.end, bottom = cardBottom)) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (s.kind == SegmentSkipper.Kind.INTRO) "Intro saltata" else "Finale saltato",
                        color = Color.White, style = MaterialTheme.typography.labelLarge)
                    androidx.compose.material3.TextButton(onClick = { skipper.undo(s.kind); player.seekTo(s.startMs); skippedChip = null }) {
                        Text("Annulla")
                    }
                }
            }
        }
```

  (e) in `sideButtons`, prima del blocco Picture-in-Picture:

```kotlin
                // Intro and outro: marks, search in other videos, rejection.
                Surface(color = Color.Black.copy(alpha = 0.45f), shape = CircleShape) {
                    IconButton(onClick = { introSheet = true }) {
                        Icon(Icons.Filled.SkipNext, "Intro e finale", tint = Color.White)
                    }
                }
```

  (f) in fondo al `Box` principale di `VideoPlayer`, dopo lo spinner di buffering:

```kotlin
        if (introSheet) IntroOutroSheet(
            segments = segmentRows,
            pendingStart = introPending,
            onStartHere = { kind, seg ->
                val pos = player.currentPosition
                if (vm.markStart(file, kind, pos, seg)) introPending[kind] = pos else introPending.remove(kind)
            },
            onEndHere = { kind, seg ->
                val start = introPending[kind] ?: seg?.takeIf { it.source != "none" }?.startMs
                if (start == null) modeLabel = "Segna prima l'inizio"
                else if (vm.markEnd(file, kind, start, player.currentPosition)) introPending.remove(kind)
            },
            onRemove = { kind -> vm.removeSegment(file, kind) },
            onSearch = { kind, seg -> vm.searchSegment(file, kind, seg) },
            onReject = { kind, seg, everywhere -> vm.rejectSegment(file, kind, seg, everywhere) },
            onDismiss = { introSheet = false },
        )
```

  Import: `androidx.compose.material.icons.filled.SkipNext` (se assente). `modeLabel` esiste già
  (etichetta breve al centro in alto); se `modeTick` serve per riavviarla, aggiungere `modeTick++`.

- [ ] **Step 4: compilazione e test viewer** — `:app:testDebugUnitTest --tests "com.cripta.app.ui.viewer.*"`.
  Atteso: BUILD SUCCESSFUL, test viewer verdi.

- [ ] **Step 5: commit**

```bash
git add app/src/main/kotlin/com/cripta/app/ui/viewer
git commit -m "Player: skip intros and outros, mark and reject them"
```

---

### Task 12: nuove versioni, backup, README, verifica

**Files:**
- Modify: `app/src/main/kotlin/com/cripta/app/data/VaultRepository.kt` (`applyNewVersion` ~580, `importConvertedMp4` ~684, export ~1421, import ~1633)
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-10-29-intro-outro-skip-design.md` (riga sugli scarti)

- [ ] **Step 1: segni manuali sulla nuova versione** — in `VaultRepository`, accanto a `liveFolder`:

```kotlin
    /** A new version of a video takes the old one's own intro/outro marks while the timing still fits. */
    private suspend fun carryManualSegments(from: FileEntity, to: FileEntity) {
        if (!com.cripta.app.media.intro.IntroDetector.carriesManual(from.durationMs, to.durationMs)) return
        db.introDao().segmentsOf(from.id).filter { it.source != "auto" }
            .forEach { db.introDao().putSegment(it.copy(fileId = to.id)) }
    }
```

  In `applyNewVersion`, dentro `db.withTransaction { ... }` del ramo con un solo file precedente,
  dopo `db.fileDao().setWeight(...)`: `carryManualSegments(old, file)`.
  In `importConvertedMp4`, dentro la transazione, dopo il ciclo delle etichette:
  `carryManualSegments(original, entity)`.

- [ ] **Step 2: backup** — export, nel `JSONObject` di ogni file dopo `.put("tags", ...)`
  (spostare la `)` di chiusura):

```kotlin
                        .put("segments", org.json.JSONArray().apply {
                            db.introDao().segmentsOf(fwt.file.id).forEach {
                                put(org.json.JSONObject().put("kind", it.kind).put("start", it.startMs).put("end", it.endMs).put("source", it.source))
                            }
                        })
```

  Import, dopo il ciclo che collega le etichette (`db.tagDao().link(...)`):

```kotlin
                    // v3: intro/outro segments (absent in older backups).
                    o.optJSONArray("segments")?.let { arr ->
                        for (k in 0 until arr.length()) {
                            val s = arr.getJSONObject(k)
                            db.introDao().putSegment(com.cripta.app.data.db.MediaSegmentEntity(
                                uuid, s.getString("kind"), s.getLong("start"), s.getLong("end"), s.getString("source")))
                        }
                    }
```

- [ ] **Step 3: README** — nell'elenco funzioni, dopo la riga "Conversione video in MP4 scorribile":

```markdown
- ⏭️ Intro e finali riconosciuti confrontando l'audio dei video del vault (ovunque si trovino): pulsante «Salta intro» / «Salta finale» o skip automatico; segno manuale, ricerca negli altri video e scarto dei falsi positivi dal player
```

- [ ] **Step 4: spec** — nella sezione "Scarto di un falso positivo", sostituire
  "Eliminarne uno lo riattiva alla scansione successiva." con
  "Eliminarne uno rimette subito i segmenti che bloccava (ricerca del tratto in tutti i video)."

- [ ] **Step 5: tutti i test** — `GRADLE` con `:app:testDebugUnitTest` (senza `--tests`) e poi
  `:app:assembleDebug`. Atteso: tutti i test verdi, APK generato.

- [ ] **Step 6: commit**

```bash
git add app/src/main/kotlin/com/cripta/app/data/VaultRepository.kt README.md docs/superpowers/specs/2026-10-29-intro-outro-skip-design.md
git commit -m "Intro: manual marks follow new versions, segments in backups"
```

- [ ] **Step 7: prova reale (con l'utente)** — push su `main` solo con il suo via libera (genera la
  release). Sull'APK di CI:
  1. importare 3–4 episodi di una serie con la stessa sigla, in cartelle diverse o nella radice;
  2. attendere la notifica "Analisi completata" (o lanciare "Analizza i video esistenti");
  3. aprire un episodio: "Salta intro" compare sulla sigla e salta alla fine giusta;
  4. attivare lo skip automatico: salta da solo, "Annulla" torna indietro;
  5. sheet: segnare a mano un finale e "Cerca negli altri video";
  6. "Non è un'intro" → "In tutti i video": sparisce anche negli altri episodi; Impostazioni →
     "Segmenti scartati" → "Togli" lo rimette;
  7. un video singolo senza corrispondenze: nessun pulsante, segno manuale funzionante.
