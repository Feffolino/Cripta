# Cripta design

How the app looks and why. Product context is in [PRODUCT.md](PRODUCT.md); tokens live in
`app/src/main/kotlin/com/cripta/app/ui/theme/`.

## World

Calm, discreet, dark-first. A private safe for one owner, used in short sessions, often in public.
The look stays close to the original Cripta: near-black layered surfaces, one calm blue accent.
The app icon uses the same two colours (background `#0A0C10`, accent `#5AA9FF`): if either
changes, change only those two colours in the icon.

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

## Type

System sans, tabular figures in every style (counts, sizes and durations line up).

- Section heading: `titleSmall`, accent colour, marked as heading.
- Row title: `bodyLarge` medium weight. Its caption: `bodySmall` (12/15), secondary colour, 2dp
  under the title, so a title and its caption read as one item.
- Figures that summarise (Home counts): `headlineMedium`.

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

## Touch and motion

- Targets at least 48dp (chips in the viewer bar at least 36dp tall with padding around them).
- Selected state is never told by colour alone: a tick or a ring goes with it.
- Motion explains a state change and is short (120 / 220 / 320 ms, ease-out, no bounce); it
  collapses when the system removes animations.

## Copy

Plain Italian, short. Controls name their effect, not the mechanism ("Schermo diviso con i
dettagli", "Crea backup"). Messages that point to a setting use its real path
(`Impostazioni › <pagina>`).
