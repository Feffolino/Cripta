# Crypto Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the encryption engine for Cripta — a testable crypto core that encrypts/decrypts files with per-file keys, supports seekable video decryption, and crypto-shreds files — with the master key protected by a hardware-backed KEK.

**Architecture:** Google Tink provides all data-plane crypto. A `StreamingAead` keyset per file encrypts the body and gives a seekable decrypting channel (no plaintext temp for video). A single Tink AEAD keyset is the DEK; per-file keysets are serialized and wrapped by the DEK. The DEK keyset is wrapped by a `KekProvider`. The KEK is abstracted behind an interface so the whole engine is unit-testable on the JVM with an in-memory fake, while the real Android implementation uses an Android Keystore AES-GCM key gated by BiometricPrompt. Crypto-shredding = destroying a file's wrapped keyset.

**Tech Stack:** Kotlin, Gradle (Android + JVM unit tests), Google Tink (`tink-android`), JUnit4, Android Keystore, AndroidX Biometric (device impl only). Min SDK 31.

---

## File Structure

- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` — project + version catalog.
- `app/` — Android application module (thin shell for now; hosts `crypto`).
- `app/src/main/AndroidManifest.xml` — no INTERNET permission, `allowBackup=false`.
- `crypto/` — Android library module, the encryption engine.
  - `crypto/src/main/kotlin/com/cripta/crypto/TinkInit.kt` — one-time Tink registration.
  - `crypto/src/main/kotlin/com/cripta/crypto/KekProvider.kt` — KEK wrap/unwrap interface.
  - `crypto/src/main/kotlin/com/cripta/crypto/InMemoryKekProvider.kt` — JVM/test KEK (JCA AES-GCM).
  - `crypto/src/main/kotlin/com/cripta/crypto/AndroidKeystoreKekProvider.kt` — device KEK (Keystore + biometric Cipher).
  - `crypto/src/main/kotlin/com/cripta/crypto/DekManager.kt` — create/load/wrap the DEK keyset.
  - `crypto/src/main/kotlin/com/cripta/crypto/FileCrypto.kt` — per-file keyset create/wrap/unwrap, encrypt/decrypt/seek, shred.
  - `crypto/src/main/kotlin/com/cripta/crypto/SeekableInputByteChannel.kt` — `SeekableByteChannel` over a file for decryption.
- `crypto/src/test/kotlin/com/cripta/crypto/` — JVM unit tests (run without a device).

All crypto data types use `ByteArray` for key material and `File`/streams for bodies. Associated data (AAD) binds each file's ciphertext to its `blobUuid` string.

---

## Prerequisites

The executing environment needs a JDK 17 and the Android SDK (for the `crypto`/`app` Android library plugins). JVM unit tests in `crypto/src/test` run via `./gradlew :crypto:testDebugUnitTest` and do NOT require a device or emulator. Android Keystore code (`AndroidKeystoreKekProvider`) cannot run on the JVM — it is covered by an instrumented test stub, not unit tests.

---

### Task 1: Project scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `crypto/build.gradle.kts`
- Create: `crypto/src/main/AndroidManifest.xml`
- Create: `.gitignore`

- [ ] **Step 1: Create `.gitignore`**

```gitignore
.gradle/
build/
local.properties
*.iml
.idea/
.DS_Store
captures/
.externalNativeBuild/
.cxx/
```

- [ ] **Step 2: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.5.2"
kotlin = "2.0.20"
tink = "1.14.1"
junit = "4.13.2"
biometric = "1.1.0"
coreKtx = "1.13.1"
androidxTestExt = "1.2.1"

[libraries]
tink-android = { module = "com.google.crypto.tink:tink-android", version.ref = "tink" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-biometric = { module = "androidx.biometric:biometric", version.ref = "biometric" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExt" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

- [ ] **Step 3: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Cripta"
include(":app", ":crypto")
```

- [ ] **Step 4: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 5: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}
```

- [ ] **Step 6: Create `crypto/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cripta.crypto"
    compileSdk = 34

    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.tink.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.biometric)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
