# Cripta design

How the app looks and why. Product context is in [PRODUCT.md](PRODUCT.md); tokens live in
`app/src/main/kotlin/com/cripta/app/ui/theme/`.

## World

Calm, discreet, dark-first. A private safe for one owner, used in short sessions, often in public.
The look stays close to the original Cripta: near-black layered surfaces, one calm blue accent.
The app icon (keyhole cut out of a circle, `ic_cripta_logo`) is `#5B8DEF` on `#0E1116`; the lock
screen shows the same mark tinted with the theme accent. If the palette changes, change only those two icon colours.

## Colour

| Role | Dark | Light |
|---|---|---|
| Background | `#0A0C10` | `#F7F8FA` |
| Surface | `#12151C` | `#FFFFFF` |
| Surface container high | `#1E232E` | `#E2E7EF` |
| Accent (primary) | `#5AA9FF` | `#2563EB` |
| Text | `#F2F5F9` | `#0F172A` |
| Secondary text | `#98A2B3` | `#475467` |
| Error text | `#FF7A7A` | `#C62828` |
| Favourite | `#F5B041` | `#F5B041` |

- Restrained: neutrals plus one accent. The accent marks actions, state and section headings.
  Never one colour per category, per file type or per screen section.
- Colours come only from the theme roles; no hex values in screens. Exceptions: the player's black
  stage and tag colours.
- Tag colours ([TagPalette]) are the user's. Text on a tag tint goes through `readableOn()` so it
  reaches 4.5:1 in both themes and with custom colours.
- Anything drawn over a picture (tags, length, star, play mark) sits on the dark glass
  `OnImageGlass`, never on a gradient over the whole cover.

## Type

System sans, tabular figures in every style (counts, sizes and durations line up).

- Section heading: `titleSmall`, accent colour, marked as heading.
- Row title: `bodyLarge` medium weight. Its caption: `bodySmall` (12/15), secondary colour, 2dp
  under the title, so a title and its caption read as one item.
- Figures that summarise (Home counts): `headlineMedium`.
- Page titles of the tabs: `headlineSmall` next to the app mark (`HeaderTitle`).
- Counts are written with thousands separators (`formatCount`: 1.234).

## Structure

- **No borders and no bordered containers** around pages, sections, groups or list entries. No
  separator lines as structure. A group is a heading plus space: more space above a heading than
  below it.
- Settings: plain rows (neutral icon, title, caption); sections inside a page are
  `Section(title, description)`; pages are grouped as Preferenze / I tuoi dati / App.
- Filled tonal surfaces are allowed for things that are one object (the Home summary strip, the
  search pill, sheets, dialogs), never to frame a group of options.
- Borders exist only as state markers: selection rings and checks on covers, the chosen colour in a
  picker, the folder highlighted while dragging onto it.
- Top bars are the page itself (same background) and lift one tier only when content scrolls under
  them (`criptaTopBarColors()`). The navigation bar is a flat tier with an accent pill on the
  current tab.
- Counted figures are lines, not tiles: `CountRow` (label left, "1.234 di 5.678" right, optional
  bar). Side-by-side tiles cut 4-digit numbers.

## Shape

- Radii: 10 / 14 / 20 / 26 / 32 dp (`CriptaShapes`). Chips, pills, badges and the navigation
  indicator are fully round.
- Covers: `CoverShape` (18dp) in grids, shelves and pickers; `CoverShapeSmall` (12dp) in list rows
  and small previews.

## Components

- **Tags: one component, `TagPill`** (`ui/components/TagPill.kt`), sizes Small (covers, list rows,
  Home), Medium (pickers, filters, settings library, downloader), Large (viewer quick bar). Off /
  plain: the tag colour at 20% with the name in the same hue at 4.5:1. On: full colour plus a tick.
  Excluded (filters): tint, strike-through and a "no" sign. Over a picture: `onImage = true`.
  "+N" is `MoreTagsPill`.
- **Covers** (`ThumbBox`, `MediaThumb`, `CoverThumb`): layout unchanged (name and caption under
  the cover, tags along its bottom edge), but only the essentials go on the picture: tags, the
  favourite star, the video length (top right) or a play mark, the watched bar. No quality badge
  (4K/HD/SD) and no shade over the image.
- **Folders** (`FolderMosaic`): a stack — the newest cover in front, two sheets in the folder's
  colour peeking above it. Emoji/glyph, star and count go with the name underneath, never on the
  picture.
- **Fields**: `CriptaTextField`, a filled slab with no outline or underline.
- **Chips**: `ChoiceChip` (neutral tonal, accent plus tick when on) and `ActionChip` (tonal);
  no outlined chips. Secondary buttons are tonal (`FilledTonalButton`), not outlined.
- **Downloader**: link field first (paste inside it), the analysed video as a 16:9 cover with title,
  quality as one segmented control with the size under each option, destination as two plain rows
  opening sheets, one wide button; the queue grouped under In corso / In attesa / Finiti.

## Touch and motion

- Targets at least 48dp (chips in the viewer bar at least 36dp tall with padding around them).
- Selected state is never told by colour alone: a tick or a ring goes with it.
- Motion explains a state change and is short (120 / 220 / 320 ms, ease-out, no bounce); it
  collapses when the system removes animations.

## Copy

Plain Italian, short. Controls name their effect, not the mechanism ("Schermo diviso con i
dettagli", "Crea backup"). Messages that point to a setting use its real path
(`Impostazioni › <pagina>`).
