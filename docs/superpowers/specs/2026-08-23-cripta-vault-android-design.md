# Cripta — Android Encrypted File Vault — Design Spec

- **Date**: 2026-08-23
- **Status**: Approved (design), pending security review
- **Platform**: Android, Kotlin, min SDK 31 (Android 12), target latest stable

## 1. Purpose

Native Android app to encrypt files of any type (photos, videos, notes, documents) at
rest, organize them with nested folders and free-form tags, search/filter them, view
photos and videos inside the app without ever persisting plaintext to disk, decrypt on
demand for export, and securely destroy files.

## 2. Threat Model (explicit)

- **Adversary**: casual snoopers — someone who picks up an unlocked device (partner,
  friend, colleague).
- **In scope**: app-level authentication gate; all file content and metadata encrypted
  at rest; no plaintext of protected content persisted to disk during viewing.
- **Out of scope (stated limits)**: forensic analysis, rooted-device attackers, physical
  memory extraction, and guaranteed physical erasure of flash storage. These are
  explicitly NOT defended against, consistent with the chosen threat level.
- **Core guarantee**: the data encryption key is never written to disk in plaintext; it
  exists in RAM only after unlock and is cleared on lock/background.

## 3. Key Management

- **KEK** (Key Encryption Key): AES-256-GCM key in Android Keystore, StrongBox-backed
  when available (API 31), created with `setUserAuthenticationRequired(true)`.
- **DEK** (Data Encryption Key): random 256-bit master key generated once at first
  setup; stored on disk only in KEK-wrapped form.
- **Unlock flow**: `BiometricPrompt` (biometric or device PIN/credential fallback)
  authorizes use of the KEK via a `CryptoObject` → KEK unwraps the DEK into RAM.
- **Lock flow**: DEK reference cleared from memory on app lock, timeout, or background.
- Auth = PIN/biometric per user choice. This defends against casual snoopers, not
  forensic/root attackers (see threat model).

## 4. Cryptography

- **Library**: Google Tink.
- **File bodies**: Tink `StreamingAead` (AES-256-GCM-HKDF), chunked. Provides a
  *seekable* decrypting channel — required for video playback and seeking with no
  plaintext temp file.
- **Per-file keys**: each file has its own Tink keyset, wrapped by the DEK. Enables
  crypto-shredding (destroy the per-file keyset → ciphertext is unrecoverable).
- **Rejected alternatives**:
  - Hand-rolled JCA AES-GCM chunking — too much bespoke crypto surface, bug-prone.
  - Jetpack Security `EncryptedFile` — no seekable decryption, would force a plaintext
    temp file for video → violates the no-plaintext-on-disk requirement.

## 5. Storage Layout

- Encrypted blobs in app-internal storage: `filesDir/vault/<uuid>`. Filenames are random
  UUIDs — no information leak from the filesystem.
- The folder tree is **logical only** (in the DB), never mirrored on the filesystem.
- Metadata in a Room database encrypted with **SQLCipher**, so original filenames, folder
  names, tags, and dates never leak in plaintext.

## 6. Data Model (metadata DB — SQLCipher)

- `folder`: `id`, `name`, `parentId` (nullable → root), timestamps. Tree structure;
  arbitrary nesting.
- `file`: `id`, `blobUuid`, `originalName`, `mimeType`, `sizeBytes`, `folderId` (single —
  a file lives in exactly one folder), `isFavorite`, `createdAt`, `importedAt`,
  `keysetRef`.
- `tag`: `id`, `name`.
- `file_tag`: many-to-many join (`fileId`, `tagId`). A file may have many tags; tags are
  transversal across folders.

## 7. Folders & Tags Model

- **Folders**: nested (tree). Each file belongs to exactly one folder — "real folder"
  mental model, no duplication.
- **Tags**: multiple, free-form, cut across folders (e.g. tag `viaggio` spans files in
  `Documenti`, `Foto`, `Ricevute`).
- Folder operations: create, rename, move, delete (recursive). Move file between folders.

## 8. Viewer (no plaintext on disk)

- **Photos**: decrypted fully into an in-memory `byte[]` and rendered via Coil. Photos
  are small enough for RAM.
