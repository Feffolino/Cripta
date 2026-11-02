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
- Anything drawn over a picture (length, star, play mark, people's faces) sits on the dark glass
  `OnImageGlass`, never on a gradient over the whole cover. Tags over a picture are the one
  exception: a see-through tint of their own colour (see Components).

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
  `Section(title, description)` (the description 4dp under its heading, the options 10dp further,
  32dp between sections); pages are grouped as Preferenze / I tuoi dati / App. A page about one
  feature (Persone, Intro e finale) opens with `PageIntro`: a sentence on what it does, no heading
  repeating the top bar, its state and main action; then its settings; last "Manutenzione", where
  rare actions are text buttons and the one that throws work away is a `DestructiveButton`.
- Filled tonal surfaces are allowed for things that are one object (the Home summary strip, the
  search pill, sheets, dialogs), never to frame a group of options.
- Borders exist only as state markers: selection rings and checks on covers, the chosen colour,
  icon or face in a picker, the folder highlighted while dragging onto it, the face marked on a
  frame in the people suggestions. One exception that is not a state: the rim of a tag over a
  picture, which keeps the tinted pill readable on any image.
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
  Home), Medium (pickers, filters, settings library, downloader), Large (viewer quick bar). Colour means "on":
  Plain (a tag shown on a file) is the tag colour, translucent (35% over the page), name at 4.5:1;
  On (assigned, filtered) is the tag colour in full, white or black text, and a tick, so it reads
  apart from Off even with tag colours off. The tick takes the room of the dot, the pin or the
  tag's icon: turning a tag on or off never changes the pill's width (nothing after it moves). Over a picture (`onImage = true`) the pill is
  the tag colour at 45% (On: 90%) on a light dark glass, with a 1dp rim of the same colour at 85%, text
  white or black at 4.5:1. Off is neutral with muted text and only a dot in the tag colour.
  Excluded: neutral, strike-through, "no" sign. "+N" is `MoreTagsPill`. The text is never given
  a weight (it would measure to nothing in a LazyRow).
- **Tag icons** (`ui/tagicons/`): Microsoft's Fluent Emoji, high-contrast set (MIT, licence in
  `assets/tagicons/`), one colour, drawn in the pill's text colour before the name (in place of
  the colour dot). The editor offers icons fitting the tag's name (Italian or English words), the
  common ones, and a searchable list of all. On a cover, the short style with an icon and no
  alias shows the icon alone.
- **Covers** (`ThumbBox`, `MediaThumb`, `CoverThumb`): layout unchanged (name and caption under
  the cover, tags along its bottom edge), but only the essentials go on the picture: tags, the
  favourite star and the faces of the people in the media (top left, up to three then "+N", size
  and on/off in Impostazioni › Aspetto; on Home they follow "Persone in Home": hidden, or blurred
  until the people row is touched, and again 30 s later), the video length (top right) or a play mark, the watched
  bar. No quality badge (4K/HD/SD) and no shade over the image. Notes, texts and documents
  show their content: a light sheet with the first lines (the first in bold when it reads as a
  title); a PDF its first page. So do the folders holding them.