```

- [ ] **Step 7: Create `crypto/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 8: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cripta.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cripta.app"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":crypto"))
    implementation(libs.androidx.core.ktx)
}
```

- [ ] **Step 9: Create `app/src/main/AndroidManifest.xml`** (no INTERNET permission, backup disabled)

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="false"
        android:fullBackupContent="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:label="Cripta"
        android:supportsRtl="true">
    </application>
</manifest>
```

- [ ] **Step 10: Create `app/src/main/res/xml/data_extraction_rules.xml`** (exclude everything from cloud/D2D backup)

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" />
        <exclude domain="file" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" />
        <exclude domain="file" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
    </device-transfer>
</data-extraction-rules>
```

- [ ] **Step 11: Verify the project configures**

Run: `./gradlew :crypto:tasks --offline || ./gradlew :crypto:tasks`
Expected: Gradle resolves plugins and lists tasks for `:crypto` with no configuration error. (First run downloads dependencies; drop `--offline`.)

- [ ] **Step 12: Commit**

```bash
git add settings.gradle.kts gradle.properties build.gradle.kts gradle/ app/ crypto/ .gitignore
git commit -m "chore: scaffold Cripta Gradle project with app and crypto modules"
```

---

### Task 2: Tink initialization

**Files:**
- Create: `crypto/src/main/kotlin/com/cripta/crypto/TinkInit.kt`
- Test: `crypto/src/test/kotlin/com/cripta/crypto/TinkInitTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cripta.crypto

import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.StreamingAeadKeyTemplates
import org.junit.Assert.assertNotNull
import org.junit.Test

