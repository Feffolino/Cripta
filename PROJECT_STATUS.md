# Cripta — Project Status / Handoff

**Last updated:** 2026-11-01
**Repo:** github.com/Feffolino/Cripta (private), branch `main`

## What Cripta is

Android (Kotlin, Jetpack Compose) private vault for one owner: files of any type (photos, videos,
notes, documents) are encrypted on the phone, organised in nested folders and free-form tags, and
viewed in an internal player that never writes plaintext to disk. Product context is in
[PRODUCT.md](PRODUCT.md), the look and its rules in [DESIGN.md](DESIGN.md).

## Validated decisions (see `docs/superpowers/specs/2026-08-23-cripta-vault-android-design.md`)

- **Threat model:** casual snoopers (not forensic/root).
- **Auth:** PIN/biometric via Android Keystore (StrongBox), no passphrase KDF.
- **No network permission** for the vault itself; links open in the user's browser.
- **Min SDK:** 31 (Android 12), target 34.
- **Crypto:** Google Tink `StreamingAead` (chunked, seekable) per file + Android Keystore KEK +
  SQLCipher metadata DB. **Delete:** crypto-shredding (destroy the per-file keyset).

## What the app does today

- **Vault (Cartelle):** nested folders with colour/icon, cover and star; files in grid or list,
  sorts (date, name, size, manual), filters (type, tags included/excluded, people, favourites,
  untagged), saved filters, fast scroller, selection with bulk actions, trash with retention,
  duplicate finder (exact and similar media), random opening.
- **Tags:** colours (own or palette, random assignment), acronym ("sigla"), pin, recents, and an
  icon from the Fluent Emoji high-contrast set (MIT) suggested from the tag's name. Icons replaced
  the old emojis (tag aliases and folder emojis are turned into the matching icon at unlock). The
  icon search takes Italian words: `assets/tagicons/it.txt`, built from Unicode CLDR by
  `scripts/tagicons_it.py`.
- **Viewer:** photos and videos (ExoPlayer over the encrypted file), filmstrip, quick-tag bar,
  details panel (info, tags, intro/outro, people), split view, PiP, gestures, resume, rotation lock
  remembered, MP4 conversion of unsupported videos.
- **Intro/outro:** audio fingerprints find shared intros/outros; signatures, rules and rejected
  sounds; skip button or automatic skip.
- **People:** on-device face detection and embeddings (YuNet + SFace); people are made and named by
  the user only. Each person has a profile (face picked among those found, notes, links, media in
  order) reached from Home and the people page; suggestions of similar faces elsewhere, confirmed
  one by one with the frame and the video at that moment; faces shown top-left on covers (Cartelle
  and Home, on/off and size in Settings); faces found by mistake can be cleared by period or media.
- **Home:** summary, favourites, people, continue watching, shelves.
- **Download:** in-app link downloader (yt-dlp) with quality choice, queue and default destination.
- **Backup:** passphrase-encrypted archive of the whole vault (files, folders, tags, saved filters,
  segments) plus people, faces, face scans and intro/outro analysis; restore merges into the vault.
- **Security:** FLAG_SECURE (screenshots opt-in), auto-lock, lock keeps the navigation state,
  PIN or biometric unlock.
- **Updates:** in-app update check against GitHub releases.

## Build and release

- CI (`.github/workflows/build-apk.yml`) builds and unit-tests every push to `main` and publishes a
  versioned GitHub Release (`v0.1.0-bN`) with the APK attached.
- Local build: JDK 17 (Gradle-managed in `~/.gradle/jdks`) and Gradle 8.9;
  `gradle :app:compileDebugKotlin`, `gradle :app:testDebugUnitTest`.
- Database: Room on SQLCipher, schema version 29; every migration is explicit (no destructive
  fallback). `IntroMigrationTest` checks the face/intro tables, tags, folders and files against a fresh schema:
  extend it with each new migration that touches them.

## Key hierarchy

- KEK = Android Keystore AES-256-GCM, StrongBox, `setUserAuthenticationRequired(true)`,
  `setInvalidatedByBiometricEnrollment(false)` (it is the root of the whole hierarchy: invalidating
  it on a biometric change would make all data unrecoverable). An already invalidated key is
  detected at unlock and a vault reset is offered.
- The Keystore cipher unwraps the **DEK keyset**; the DEK decrypts the **SQLCipher passphrase** and
  wraps every **per-file StreamingAead keyset**.
- Blobs on disk: `filesDir/vault/<uuid>`, random names. The folder tree exists only in the DB.

## Open points

- Analysis of 2026-11-01, not done yet: APK size (CI ships an unminified debug build of ~194 MB;
  R8 on the `debug` build type, arm64 only and PdfRenderer instead of PdfBox would bring it to
  ~80 MB with the same package id); splitting `VaultRepository` further (import and conversion
  state) and the 2000+ line screens.

- Everything since the start of November was verified by build and unit tests only, not yet on a
  device: people profile and suggestions (the similarity threshold `FaceRepository.SUGGEST` = 0.36
  may need tuning on real faces), backup round trip with people and intro data, new navigation
  motion.
- A restore onto a vault that already holds the same people creates them twice (files behave the
  same way: restore merges, it never replaces).