- **Video/audio**: Tink seekable decrypting channel → custom Media3/ExoPlayer
  `DataSource` → play and seek with zero plaintext temp files.
- Viewer activities set `FLAG_SECURE` (blocks screenshots and the recents thumbnail).
- **Not in the internal viewer (v1)**: text notes and documents (PDF, docx, …). These
  can still be stored encrypted and decrypted on demand for export to an external app —
  there is just no in-app viewer/editor for them in v1.

## 9. Secure Deletion (crypto-shredding)

- Primary guarantee: destroy the file's per-file keyset → the ciphertext blob becomes
  unrecoverable noise, even to a forensic attacker.
- Then delete the blob file and the DB row (best-effort overwrite).
- Physical flash wipe is not guaranteed by the OS (wear leveling, TRIM) — stated as a
  known limit; crypto-shredding is the real guarantee.
- Deleting a folder crypto-shreds all contained files and subfolders recursively, behind
  an explicit irreversible-action confirmation.

## 10. Import & Original Handling

- Import via Storage Access Framework (SAF) and via the system share sheet (the app
  registers as a share target). Content is streamed straight into encryption — no
  plaintext copy is made by the app.
- After a successful import, the app asks whether to delete the original from the gallery
  (MediaStore delete via the system dialog).
- A "remember my choice" toggle persists this decision; it is changeable later in
  Settings.
- Note: deleting the original from MediaStore is not a secure wipe of the original file —
  an OS limitation, acceptable for the casual-snooper threat model.

## 11. Search, Filters, Favorites, Random

- **Filters**: folder scope (this folder / include subfolders) combined with tags
  (AND/OR), file type, favorites, date, and filename text.
- **Favorites**: boolean flag on the file; dedicated Favorites view.
- **Random button**: picks a random file from the *current filtered result set*
  (respecting active folder + filters) and opens it in the viewer.
- **Decrypt on demand**: export a decrypted copy via SAF with an explicit warning that
  the exported file leaves the vault's protection.

## 12. App-Level Security

- Auth gate on launch and re-auth on return from background after a configurable
  auto-lock timeout.
- `FLAG_SECURE` on sensitive screens.
- DEK cleared from RAM on lock/background.
- **Zero network permission** declared — no `INTERNET` permission at all. Reduces attack
  surface and is a strong privacy signal. Storage is local-only; no cloud sync in v1.

## 13. Backup

- Local-only in v1. No automatic cloud sync (rejected for v1 complexity). Risk: losing
  the device means losing the data — accepted for v1. Encrypted export/import may be
  added later.

## 14. Tech Stack

Kotlin · Jetpack Compose + Material 3 · MVVM · Hilt (DI) · Coroutines/Flow ·
Room + SQLCipher · Google Tink · AndroidX Biometric · Media3 (ExoPlayer, custom
DataSource) · Coil (images). Min SDK 31, target latest stable.

## 15. Module Structure

- `crypto` — `CryptoManager`, `KeyManager` (Tink + Android Keystore, wrap/unwrap,
  StreamingAead, seekable channel).
- `data` — Room + SQLCipher entities/DAOs, `VaultRepository`.
- `domain` — use cases: `ImportFile`, `OpenFile`, `SecureDelete`, `RandomPick`, `Search`,
  folder/tag operations.
- `ui` — screens: Auth/Onboarding, Vault (folder tree + list), Viewer (photo/video),
  Search/Filter, Favorites, Settings.

## 16. Proposed Extra Features (v1, lightweight)

- Zero network permission (above).
- Auto-lock timeout + re-auth on foreground.
- Import from the system share sheet.
- Multi-select for bulk import / delete / tag / move.

## 17. Out of Scope (v1)

- Cloud sync/backup.
- Plausible deniability / hidden or decoy vault / duress features (not needed for the
  casual-snooper threat model).
- In-app text-note editor and document (PDF/docx) viewer.
- Nested-folder move-conflict edge cases beyond basic move.

## 18. Known Limitations (stated plainly)

- Not resistant to forensic analysis, rooted devices, or physical memory extraction.
- No guaranteed physical erasure of flash storage; crypto-shredding is the deletion
  guarantee.
- Deleting an imported original from MediaStore is not a secure wipe of that original.
- Losing the device with no backup means losing the data (local-only v1).