- **People**: the people page (grid of round faces, filters as chips, order from the top bar) leads
  to each person's **profile** page: face (tap to pick another among those found), name, star,
  notes and links as plain fields, the suggestions, then the media. **Suggestions** ("Forse è …")
  are one face at a time, full screen, swiped up like a feed (the frame takes the free space, the
  two faces side by side under it, "No" and "È …" across the width; an answer glides to the next
  and the answered page stays, marked; after a yes the faces ahead are looked for again with the
  new face; Back from a video returns to the same face; the face shows large while its frame
  loads), most alike first, the face found next to the person's own, the whole frame with the face
  ringed (tap to play from there) and "È …" / "Non è lui/lei": nothing is added without a yes.
  In a media's details a long press selects faces (ring and tick on the face): "Stessa persona"
  joins faces of one person into one row, "Elimina" removes faces found by mistake; "Annulla"
  stays under the list for a few seconds (the details sheet hides the snackbar). "Non è un volto"
  also leaves similar faces out of every new analysis. Faces seen in one frame or blurry fold
  under "Altri N volti poco chiari". "Chi è?" offers "Dividi" (moments that are someone else) and,
  after naming a face, "Anche questi sono …?" for the alike faces of the same media. "Aggiungi un
  volto" looks up to 15 s further when the paused frame has none. Once a face of a video is
  confirmed, the video is looked at closer in the background ("Analizza meglio" in the ⋮ by
  Persone asks again): its other faces that are surely that person join their row, those not found
  again fold under "poco chiari", and one quiet line under the heading says what was done (and that
  "Dividi" undoes a join). Nothing is ever given to anyone by it. The people page's "Da
  sistemare" chip lists unassigned faces grouped by likeness, named or deleted a group at a time;
  it opens one group per page, full screen, swiped up like the suggestions (the frame of the face in
  view, the group's faces in a row, "Chi è?" and "Dividi" across the width, the two deletions as destructive buttons under them,
  faces seen once last); faces tapped out of a group and "Dividi" are never grouped together again. "Senza volti": a tap opens the media (swiping through the list) to add faces by hand, a long
  press says why; "Cerca più a fondo" covers them all, unreadable ones too.
  Recognition (Impostazioni › Persone): "Tutti i volti" (Da sistemare) or "Solo persone note",
  where "Da confermare" lists only faces like someone with faces in at least 2 media. The people
  grid selects as Cartelle (long press, drag over the others): star, "Unisci" (the user picks who
  stays), "Nascondi".
- **Similar media** (Impostazioni › Duplicati e cestino, "Ricerca approfondita"): one group per
  page, full screen, swiped up like "Da sistemare". The copy in view takes the free space (tap: the
  viewer, full screen, swiping through the group); under it name and figures, never on the
  picture. The copies in a row, each kept (ring and tick) or deleted (bin): a tap switches it and
  puts it in view, a long press only shows it; the suggested one starts kept. Then the tags the
  kept copy will have (those coming from deleted copies marked +), "Perché è consigliata" (the
  suggested copy against another: "Conta di più", then "Anche", tags that never decide, the slide
  comparison and both in the viewer), "Tieni N, elimina M" across the width and "Non sono
  simili" (remembered: never grouped again). No confirmation with the trash on, "Annulla" at the
  bottom; the last page says what was freed.
- **Folders** (`FolderMosaic`): a stack — the cover in front (chosen from the folder menu,
  "Scegli la copertina", else the newest file), the next two pictures peeking above it, dimmed
  (the folder colour when there are none). The folder glyph (colour + emoji), star and count go
  with the name underneath, never on the picture. No "more" button: actions are on long-press,
  so the name keeps the whole width.
- **Start carousel** (Home with an empty vault): three pages to swipe, one step each (add,
  organise, lock): a tonal disc with the step's icon in the accent, a `headlineSmall` title, one
  or two lines of why, then the step's own buttons, so each page works without the others. Under
  it "Indietro", dots and "Avanti" in fixed slots; the current dot is a wider accent pill. Pages
  move with the 320 ms ease-out.
