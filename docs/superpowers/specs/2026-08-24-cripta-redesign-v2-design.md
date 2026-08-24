# Cripta v2 — Redesign + Backup + Notes/PDF — Design Spec

- **Date**: 2026-08-24
- **Status**: Approved (design), pending cost confirmation before implementation
- **Builds on**: `2026-08-23-cripta-vault-android-design.md` (v1, shipped as v0.1.0-b9)

## Goal

Make Cripta feel modern and intentional (it currently reads as "primitive/old"), and add
the highest-value missing capabilities: a premium dark redesign with sectioned navigation
and a rich home, a light theme + Material You, encrypted cross-device backup/restore, and
internal note + PDF viewing.

## Decisions (validated)

- **Aesthetic**: Premium secure dark (deep dark, layered translucent surfaces, soft accent
  glow, fluid motion).
- **Navigation**: bottom navigation — Home, Cartelle, Preferiti, Impostazioni.
- **Home**: shelves — Recenti, Preferiti, Cartelle, plus quick type filters.
- **Missing features to add**: encrypted backup/restore (cross-device, passphrase),
  light theme + Material You, internal notes editor + PDF viewer.
- **Rollout**: all in one (user's choice), delivered as one APK; still authored as four
  logical phases below for clarity.

## Phase 1 — Design System v2 + App Shell

**Design tokens**
- Background `#0A0C10`; surfaces `#12151C` / `#171B24` / `#1E232E`; outline `#2A303C`.
- Accent: calm blue `#5AA9FF` with a low-alpha glow; file accent amber `#F5B041`;
  success `#3FB950`; danger `#EF4444`.
- Text `#F2F5F9`, muted `#98A2B3`. Contrast AA (≥4.5:1 body).
- Type scale enlarged; tabular figures for sizes/dates.
- Shapes: cards 16–20dp, sheets 28dp, chips pill.
- Elevation: translucent surface + 1px hairline border ("glass"), minimal shadow.

**Motion**
- Thumbnail → viewer shared-element transition.
- Staggered grid entrance (30–50ms), spring FABs, thumbnail crossfade,
  bottom-nav indicator slide. Respect reduced-motion.

**Shell**
- Edge-to-edge (draw behind system bars; inset-aware). Transparent system bars.
- `BottomNavBar` with 4 destinations and an animated indicator.
- `HomeScreen`: top app bar (title, search entry, lock); horizontal shelves for
  Recenti (last imported) and Preferiti; a Cartelle row/grid; quick type filters
  (Tutti/Immagini/Video/Note/PDF/Altro).
- `FoldersScreen`: the existing folder tree browser, restyled.
- `FavoritesScreen`: favorites grid.
- `SettingsScreen`: restyled, hosts theme + backup + tag management.
- Empty states: vector illustration + CTA.
- Search becomes its own focused surface reachable from Home.

**Components refactor**: extract shared `MediaGrid`, `MediaCell`, `ThumbBox`,
`FilterSheet` used across Home/Folders/Favorites, so screens stay small and focused.

## Phase 2 — Light theme + Material You

- Settings: theme = System / Light / Dark, and a "colori dinamici" toggle.
- Dynamic color via `dynamicLightColorScheme`/`dynamicDarkColorScheme` (API 31+) when the
  toggle is on; otherwise the hand-tuned Cripta palettes.
- A separately designed light palette (not an inversion); verify contrast independently.
- Persisted in `SettingsStore`.

## Phase 3 — Encrypted backup / restore (cross-device)

- **Format** `.criptabak`: `[magic][version][kdf-params(salt, Argon2id params)]` then an
  AES-256-GCM (Tink StreamingAead) stream of a container holding `manifest.json`
  (files + folders + tags + per-file keyset material) and every ciphertext blob.
- **Key**: derived from a user **backup passphrase** via Argon2id (device-independent), so
  the backup restores on another device (the device Keystore KEK cannot leave the device).
- **Export**: SAF `CreateDocument`; stream directly, no plaintext on disk.
- **Import/restore**: SAF `OpenDocument`; verify passphrase; rebuild DB rows + blobs;
  merge or replace (ask). Re-wrap per-file keysets under the current device DEK on restore.
- **Limits stated**: backup security depends on passphrase strength; a weak passphrase =
  weak backup. Documented in-app.

## Phase 4 — Notes + PDF viewer

- **Notes**: a note is a stored file with mime `text/cripta-note`. `NoteEditorScreen`
  creates/edits; content encrypted like any file (small, in-memory). "Nuova nota" action
  on Home/Folders.
- **PDF**: render decrypted bytes **in RAM** using PdfBox-Android
  (`com.tom-roush:pdfbox-android`) → page Bitmaps; no plaintext temp file. Adds a
  multi-MB dependency (accepted). Viewer gains a PDF page pager.
- Viewer type routing: image / video / note / pdf / other(export).

## Architecture notes

- Keep `:crypto` module unchanged except adding a passphrase-KDF helper (Argon2id via Tink
  or a small KDF) for backup — isolated in `crypto`.
- New packages under `:app`: `ui/home`, `ui/folders`, `ui/favorites`, `ui/note`,
  `backup/`, `ui/components/` (shared media components), `ui/nav/`.
- Repository gains: recent(), favoritesFlow(), note CRUD, backup export/import.
- DB: bump version (+ migration) only if schema changes (notes reuse `files`; likely no
  schema change beyond none — mime distinguishes notes).

## Out of scope (v2)

- Decoy/hidden vault, secure temporary sharing, face/auto-albums, cloud sync.

## Known limitations (unchanged + new)

- Threat model remains casual-snooper; not forensic/root.
- Backup security = passphrase strength.
- PDF/notes decrypt into RAM; a shred-on-close temp path is a fallback only if the
  in-RAM renderer proves unreliable (would be flagged).

## Testing

- Crypto/backup: JVM unit tests for KDF + backup container round-trip (export→import).
- UI: build-green on CI + manual device testing per phase.
