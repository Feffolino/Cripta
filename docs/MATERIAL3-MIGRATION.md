# Cripta on Material Design 3

The plan for rebuilding Cripta's UI entirely on Google's Material Design 3 (M3). It replaces the
custom visual system described in [DESIGN.md](../DESIGN.md); product behaviour, copy and privacy
rules stay as they are ([PRODUCT.md](../PRODUCT.md)).

## Build and delivery

- Work on its own branch. **Never push**: commits stay local until the owner has tried the build.
- The M3 build installs **beside** the current app, so the two can be compared on one phone:
  - `applicationIdSuffix` `.m3` (debug: `.m3.debug` or equivalent, as long as it differs from
    `com.cripta.app` and `com.cripta.app.debug`).
  - App label "Cripta M3".
  - Signed with a **different key** from the shared one: the default Android debug key (or a new
    local keystore that is not committed). Do not use `keystore.properties` / the `shared`
    signing config for this build.
- Done means: `./gradlew assembleDebug` and the unit tests pass, the APK path is reported, and
  DESIGN.md on the branch describes the new system.

## Where the rules are

1. **The `material-3` skill** (`~/.claude/skills/material-3/`): load it before any UI work.
   - `SKILL.md`: tokens, component list, anti-patterns, the audit procedure (`/material-3 audit`).
   - `references/color-system.md`: colour roles, tonal pairing, Compose mapping.
   - `references/typography-and-shape.md`: type scale, shape corners, elevation, motion.
   - `references/component-catalog.md`: every component with Compose code.
   - `references/navigation-patterns.md`, `references/layout-and-responsive.md`: navigation bar /
     rail, window size classes, insets, foldables.
   - `references/theming-and-dynamic-color.md`: static and dynamic schemes.
2. **Official docs** when the skill is not enough: m3.material.io and the Compose Material 3 API
   reference (context7 MCP: library `androidx.compose.material3`).
3. **This document**: the decisions specific to Cripta (below). Where it and the skill disagree,
   the skill wins, except for the product rules listed under "Kept from Cripta".
4. **DESIGN.md** (current): read it to understand what each screen does today and why; its
   visual rules are superseded, its product rules are not.

## Where the code is

- Tokens: `app/src/main/kotlin/com/cripta/app/ui/theme/` (`Color.kt`, `Theme.kt`, `Type.kt`,
  `Shape.kt`, `Motion.kt`).
- Shared components: `app/src/main/kotlin/com/cripta/app/ui/components/` (`Controls.kt` holds
  `CriptaTextField`, `ChoiceChip`, `ActionChip`, `DestructiveButton`, `ButtonText`,
  `criptaTopBarColors`, `Section`, `PageIntro`; plus `TagPill.kt`, `CountRow.kt`, `CriptaSheet.kt`,
  `MediaComponents.kt`, `Selection.kt`, `SplitPanel.kt`, `ChipFlowRow.kt`).
- App shell and navigation bar: `ui/AppRoot.kt`.
- Screens by area: `ui/home`, `ui/vault` (folders, covers, viewer), `ui/settings`, `ui/note`,
  `ui/download`, people and faces (search `FaceAvatar`, `PeopleScreen`, `FaceCompare`).
- Dynamic colour: `CriptaTheme(dynamicColor = …)`, wired from `SettingsStore` / `MainActivity` /
  `SettingsScreen`.
- Dependencies: `gradle/libs.versions.toml` (`composeBom = "2024.09.02"` → material3 1.3.0,
  `compileSdk = 34`, AGP 8.5.2, Kotlin 2.0.20).

## Version

M3 Expressive (button sizes XS–XL, `MotionScheme`, the `*Increased` shape tokens, button
groups, loading indicator) needs material3 1.4 or later. Raise the Compose BOM to the newest
stable one whose material3 has these APIs, and `compileSdk` / AGP / Kotlin as far as that BOM
requires. Expressive APIs may still need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`. If
the upgrade cannot build, stay on M3 baseline and record why in DESIGN.md; do not hand-roll
Expressive components.

## Tokens

### Colour

- Generate the full scheme (light and dark, every role including the seven surface tiers) from
  the seed `#5AA9FF` with Material Color Utilities (for example a throwaway script with
  `@material/material-color-utilities`, scheme "TonalSpot" or "Fidelity"), not by hand. Paste
  the generated values into `Color.kt` / `Theme.kt`.
- Page background is `surface`; `background` only mirrors it. No `Bg` / `Surface` split.
- Roles are used in their pairs only (`primary`/`onPrimary`, `secondaryContainer`/
  `onSecondaryContainer`, `surfaceContainer*`/`onSurface`…). No `Color(0x…)` in screens.
- `surfaceTint` stays the generated value; elevation is tonal, shadows only over busy images.
- Dynamic colour (Android 12+) is the existing setting; it now works fully because every screen
  reads roles. Default stays off so Cripta keeps its blue unless the owner turns it on.