- **Long titles** in the top bar (a folder's name) glide sideways (`basicMarquee`), cut when
  animations are off.
- **"+"**: adds things. Its actions (Importa, Cartella, Nota) open in one tonal pill to the left
  of the button, each a round accent icon with its name under it. "Apri a caso" opens rather than
  adds, so it is its own small button above the "+".
- **Tag colours off** (Settings › Etichette): `TagPill` itself switches every tag to the accent,
  so no screen can show tag colours the user turned off.
- **Favourite folders**: the cover star (`CoverStar`) on the front picture, like on files.
- **Folder changes**: the level stays hidden until its folders and files have both loaded, then
  slides in; the previous level never flashes.
- **Fields**: `CriptaTextField`, filled with no outline or underline. Single-line fields (every
  search, links, names) are full pills; multi-line ones a 18dp slab. The fill is one tier above the
  page; inside a dialog (itself that tier) it is a darker well (`surface`, `LocalFieldContainer`),
  so a field never reads as plain text.
- **Chips**: `ChoiceChip` (neutral tonal, accent plus tick when on) and `ActionChip` (tonal);
  no outlined chips. Secondary buttons are tonal (`FilledTonalButton`), not outlined.
- **Destructive actions** (delete, empty, start over): a destructive action is still a button,
  never bare text. `DestructiveButton` is the neutral tonal button with its label and icon in the
  error colour, 48dp tall at least, full width or weighted in a row like the buttons beside it;
  never filled red, so the view keeps its one filled button. Rare actions that throw nothing away
  may stay text buttons. Dialogs (`CriptaAlertDialog`) keep the platform convention: every
  action, a confirm that deletes too, is a text button, the deleting one in the error colour; the
  dialog itself is the weight. Menu items that delete are text in the error colour.
- **Button labels** (`ButtonText`): one line, cut with "…" when the button is too narrow, never
  broken mid-phrase. A label that would often be cut gets shorter words ("Inizio qui (1:23)"),
  and several buttons that may not fit side by side go in a `FlowRow`. Two lines only where the
  control is built for them (the details sheet's icon actions), and then balanced.
- **Details panel** (viewer): the file (preview, name, type and date, its actions), its figures
  as one quiet strip like Home's summary (`DetailFigure`, equal columns: the value in `titleLarge`, its
  unit smaller and quieter, then a small icon and what it is; a video's resolution by its name,
  "Full HD", a photo's in megapixels, the exact pixels under it; two balanced lines at most, never cut), the link
  as a single-line field (paste or copy at its end, saved on Done), then Persone, Etichette,
  Intro e finale, Informazioni. Each section opens with `PanelHeading` (accent heading, optional
  line of help, optional action at its end) and 24dp more above it than under it. One main action
  per section, tonal; the rare ones in a ⋮ menu. Etichette shows the file's, pinned and recent
  tags, the whole library behind "Mostra tutte" (remembered for every media until "Mostra meno"). Labels of values in sentence case ("Tipo",
  "Link"), never all capitals.
  - Persone: a face row is the face, who it is and its moments. A suggestion reads "Forse è …?"
    with ✓ / ✗ on the row; a placed face has a plain 48dp star; not them, Dividi, Elimina and
    "Non è un volto" are in the row's ⋮. "Chi è?" is only the people (search, "Nuova persona" as
    a row like the others) and the name. People given the media by hand are rows with "Togli".
  - Intro e finale: both the accent, the outro lighter. With none found, one line and "Intro da
    qui" / "Finale da qui". A found one shows "Prova" and "Regola" (the ±1 s / ±0,1 s trim opens
    only then); marking it again is in ⋮. Plain words: trovata, segnata da te, sigla.
- **Viewer filmstrip**: covers 26–54dp tall by size step, 10dp apart, 8dp corners (or round);
  the current one larger, full opacity and ringed.
- **Dialogs**: `CriptaAlertDialog` titles in `titleLarge`, shape from the theme (32dp).
- **Downloader**: link field first (paste inside it), the analysed video as a 16:9 cover with title,
  quality as a grid of three options per row with the size under each, destination as two plain rows
  opening sheets, one wide button; the queue grouped under In corso / In attesa / Finiti.

## Touch and motion

- Targets at least 48dp (chips in the viewer bar at least 36dp tall with padding around them).
- Selected state is never told by colour alone: a tick or a ring goes with it.
- A picked face looks the same everywhere (filters, people grid, groups, details, pickers): a 3dp
  accent ring (`FaceAvatar(selected = …)`), the name under it in the accent where there is one.
- Motion explains a state change and is short (120 / 220 / 320 ms, ease-out, no bounce); it
  collapses when the system removes animations.
- Navigation: tabs cross-fade with a slight drift towards the tab chosen; pages (people, a
  profile, favourites, a note, a Settings page) slide in along X from the right and Back reverses
  it; only the player moves in depth (comes forward, the page recedes).

## Copy

Plain Italian, short. Controls name their effect, not the mechanism ("Schermo diviso con i
dettagli", "Crea backup"). Messages that point to a setting use its real path
(`Impostazioni › <pagina>`).
