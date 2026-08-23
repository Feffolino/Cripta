# Cripta — Project Status / Handoff

**Last updated:** 2026-08-23
**Repo:** github.com/Feffolino/Cripta (private), branch `main`

## What Cripta is

Android (Kotlin) app: encrypt files of any type (photos, videos, notes, documents),
organize with **nested folders + free-form tags**, search/filter, random-open from filtered
results, favorites, internal viewer for photos/videos that never writes plaintext to disk,
decrypt-on-demand export, and secure deletion (crypto-shredding).

## Validated decisions (see `docs/superpowers/specs/2026-08-23-cripta-vault-android-design.md`)

- **Threat model:** casual snoopers (not forensic/root).
- **Auth:** PIN/biometric via Android Keystore (StrongBox), no passphrase KDF.
- **Backup:** local-only, zero network permission.
- **Min SDK:** 31 (Android 12).
- **Folders:** nested tree, one file per folder. **Tags:** multiple, transversal.
- **Viewer:** photos + videos only (notes/docs are storable + decrypt-on-demand, no in-app viewer v1).
- **Crypto:** Google Tink `StreamingAead` (chunked, seekable) + Android Keystore KEK + SQLCipher metadata DB.
- **Delete:** crypto-shredding (destroy per-file keyset).

## Status by plan (see `docs/superpowers/plans/`)

| # | Plan | Status |
|---|------|--------|
| 1 | Crypto foundation | **DONE** (code + JVM unit tests written; NOT yet compiled/run) |
| 2 | Data layer (Room+SQLCipher, repository) | TODO |
| 3 | Import + storage + crypto-shred | TODO |
| 4 | Folders (tree) + tags | TODO |
| 5 | Auth gate + app hardening (FLAG_SECURE, auto-lock) | TODO |
| 6 | Vault UI (tree nav, list, multi-select) | TODO |
| 7 | Viewer (photo in-RAM, video seekable ExoPlayer) | TODO |
| 8 | Search/filters/favorites/random + settings | TODO |

Only plan 1 is written. Plans 2–8 not yet authored in detail; they exist as the roadmap above.

## What's in the repo now

- `crypto/` module: complete engine — `TinkInit`, `KekProvider`, `InMemoryKekProvider`,
  `AndroidKeystoreKekProvider`, `DekManager`, `FileCrypto`, `SeekableInputByteChannel`,
  plus JVM unit tests under `crypto/src/test/`.
- `app/` module: scaffold only — build.gradle, AndroidManifest (share target, no INTERNET,
  allowBackup=false), theme, adaptive launcher icon, extraction rules. **No Kotlin app code yet**
  (no `CriptaApp`, no `MainActivity`, no DB/UI). The app module will NOT build until these exist.
- Full Gradle version catalog (`gradle/libs.versions.toml`) already lists the whole intended
  stack (Compose, Hilt, Room, SQLCipher, Media3, Coil, DataStore).

## Key design of the key hierarchy (for whoever implements plan 5)

- KEK = Android Keystore AES-256-GCM, StrongBox, `setUserAuthenticationRequired(true)`,
  `setInvalidatedByBiometricEnrollment(true)`. Unlocked via BiometricPrompt `CryptoObject`.
- The biometric-authorized Keystore cipher unwraps the **DEK keyset bytes**
  (`DekManager.newDekKeysetBytes()` at setup → `dekAeadFromBytes()` after unlock).
- DEK (a Tink AEAD) then decrypts the **SQLCipher DB passphrase** (stored DEK-wrapped) and
  wraps/unwraps every **per-file StreamingAead keyset** (`FileCrypto`).
- Wrapped blobs (wrappedDek, wrapped DB key) are safe to store in plain prefs/file.
- Blobs on disk: `filesDir/vault/<uuid>`, random UUID names. Folder tree is logical (DB only).

## Build / toolchain notes (IMPORTANT)

- This dev machine had: JDK 25 (too new for AGP 8.5 — needs JDK 17), **no Android SDK**, no Gradle.
- A portable-toolchain download (JDK 17 + Android cmdline-tools + Gradle 8.9) was attempted but
  the network was unreliable (TLS revocation-offline; needs `curl --ssl-no-revoke`; downloads
  reset mid-transfer). **The project has never been compiled or built into an APK yet.**
- To build an APK you need: JDK 17, Android SDK (platform-android-34, build-tools, platform-tools),
  and the Gradle wrapper (run `gradle wrapper` once, or build via Android Studio).
- Recommended: open in Android Studio (Ladybug+) which supplies JDK + SDK + Gradle, or a CI/cloud
  environment with the Android SDK preinstalled.

## Next actions

1. Author + implement plans 2–8 (data layer first).
2. Add `CriptaApp` (@HiltAndroidApp) + `MainActivity` (FLAG_SECURE) so the app module compiles.
3. First build → produce a debug APK for testing.
4. Then UX polish pass (ui-ux-pro-max) and a code-level security review.