- Custom colours that are not M3 roles become **extended colours**, harmonised with the primary
  (`MaterialColors.harmonize` or the utilities' `Blend.harmonize`), each with its own on-colour
  and container: Favourite (seed `#F5B041`), Success. They must reach 3:1 as icons in both
  themes (the current gold on the light background does not).
- Kept as they are (domain colours, not theme roles): `TagPalette` and `readableOn()`, the
  player's black stage, the dark glass under badges on covers (use `scrim` at a fixed alpha).
- The launcher icon and the lock-screen mark use the generated `primary` / dark `surface`.

### Type

- `Typography()` defaults (M3 scale, sizes, line heights, tracking, Regular display/headline),
  on the system font. Add only `fontFeatureSettings = "tnum"` to every style, all 15 of them
  (`headlineLarge` included).
- Use the emphasized variants (Expressive) instead of custom weights where emphasis is needed.
- Role mapping: list headline `bodyLarge`, list supporting text `bodyMedium`, list subheader /
  section heading `titleSmall` in `primary`, dialog title `headlineSmall`, top bar title
  `titleLarge` (small bar) or `headlineSmall`/`headlineMedium` (medium/large bar), Home figures
  `displaySmall` or `headlineLarge`.

### Shape

- `Shapes()` defaults: 4 / 8 / 12 / 16 / 28 dp, plus the Expressive increased tokens when
  available. No other radius in the code: covers use `shapes.medium` (grids) and
  `shapes.small` (rows, filmstrip), sheets and dialogs `extraLarge`, buttons, FABs' pills,
  search bar and badges `CircleShape` / full.

### Motion

- Expressive: `MotionScheme.standard()` for component springs (no bouncy `expressive()` scheme:
  Cripta stays calm). Baseline: M3 easing and duration tokens (emphasized decelerate to enter,
  emphasized accelerate to exit, 200–500 ms).
- Transitions: shared axis X between pages (kept), fade through between tabs (kept), container
  transform / shared axis Z into the player (kept).
- `animationsEnabled()` and the reduced-motion behaviour are kept.

## Components

Replace each custom component with the M3 one, styled only through `…Defaults` and the theme.

| Today | M3 |
|---|---|
| `CriptaTextField` (pill, no indicator) | `TextField` (filled, with active indicator) for forms; `SearchBar` / `DockedSearchBar` for every search; `OutlinedTextField` where a field sits on a tonal surface |
| `ChoiceChip` | `FilterChip` |
| `ActionChip` | `AssistChip` (or `SuggestionChip`) |
| Selected people / tags in pickers | `InputChip` |
| Main action 56dp | `Button` at the default size; Expressive `ButtonDefaults` Medium size only for a page's single hero action |
| Tonal 48dp | `FilledTonalButton` default size; `minimumInteractiveComponentSize()` keeps 48dp targets |
| `DestructiveButton` | `FilledTonalButton` with `errorContainer` / `onErrorContainer`; in dialogs `TextButton` with `error` content |
| `CriptaAlertDialog` | `AlertDialog` defaults (`headlineSmall`, 28dp) |
| `criptaTopBarColors()` | `TopAppBar` / `MediumTopAppBar` / `LargeTopAppBar` defaults (`surface`, scrolled `surfaceContainer`) with `TopAppBarDefaults` scroll behaviour |
| Navigation bar, `primary` indicator | `NavigationBar` defaults (`secondaryContainer` indicator); `NavigationRail` from the medium window size class |
| "+" with tonal pill of actions | `FloatingActionButtonMenu` (Expressive) or FAB + `SmallFloatingActionButton`s; "Apri a caso" a `SmallFloatingActionButton` |
| Settings rows | `ListItem` (leading icon, headline, supporting text, trailing switch); section headings as list subheaders |
| Viewer details sections ⋮ | `DropdownMenu` defaults |
| `CriptaSheet` | `ModalBottomSheet` defaults |
| Start carousel dots | `HorizontalPager` + M3 page indicator built from tokens |
| Progress, counters | `LinearProgressIndicator` / `CircularProgressIndicator`; Expressive `LoadingIndicator` for indeterminate waits |
| Badges on covers | `Badge` for counts; the rest on the scrim |
| Folder / file covers | `Card` is not needed: covers stay images in `shapes.medium`, name and caption under them |
| Downloader quality grid | `SingleChoiceSegmentedButtonRow` when it fits, else `FilterChip`s |
| Snackbar "Annulla" | `SnackbarHost` defaults |

Domain components without an M3 equivalent keep their behaviour but take only tokens:
`TagPill` (shape `shapes.small` like chips, colours via `readableOn()`, tick on "on"), `CountRow`,
`FaceAvatar` (3dp `primary` ring when picked), `FolderMosaic`, the people / similar-media feeds.

## Kept from Cripta

These are product rules, not visual style; they stay in force:

- Dark theme is the default; light and system remain.
- Copy: plain Italian, controls name their effect, settings paths as `Impostazioni › <pagina>`.
- Selected state never by colour alone (tick or ring).
- Touch targets at least 48dp.
- Text at 4.5:1, icons and boundaries at 3:1, in both themes and with custom tag colours.
- Only the essentials on a picture; no badges like 4K/HD; no gradient over covers.
- Groups are a heading plus space: no cards or boxes around sections of options or list entries.
  M3 lists already work this way. Component outlines that the M3 spec defines (outlined text
  field, outlined button, unselected filter chip) are part of the component and allowed.
- Nothing fetched from the network for the UI (no downloadable fonts).

## Order of work

1. Branch, `.m3` variant, separate signing, label. Build once unchanged.
2. Dependency upgrade (see Version). Build.
3. Tokens: colour scheme, extended colours, `Typography`, `Shapes`, motion. Build.
4. Shared components (`ui/components`, `AppRoot.kt`) per the table. Build.
5. Screens, one area at a time (shell and navigation, Home, Cartelle / vault and covers,
   viewer and details, people and faces, settings, notes, downloader, lock screen and
   onboarding). Remove every hex value, custom radius and custom height from screens. Build and
   commit after each area.
6. `/material-3 audit` on `app/src/main/kotlin/com/cripta/app/ui/`; fix until every category is
   7/10 or more, with Colour, Typography, Shape and Components at 9/10.
7. Rewrite DESIGN.md on the branch for the new system (same structure: World, Colour, Type,
   Structure, Shape, Components, Touch and motion, Copy).