class TinkInitTest {
    @Test
    fun ensureInitialized_allowsStreamingAeadPrimitive() {
        TinkInit.ensureInitialized()
        val handle = KeysetHandle.generateNew(StreamingAeadKeyTemplates.AES256_GCM_HKDF_1MB)
        val primitive = handle.getPrimitive(StreamingAead::class.java)
        assertNotNull(primitive)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :crypto:testDebugUnitTest --tests "com.cripta.crypto.TinkInitTest"`
Expected: FAIL — `TinkInit` unresolved reference.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.cripta.crypto

import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.util.concurrent.atomic.AtomicBoolean

/** Registers Tink primitives exactly once per process. */
object TinkInit {
    private val done = AtomicBoolean(false)

    fun ensureInitialized() {
        if (done.compareAndSet(false, true)) {
            AeadConfig.register()
            StreamingAeadConfig.register()
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :crypto:testDebugUnitTest --tests "com.cripta.crypto.TinkInitTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add crypto/src/main/kotlin/com/cripta/crypto/TinkInit.kt crypto/src/test/kotlin/com/cripta/crypto/TinkInitTest.kt
git commit -m "feat(crypto): one-time Tink registration"
```

---

### Task 3: KekProvider interface + in-memory implementation

**Files:**
- Create: `crypto/src/main/kotlin/com/cripta/crypto/KekProvider.kt`
- Create: `crypto/src/main/kotlin/com/cripta/crypto/InMemoryKekProvider.kt`
- Test: `crypto/src/test/kotlin/com/cripta/crypto/InMemoryKekProviderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cripta.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InMemoryKekProviderTest {
    @Test
    fun wrapThenUnwrap_returnsOriginalPlaintext() {
        val kek = InMemoryKekProvider.generate()
        val secret = "master-key-material".toByteArray()
        val wrapped = kek.wrap(secret)
        assertFalse(wrapped.contentEquals(secret))
        val unwrapped = kek.unwrap(wrapped)
        assertArrayEquals(secret, unwrapped)
    }

    @Test(expected = Exception::class)
    fun unwrap_withWrongKey_fails() {
        val wrapped = InMemoryKekProvider.generate().wrap("x".toByteArray())
        InMemoryKekProvider.generate().unwrap(wrapped)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :crypto:testDebugUnitTest --tests "com.cripta.crypto.InMemoryKekProviderTest"`
Expected: FAIL — `KekProvider` / `InMemoryKekProvider` unresolved.

- [ ] **Step 3: Write the interface**

```kotlin
package com.cripta.crypto

/**
 * Wraps/unwraps small key material (the serialized DEK keyset).
 * Real impl is Android Keystore + biometric; test impl is in-memory.
 */
interface KekProvider {
    fun wrap(plaintext: ByteArray): ByteArray
    fun unwrap(ciphertext: ByteArray): ByteArray
}
```

- [ ] **Step 4: Write the in-memory implementation**

```kotlin
package com.cripta.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** JVM-only KEK for unit tests: AES-256-GCM with a random in-memory key. */
class InMemoryKekProvider private constructor(private val key: SecretKey) : KekProvider {

    override fun wrap(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext)
        return iv + ct
    }

    override fun unwrap(ciphertext: ByteArray): ByteArray {
        val iv = ciphertext.copyOfRange(0, IV_LEN)
        val ct = ciphertext.copyOfRange(IV_LEN, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    companion object {
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_LEN = 12
        private const val TAG_BITS = 128

        fun generate(): InMemoryKekProvider {
            val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
            return InMemoryKekProvider(SecretKeySpec(raw, "AES"))
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :crypto:testDebugUnitTest --tests "com.cripta.crypto.InMemoryKekProviderTest"`
Expected: PASS (both tests).

- [ ] **Step 6: Commit**

```bash
git add crypto/src/main/kotlin/com/cripta/crypto/KekProvider.kt crypto/src/main/kotlin/com/cripta/crypto/InMemoryKekProvider.kt crypto/src/test/kotlin/com/cripta/crypto/InMemoryKekProviderTest.kt
git commit -m "feat(crypto): KekProvider interface with in-memory test implementation"
```

---

### Task 4: DekManager — create, wrap, and load the DEK keyset

**Files:**
- Create: `crypto/src/main/kotlin/com/cripta/crypto/DekManager.kt`
- Test: `crypto/src/test/kotlin/com/cripta/crypto/DekManagerTest.kt`

The DEK is a Tink AEAD keyset used to wrap per-file keysets. It is serialized to bytes and those bytes are wrapped by the `KekProvider`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cripta.crypto

import com.google.crypto.tink.Aead
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class DekManagerTest {
    private lateinit var kek: KekProvider

    @Before fun setUp() {
        TinkInit.ensureInitialized()
        kek = InMemoryKekProvider.generate()
    }

    @Test
    fun createWrappedDek_thenLoad_yieldsUsableAead() {
        val wrapped = DekManager.createWrappedDek(kek)
        val aead: Aead = DekManager.loadDekAead(wrapped, kek)
        assertNotNull(aead)
        val msg = "hello".toByteArray()
        val ct = aead.encrypt(msg, null)
        assertArrayEquals(msg, aead.decrypt(ct, null))
    }

    @Test
    fun sameWrappedDek_decryptsCiphertextFromAnotherLoad() {
        val wrapped = DekManager.createWrappedDek(kek)
        val ct = DekManager.loadDekAead(wrapped, kek).encrypt("x".toByteArray(), null)
        val plain = DekManager.loadDekAead(wrapped, kek).decrypt(ct, null)
        assertArrayEquals("x".toByteArray(), plain)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :crypto:testDebugUnitTest --tests "com.cripta.crypto.DekManagerTest"`
Expected: FAIL — `DekManager` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.cripta.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadKeyTemplates
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Manages the Data Encryption Key: a Tink AEAD keyset serialized and wrapped by the KEK.
 * The DEK in turn wraps every per-file keyset.
 */
object DekManager {

    /** Generate a new DEK keyset and return it wrapped by the KEK. Call once at setup. */
    fun createWrappedDek(kek: KekProvider): ByteArray {
        TinkInit.ensureInitialized()
        val handle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
        val serialized = ByteArrayOutputStream().use { out ->
            CleartextKeysetHandle.write(handle, BinaryKeysetWriter.withOutputStream(out))
            out.toByteArray()
        }
        return kek.wrap(serialized)
    }

    /** Unwrap the stored DEK and return its Aead primitive. Call after unlock. */
    fun loadDekAead(wrappedDek: ByteArray, kek: KekProvider): Aead {
        TinkInit.ensureInitialized()
        val serialized = kek.unwrap(wrappedDek)
        val handle = CleartextKeysetHandle.read(
            BinaryKeysetReader.withInputStream(ByteArrayInputStream(serialized))
        )
        return handle.getPrimitive(Aead::class.java)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :crypto:testDebugUnitTest --tests "com.cripta.crypto.DekManagerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add crypto/src/main/kotlin/com/cripta/crypto/DekManager.kt crypto/src/test/kotlin/com/cripta/crypto/DekManagerTest.kt
git commit -m "feat(crypto): DEK keyset creation, wrapping, and loading"
```

---

### Task 5: SeekableInputByteChannel

**Files:**
- Create: `crypto/src/main/kotlin/com/cripta/crypto/SeekableInputByteChannel.kt`
- Test: `crypto/src/test/kotlin/com/cripta/crypto/SeekableInputByteChannelTest.kt`

Tink's `newSeekableDecryptingChannel` needs a `SeekableByteChannel` over the ciphertext. A read-only channel over a `File` (via `RandomAccessFile`) provides it.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cripta.crypto

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer

class SeekableInputByteChannelTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun readsFromPosition() {
        val f = tmp.newFile("data.bin")
        f.writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
        SeekableInputByteChannel(f).use { ch ->
            assertEquals(10L, ch.size())
            ch.position(4L)
            val buf = ByteBuffer.allocate(3)
            val n = ch.read(buf)
            assertEquals(3, n)
            assertEquals(4.toByte(), buf.get(0))
            assertEquals(6.toByte(), buf.get(2))
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :crypto:testDebugUnitTest --tests "com.cripta.crypto.SeekableInputByteChannelTest"`
Expected: FAIL — `SeekableInputByteChannel` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.cripta.crypto

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/** Read-only SeekableByteChannel over a file, for Tink's seekable decrypting channel. */
class SeekableInputByteChannel(file: File) : SeekableByteChannel {
    private val raf = RandomAccessFile(file, "r")
    private var open = true

    override fun read(dst: ByteBuffer): Int {
        val tmp = ByteArray(dst.remaining())
        val n = raf.read(tmp)
        if (n > 0) dst.put(tmp, 0, n)
        return n
    }

    override fun write(src: ByteBuffer): Int =
        throw java.nio.channels.NonWritableChannelException()

    override fun position(): Long = raf.filePointer
    override fun position(newPosition: Long): SeekableByteChannel {
        raf.seek(newPosition); return this
    }
    override fun size(): Long = raf.length()
    override fun truncate(size: Long): SeekableByteChannel =
        throw java.nio.channels.NonWritableChannelException()
    override fun isOpen(): Boolean = open
    override fun close() { open = false; raf.close() }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :crypto:testDebugUnitTest --tests "com.cripta.crypto.SeekableInputByteChannelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add crypto/src/main/kotlin/com/cripta/crypto/SeekableInputByteChannel.kt crypto/src/test/kotlin/com/cripta/crypto/SeekableInputByteChannelTest.kt
git commit -m "feat(crypto): read-only seekable byte channel over a file"
```

---

### Task 6: FileCrypto — per-file keyset, encrypt, decrypt, seek, shred

**Files:**
- Create: `crypto/src/main/kotlin/com/cripta/crypto/FileCrypto.kt`
- Test: `crypto/src/test/kotlin/com/cripta/crypto/FileCryptoTest.kt`

Each file gets its own `StreamingAead` keyset, serialized and wrapped by the DEK Aead. AAD = the file's `blobUuid` bytes, binding ciphertext to its identity.

- [ ] **Step 1: Write the failing test (round-trip, seek, shred, wrong-AAD)**

```kotlin
package com.cripta.crypto

import com.google.crypto.tink.Aead
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class FileCryptoTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var dek: Aead

    @Before fun setUp() {
        TinkInit.ensureInitialized()
        val kek = InMemoryKekProvider.generate()
        dek = DekManager.loadDekAead(DekManager.createWrappedDek(kek), kek)
    }

    private fun bigPlaintext(): ByteArray = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }

    @Test
    fun encryptThenDecrypt_roundTrips() {
        val uuid = "uuid-1"
        val wrappedKeyset = FileCrypto.createWrappedFileKeyset(dek)
        val plain = bigPlaintext()
        val blob = tmp.newFile("blob")
        blob.outputStream().use { out ->
            FileCrypto.encryptingStream(wrappedKeyset, dek, uuid, out).use { enc ->
                enc.write(plain)
            }
        }
        assertTrue(blob.length() > 0)
        val decrypted = ByteArrayOutputStream()
        FileCrypto.decryptingStream(wrappedKeyset, dek, uuid, blob).use { dec ->
            dec.copyTo(decrypted)
        }
        assertArrayEquals(plain, decrypted.toByteArray())
    }

    @Test
    fun seekableDecrypt_readsMiddleRange() {
        val uuid = "uuid-seek"
        val wrappedKeyset = FileCrypto.createWrappedFileKeyset(dek)
        val plain = bigPlaintext()
        val blob = tmp.newFile("blob-seek")
        blob.outputStream().use { out ->
            FileCrypto.encryptingStream(wrappedKeyset, dek, uuid, out).use { it.write(plain) }
        }
        val offset = 2_000_000L
        val len = 500
        val ch = FileCrypto.seekableDecryptingChannel(wrappedKeyset, dek, uuid, blob)
        ch.position(offset)
        val buf = ByteBuffer.allocate(len)
        while (buf.hasRemaining()) { if (ch.read(buf) <= 0) break }
        ch.close()
        val expected = plain.copyOfRange(offset.toInt(), offset.toInt() + len)
        assertArrayEquals(expected, buf.array())
    }

    @Test
    fun cryptoShred_makesBlobUndecryptable() {
        val uuid = "uuid-shred"
        val wrappedKeyset = FileCrypto.createWrappedFileKeyset(dek)
        val blob = tmp.newFile("blob-shred")
        blob.outputStream().use { out ->
            FileCrypto.encryptingStream(wrappedKeyset, dek, uuid, out).use { it.write("secret".toByteArray()) }
        }
        // Shredding = discarding wrappedKeyset. Without it, the blob cannot be decrypted.
        var failed = false
        try {
            FileCrypto.decryptingStream(FileCrypto.createWrappedFileKeyset(dek), dek, uuid, blob)
                .use { it.readBytes() }
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("blob must not decrypt with a different keyset", failed)
    }

    @Test
    fun decrypt_withWrongAad_fails() {
        val uuid = "uuid-aad"
        val wrappedKeyset = FileCrypto.createWrappedFileKeyset(dek)
        val blob = tmp.newFile("blob-aad")
        blob.outputStream().use { out ->
            FileCrypto.encryptingStream(wrappedKeyset, dek, uuid, out).use { it.write("secret".toByteArray()) }
        }
        var failed = false
        try {
            FileCrypto.decryptingStream(wrappedKeyset, dek, "different-uuid", blob).use { it.readBytes() }
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("wrong AAD must fail", failed)
        assertFalse(uuid == "different-uuid")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :crypto:testDebugUnitTest --tests "com.cripta.crypto.FileCryptoTest"`
Expected: FAIL — `FileCrypto` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.cripta.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.StreamingAeadKeyTemplates
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SeekableByteChannel

/**
 * Per-file encryption. Each file has its own StreamingAead keyset, serialized and
 * wrapped by the DEK Aead. AAD = blobUuid bytes. Crypto-shredding = destroying the
 * file's wrapped keyset (kept in the metadata DB); the blob is then unrecoverable.
 */
object FileCrypto {

    /** Create a new per-file keyset, wrapped by the DEK. Store the returned bytes in the DB. */
    fun createWrappedFileKeyset(dek: Aead): ByteArray {
        TinkInit.ensureInitialized()
        val handle = KeysetHandle.generateNew(StreamingAeadKeyTemplates.AES256_GCM_HKDF_1MB)
        val serialized = ByteArrayOutputStream().use { out ->
            CleartextKeysetHandle.write(handle, BinaryKeysetWriter.withOutputStream(out))
            out.toByteArray()
        }
        return dek.encrypt(serialized, KEYSET_AAD)
    }

    private fun streamingAead(wrappedKeyset: ByteArray, dek: Aead): StreamingAead {
        val serialized = dek.decrypt(wrappedKeyset, KEYSET_AAD)
        val handle = CleartextKeysetHandle.read(
            BinaryKeysetReader.withInputStream(ByteArrayInputStream(serialized))
        )
        return handle.getPrimitive(StreamingAead::class.java)
    }

    /** Encrypting stream writing ciphertext to [ciphertextOut]. Caller closes it. */
    fun encryptingStream(
        wrappedKeyset: ByteArray, dek: Aead, blobUuid: String, ciphertextOut: OutputStream
    ): OutputStream =
        streamingAead(wrappedKeyset, dek)
            .newEncryptingStream(ciphertextOut, blobUuid.toByteArray())

    /** Full decrypting stream over the ciphertext [blob]. Caller closes it. */
    fun decryptingStream(
        wrappedKeyset: ByteArray, dek: Aead, blobUuid: String, blob: File
    ): InputStream =
        streamingAead(wrappedKeyset, dek)
            .newDecryptingStream(blob.inputStream(), blobUuid.toByteArray())

    /** Seekable decrypting channel for random access (video playback/seek). */
    fun seekableDecryptingChannel(
        wrappedKeyset: ByteArray, dek: Aead, blobUuid: String, blob: File
    ): SeekableByteChannel =
        streamingAead(wrappedKeyset, dek)
            .newSeekableDecryptingChannel(SeekableInputByteChannel(blob), blobUuid.toByteArray())

    /** Plain readable channel wrapper if needed by callers. */
    fun decryptingChannel(
        wrappedKeyset: ByteArray, dek: Aead, blobUuid: String, blob: File
    ): ReadableByteChannel = seekableDecryptingChannel(wrappedKeyset, dek, blobUuid, blob)

    private val KEYSET_AAD = "cripta-file-keyset".toByteArray()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :crypto:testDebugUnitTest --tests "com.cripta.crypto.FileCryptoTest"`
Expected: PASS (all four tests).

- [ ] **Step 5: Run the full crypto test suite**

Run: `./gradlew :crypto:testDebugUnitTest`
Expected: PASS — all tests from Tasks 2-6.

- [ ] **Step 6: Commit**

```bash
git add crypto/src/main/kotlin/com/cripta/crypto/FileCrypto.kt crypto/src/test/kotlin/com/cripta/crypto/FileCryptoTest.kt
git commit -m "feat(crypto): per-file keyset encrypt/decrypt/seek with crypto-shred and AAD binding"
```

---

### Task 7: AndroidKeystoreKekProvider (device-only)

**Files:**
- Create: `crypto/src/main/kotlin/com/cripta/crypto/AndroidKeystoreKekProvider.kt`
- Test: `crypto/src/androidTest/kotlin/com/cripta/crypto/AndroidKeystoreKekProviderTest.kt`

This is the real KEK: an Android Keystore AES-256-GCM key, StrongBox-preferred, requiring user authentication. It cannot run on the JVM, so it is covered by an instrumented test. The wrap/unwrap `Cipher` must be one obtained from an authenticated `BiometricPrompt` `CryptoObject` in production; the provider exposes the `Cipher` factory so the UI layer (a later plan) drives the biometric prompt. For the instrumented test, the key is created without an auth requirement so the crypto path itself can be verified on CI devices.

- [ ] **Step 1: Write the implementation**

```kotlin
package com.cripta.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * KEK backed by Android Keystore. In production the key requires user authentication,
 * so [encryptCipher]/[decryptCipher] must be unlocked via a BiometricPrompt CryptoObject
 * before wrap/unwrap. StrongBox is used when available.
 *
 * @param requireAuth false only for instrumented CI tests.
 */
class AndroidKeystoreKekProvider(
    private val requireAuth: Boolean = true,
    private val alias: String = DEFAULT_ALIAS,
) : KekProvider {

    fun ensureKey() {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(alias)) return
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(requireAuth)
            .setInvalidatedByBiometricEnrollment(true)
        try {
            builder.setIsStrongBoxBacked(true)
            buildKey(builder)
        } catch (e: android.security.keystore.StrongBoxUnavailableException) {
            builder.setIsStrongBoxBacked(false)
            buildKey(builder)
        }
    }

    private fun buildKey(builder: KeyGenParameterSpec.Builder) {
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        kg.init(builder.build())
        kg.generateKey()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (ks.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    /** Cipher for wrapping; feed to BiometricPrompt.CryptoObject before [wrapWith]. */
    fun encryptCipher(): Cipher =
        Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }

    /** Cipher for unwrapping; the IV is the first 12 bytes of the wrapped blob. */
    fun decryptCipher(wrapped: ByteArray): Cipher {
        val iv = wrapped.copyOfRange(0, IV_LEN)
        return Cipher.getInstance(TRANSFORM)
            .apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv)) }
    }

    fun wrapWith(cipher: Cipher, plaintext: ByteArray): ByteArray =
        cipher.iv + cipher.doFinal(plaintext)

    fun unwrapWith(cipher: Cipher, wrapped: ByteArray): ByteArray =
        cipher.doFinal(wrapped.copyOfRange(IV_LEN, wrapped.size))

    // Convenience path for non-auth (test) keys only.
    override fun wrap(plaintext: ByteArray): ByteArray {
        check(!requireAuth) { "auth-required key must use encryptCipher()/wrapWith()" }
        ensureKey()
        return wrapWith(encryptCipher(), plaintext)
    }

    override fun unwrap(ciphertext: ByteArray): ByteArray {
        check(!requireAuth) { "auth-required key must use decryptCipher()/unwrapWith()" }
        return unwrapWith(decryptCipher(ciphertext), ciphertext)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_ALIAS = "cripta_kek"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_LEN = 12
        private const val TAG_BITS = 128
    }
}
```

- [ ] **Step 2: Write the instrumented test**

```kotlin
package com.cripta.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreKekProviderTest {
    @Test
    fun wrapUnwrap_roundTrips_withoutAuthKey() {
        val kek = AndroidKeystoreKekProvider(requireAuth = false, alias = "cripta_kek_test")
        kek.ensureKey()
        val secret = "dek-material".toByteArray()
        val wrapped = kek.wrap(secret)
        assertArrayEquals(secret, kek.unwrap(wrapped))
    }
}
```

- [ ] **Step 3: Run the instrumented test (requires a connected device/emulator, API 31+)**

Run: `./gradlew :crypto:connectedDebugAndroidTest`
Expected: PASS on a device/emulator. If no device is available, this task's verification is deferred to the first device run — note it and continue; unit tests remain green.

- [ ] **Step 4: Commit**

```bash
git add crypto/src/main/kotlin/com/cripta/crypto/AndroidKeystoreKekProvider.kt crypto/src/androidTest/kotlin/com/cripta/crypto/AndroidKeystoreKekProviderTest.kt
git commit -m "feat(crypto): Android Keystore KEK provider with StrongBox and biometric-cipher API"
```

---

## Self-Review

**Spec coverage (crypto-relevant sections):**
- §3 Key management: KEK (`AndroidKeystoreKekProvider`, StrongBox, `setUserAuthenticationRequired`, `setInvalidatedByBiometricEnrollment`), DEK (`DekManager`), unwrap-into-RAM flow — covered. Biometric prompt UI itself is a later plan (§5 App-level).
- §4 Cryptography: Tink StreamingAead per-file keysets, seekable channel, rejected alternatives honored — covered (Tasks 2,5,6).
- §5/§9 Storage + crypto-shredding: per-file wrapped keyset in DB, shred = destroy keyset, AAD = blobUuid — covered (Task 6). Physical blob file deletion is a data-layer concern (Plan 3).
- §8 Viewer seekable video: `seekableDecryptingChannel` + `SeekableInputByteChannel` — covered (Tasks 5,6).
- §12 No network / no backup: manifest has no INTERNET permission, `allowBackup=false`, extraction rules — covered (Task 1).

**Placeholder scan:** No TBD/TODO. Instrumented-test-without-device is an explicit documented deferral, not a placeholder.

**Type consistency:** `KekProvider.wrap/unwrap(ByteArray): ByteArray` consistent across in-memory + Android impls. `DekManager.createWrappedDek/loadDekAead` return `ByteArray`/`Aead` and are used consistently in `FileCryptoTest`. `FileCrypto` methods take `(wrappedKeyset: ByteArray, dek: Aead, blobUuid: String, ...)` uniformly. `AES256_GCM_HKDF_1MB` template used for all StreamingAead keysets.

**Known follow-ups for later plans:** biometric-gated wrap/unwrap wiring (Plan 5), blob file lifecycle + overwrite-on-delete (Plan 3), DEK zeroization policy in RAM (Plan 5, documented limit).
