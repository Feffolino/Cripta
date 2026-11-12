# Changelog

What changed in Cripta, day by day, newest first, generated from the commit history.
Entries marked **stable** went out as a stable release.

## 2026-11-13

- The closer look at a video that starts right after giving a face to someone joins the other faces of that person into it again: since yesterday it joined nothing until the next look, when the person had no other face confirmed in that video

## 2026-11-12

- A drag in the manual order stopped by the system shows the saved order again, so a later drag no longer saves the move that was cancelled; files added or removed during a drag are shown once it ends
- "Annulla" right after giving faces to someone always takes them back: the closer look at the video that starts at once could join them into another face of that person, and the undo then found nothing to put back
- Downloads and conversions waiting from before Android closed Cripta are resumed at the next unlock when the first try could not read them (the vault locked meanwhile), without having to restart the app
- A duplicate search asked twice in a row runs once: two at once mixed their results and progress, and "Annulla" could no longer stop the second
- With prereleases off, updates find the last stable release however many prereleases came after it
- A backup whose tag differs from one in the vault only in capitals ("vacanze" and "Vacanze") puts its files under the tag already here, as typing it would, so saved filters find them; two tags an older backup told apart only by capitals stay two
- A restore gives files already in the vault the intro and outro, the faces and the sound analysis they lack from the backup; what they already have stays as it is. Restoring the same backup again no longer adds its signatures, rules and rejected sounds once more
- An import, a download, a conversion or a new note stopped because Android closed Cripta while it was being written no longer leaves a hidden file taking up space: it is removed at the next start
- "Durata della sigla" and "Non è una sigla" on a page opened earlier leave the marks made by hand (or removed) since then, and the new duration reaches videos found since; a rejected intro put back gets its duration once, not twice
- "Annulla" on a merge of people keeps the notes, links and star given to the person afterwards, and gives the merged person back the faces they had said "no" to
- Faces kept apart stay apart when one of them is joined with another face ("Stessa persona"), and the two parts of a face split with "Dividi" are never grouped together again
- "Elimina persona" deletes the person's notes and links too: before, they stayed hidden in the vault and went on into backups
- A backup restored into the vault it came from no longer adds a second copy of a note already there, also one written by an earlier version
- A folder created inside one being deleted goes with it (to the trash too), and one created in a folder deleted meanwhile goes to the root: before, it could stay where no screen reached it
- Emptying old folders from the trash never deletes one restored meanwhile: a note written in it right after stayed in a folder that no longer existed
- Deleting duplicates from a search opened again later never deletes a note edited since, whether it is the copy kept or one of those deleted; the copy kept is read to its end first, so a damaged copy is never the only one left; notes now record the fingerprint of their text when written
- Face analysis no longer sends usage statistics to Microsoft: the new version of the face engine added today had them on by default (phone model, Android version, Cripta's version, timings and errors, never photos, faces or anything in the vault); now it never connects to the internet
- A folder deleted for good (trash off) that stops half way, because of an error or because the app was closed meanwhile, opens again with whatever was not destroyed yet, instead of staying hidden in the trash
- Browsing thousands of files in the viewer no longer risks closing the app when it goes to the background; after Android closes it, the viewer comes back with the media around the one shown
- Each new version of the vault's database is recorded, so future updates can be checked to bring an existing vault forward without losing anything
- Cartelle stays smooth while a face analysis runs: the list is sorted again only when people, their names or their media change
- "Chiedi" after a conversion names the original as it is now, also when it was renamed while being converted
- A face analysis that stops part way on a video (it takes too long) no longer leaves double faces each time it is tried again; faces confirmed, rejected or split by hand always stay
- Opening the vault from two places at once can no longer start before the encryption is ready
- A very large backup made by an older version of Cripta says it is too large for the phone's memory, instead of a generic error
- While a backup is being restored, the Backup page says that changes made meanwhile are saved when the restore ends

## 2026-11-11

- "Dividi il gruppo" outlives "Ricomincia" (keeping corrections) and "Analizza di nuovo": the split follows the faces found again, so split faces never come back as one group
- A face analysis that stalls or cannot read a media to the end keeps the faces it did not reach, with the user's confirmations, "no" answers and splits: before, "Ricomincia" lost them on any media that timed out
- A backup made while people are merged restores every saved filter on them; people and faces are written as one consistent picture
- A restore keeps two people with the same name, and two sibling folders with the same name, apart; it brings back "Dividi il gruppo", a folder's chosen cover and the source video of rules and rejected sounds even for files already in the vault; a restore killed halfway leaves no cover on files it never changed
- An old "Installa" notification never opens an update still being downloaded
- Merging people from a profile keeps the merged notes and links on leaving; "Annulla" on a merge puts its saved filters back; a closer look never gives a face to someone the user said it is not
- "Chiedi" after a conversion is never lost when the app is closed right after; "Applica a tutti" reaches every video of a merged signature, and merging signatures drops rules that would extend new videos again
- Locking the vault while faces, groups or cover choices load, or during a single action (star, rename, new folder or tag), no longer closes the app; exporting thousands of files at once no longer closes it either
- After a vault reset the app starts over in a fresh process, so nothing of the old vault (a running job, an open folder, remembered folders and tags) reaches the new one
- "Annulla" stops a download while it is being encrypted; a failed gallery export leaves no hidden file behind; a new note that cannot be saved leaves no hidden file either
- After Android closes Cripta in the background, the viewer comes back on the media shown, with its list
- A folder deleted for good never leaves a file moved into it meanwhile without a folder; files go to the root when their folder is gone
- The automatic intro skip leaves a spot chosen while paused or from the details alone; a seek during "Salta finale" stays on this video; "Nuova etichetta" tags the media it was opened on
- A download resumed after a restart is kept when the same link was queued again with another quality, folder or tags

- Notes typed on a person's profile are saved even when Back comes right after; "Non è una persona" stays on the profile with its "Annulla"; a suggestion answer that could not be saved is asked again; TalkBack reads each media's name and selection
- "Cerca più a fondo" and "Analizza di nuovo" keep the user's confirmations, "no" answers and the person's face: the same face is recognised by its moment, not by the frame number of the sampling
- A signature's duration and "Applica a tutti" follow the latest choice for videos analysed later; merging into a signature of natural length gives every video that length; a duration change is applied to all videos or none; removing one rejected sample leaves out what another one still rejects
- A new intro analysis asked while one is being cancelled runs after it instead of being lost
- A restore killed halfway leaves no hidden files taking up space; a conversion finished just before the app was closed is not done again
- A saved filter's count on Home follows changes to the people in it; "Annulla" after moving a folder's cover puts the cover back
- Ask for the notification permission on the first background job of any kind; a scan never takes the notification of a slow download
- Links ending with a bracket of their own or with an apostrophe in the path are downloaded as pasted; domains in their own script (例子.中国) are accepted
- A video that cannot be played says why; the play / pause button of a Picture-in-Picture window opened by hand shows the right action
- Counts with large fonts put the figure under the label instead of hiding the label

- Delete an imported original only while it is the very file that was imported and its copy is still in the vault: one edited after the import is kept
- Keep a video's resume point when Android closes Cripta in the background or the vault locks with the video open
- Saved filters keep "Continua a guardare"; a tag merged while it is in the active filter becomes the merged tag there too
- Two new versions of one file in a batch both take its place, tags and star; a new version never goes into a folder deleted meanwhile, and versions are found for names in any script ("ФОТО.jpg", "ÉTÉ.jpg")
- A file shared to Cripta is never lost when the screen is recreated while it is being read
- "Casuale" in a folder just opened (or during a search) picks from what that folder shows, not from the one before
- The first brightness swipe starts from the screen's own level; the volume keys bring back the sound of a video started muted, and nothing plays aloud before the mute
- Picture-in-Picture gets a play / pause button, shows no player buttons, and moves on to the next video after one that ended in the window
- Show each running import's own name and progress, never one file's bytes under another's name
- A note that cannot be saved on leaving the editor asks to try again or leave without the changes
- Notes with Windows line endings keep their headings, check lists and code blocks; formatting a selection that ends on a line break leaves the next line alone; links with brackets open whole
- With prereleases off, updates find the last stable release even behind many prereleases
- Keep the tag order of two quick drags as shown; a setting that cannot be saved says so instead of closing the app; "Annulla" during the PIN check stops the change
- Export a note to the device as a .md file; a backup's restored covers show at once on files already here; a backup a restore could not read is never made
- Restored files get the hash of their own content, so a note edited during its backup is never dropped as a copy; an archive's settings with a wrong type are refused before anything is written
- The hint for files with no preview points to Esporta in the details panel

- Save a note from the viewer and the editor one at a time, without putting back a star, folder or trashing changed meanwhile; quick ticks that fail bring the note back to what is stored, and its cover shows the new ticks
- Back up a note saved during the backup whole, never cut to its old length
- Keep downloads exact across restarts: a removed link never comes back, a resumed one is never lost, and one already imported is not downloaded twice
- Let "Annulla" on a conversion free the queue, stop every part of a large import, and never bring back an original deleted while it was converting (its newest name, folder and star are used)
- Resolve duplicate groups one at a time, so two "Conserva" never delete every copy; "Annulla" on a single copy no longer subtracts the previous resolution
- Never shred a file restored and trashed again while the trash empties or expires
- On restore, give files already in the vault the archive's tags, people and cover (where they have none), keep existing folder covers, reuse people of the same name, free the space of skipped copies, and say when settings could not be restored
- Saved filters follow a tag or person merge instead of losing it; a person with notes or links is never removed as empty
- Take the faces' "Annulla" away once another face change is made, keep it when undoing fails, give it the accessible time and pause it behind the lock
- Keep the faces left out in "Anche questi?" and an intro mark across a rotation, name each face for TalkBack, and seek the right video from the intro timeline
- Pause behind the skipped-ending chip stays on this video; a link typed for one video is never saved on the next
- Keep a zoomed photo in view when dragged, draw the cover tag tray inside the cover in right-to-left languages, and narrow a big bubble beside "+N"
- Run one update download at a time; changing the channel no longer hides the one under way

- Put the tags on a cover on one dark frosted tray, stepped to their rows with nothing empty beside a shorter one, so dark emoji read on dark pictures; emoji lose their shadow and "+N" is plain white text on the tray
- Choose the size of the tags on covers (Impostazioni › Etichette › Sulle copertine › Dimensione): smaller tags fit more to a row, and the preview shows it at once
- Show a folder's card live while customising it, with its files and empty; keep the icon search in view, with icons that fit the name and common ones
- Show in the tag editor how many files and folders a tag is on, the pill assigned and not, and a cover with its mark; put name and short label on one line, delete from the editor, and keep a tag off the covers
- In Impostazioni › Etichette, show each tag's file count and pin, sort by most used, merge two tags and delete the ones on no file
- Save a tag's name, acronym, colour, pin and icon together, or not at all; a name already used by another tag (case aside) is flagged in the editor
- Keep tags whose names differ only in case apart everywhere, and never overwrite a tag's look when its name is typed again as a new tag
- Remove a deleted tag from saved and active filters too; a saved filter left with nothing to filter goes
- Make tag changes on many files exact: "Annulla" undoes only what changed, and the message counts the files really changed
- Keep a folder's chosen cover only while the file is still in it, and keep an older folder's emoji when only its colour changes
- Say when a folder change did not happen (the folder or file was gone), and when a parent folder keeps one out of random
- List older files in the cover picker, redraw a folder's cover when it is regenerated, and show the file type when there is no picture
- Sort tags and folders alphabetically as the app's language does; new tags go last in your own order
- Search tags by name or acronym in Impostazioni, the details panel and the download tags; a pasted emoji finds its icon
- Show the "+N" mark on covers whatever the width, draw cover tags in the library's order, and follow the cover settings on Home's carousel
- Back up from one consistent snapshot; on restore, report folders without a parent, skip files and saved filters already in the vault, and move earlier palette colours to today's
- Draw tag and folder colours in tones of their hue: a quiet tonal ground with its own text on pages, a stronger mark for dots and swatches, the pastel only on covers (with a thin rim and a soft shadow)
- Show tag and folder emoji alone, never on a circle: in the pill, on cover marks (with a soft shadow) and on folders
- Veil the pictures behind a folder's cover with its tone, and ground an empty folder in it

## 2026-11-10

- On the light theme, draw pastel tag and folder colours in a fuller tone of the same hue so they stand out from the page
- Make the tag and folder palette pastel and show every colour in full, never darkened: tag pills, cover marks, the settings preview, a folder's disc and its empty card; text on a colour is black or white, whichever reads
- Keep "Altro" (a round rainbow) first and always in view in the colour row; a colour of your own sits inside it, ticked
- Move tags and folders coloured with a preset of an earlier palette to the new one; colours of your own stay as they are
- Keep the tag icon catalog and word lists in LF on Windows checkouts, so local builds and tests match CI
- Offer a colour of your own for tags and folders ("Altro": hue, saturation and brightness sliders or a typed code) next to the presets
- Give tags and folders one shared palette of twelve colours that matches the app's blue accent; colours saved before keep working
- Show a placeholder for the space bar in Impostazioni and Spazio e pulizia until the figures are known
- Scroll the tag colour swatches sideways on one line
- Make the grid auto-scroll during a drag start later and speed up more gently
- Draw tag and folder icons as Fluent Emoji Flat in full colour (about 1,480 emoji, searchable by name in all twelve app languages); icons saved before keep working
- Improve sort sheet contrast and labels, and enlarge the People favourite target `d9c5d88`
- Test saved viewer settings and the order of hidden favourites `e74e128`
- Show loading placeholders for the Intro and ending analysis summary `defa38a`
- Refine the Arabic search label and Italian passphrase length message `2e82e75`

## 2026-11-09

- Cover corrupted backups in tests, fix lint warnings and update CI tooling `b8a136e` `4c39c52` `664ff1e` `cf66304`
- Keep exception messages out of release logs `3206dce`
- Wipe temporary plaintext keyset copies after use `3c05cba`
- Show the people count and favourite-first rule in the People sort sheet `f08c5ff`
- Put sorting first in the filter sheet and add a matching People sort sheet `dfdcd69`
- Show loading placeholders for Favourites and folder covers, and name people in search `ce3ae0c`
- Move the favourite star towards the corner of the face `038480c`
- Paste links directly into a person's profile `581269a`
- Read saved rotation preferences before opening a video `9ddc85f`
- Open links from notes, sort favourite people first, find people in search and show loading placeholders `e38b0e1`
- Show downloader folder levels in a fixed-height sheet `5aac08d`
- Show the latest backup, a folder picker and a passphrase length bar `b54f35a`

## 2026-11-08

- Ignore stray secrets, loose APKs, Windows metadata and Python caches `712cbfc`
- Add ten languages and locale parity checks `568413c`
- Design notes for the folder row, download picker, space page and quoted texts `d206598`
- Phone-wide space bar, vault cleanup and fixes from the phone test `73795a4`
- App in English and Italian, following the phone's language `b862cbf`

## 2026-11-07

- Stop rebuilding the progress notification on every downloaded chunk `7805b75`
- Notes open straight in the editor; note covers show the title and the formatted text `6984530`
- Collapsible folders group in Cartelle, remembered across launches `73d1bc5`
- README in English `c2f5175`
- README describes the app's features as they are now `574503c`

## 2026-11-06

- Cripta Design System look as the official app; selective, complete backup `12eb35f`
- Video length shown once: on the cover badge, not again under the title `b1c4c4b`

## 2026-11-03

- Material 3 migration plan `9bac3ba`
- Suggestions stricter as a person grows; squeezed videos read as shown; fixes `21faa0d`
- Buttons at their height; the trash's actions at the bottom; similar copies not cut `3e7366e`
- Suggestions: faces set aside are never offered; profile count follows joins `2a55d19`
- Player opened once: a second tap no longer stacks a second player `58eada4`

## 2026-11-02

- People profile: "Analizza meglio i suoi video" `09fdb99`
- Videos with a confirmed face are looked at closer: their stray faces join it or fold away `5ef820f`
- Similar media: choose what to keep, see why, open full screen, "Non sono simili" `21ea829`
- Destructive actions are buttons: DestructiveButton everywhere outside dialogs `ad076a7`
- Backup: the duplicate search's fingerprints are saved and restored `40779f4`
- Import: ask about media that look like vault ones; clearer duplicate settings `20953cb`
- Scan results opened as the vault locks: no tags instead of a crash `c92f2e8`
- Duplicate scan review fixes `2058fdd`
- Similar media: one group at a time, full screen, as the faces to sort `a3c311d`
- Duplicate scan: fingerprints kept, read in parallel, video frames in the app `b32b3df`
- Face scan progress: every media in hand in Settings, only the percent in the notification `de15e8a`
- Face scan reader: software decoders first, hardware last `92a8c91`
- Face scan reader: review fixes `0748f07`
- Face scan: video frames decoded in the app, not through the media server `3dcaf71`
- Face scan: a stuck media is left behind, the file in hand is named `2b38d8a`
- Details: the file's figures as one quiet strip, short values `cf3ce5b`
- Face scan no longer stuck, faster cancel, content covers, steady tags `6a7906f`

## 2026-11-01

- Vault: the + actions open in a column above the button `9067fef`
- Vault: the date/position bubble shows only while the fast scroller is held `7e7c098`
- Review fixes: safer backup/restore, faces kept on re-analysis, crash and leak fixes `4a1f3a1`
- Face scan of chosen media: right total at once, progress frame by frame `a8c2f7e`
- Details: a long press on the media's name copies it `0a0cae9`
- Suggestions: back to the same face, faster frames, fresh after a yes `945fcba`
- Details: 'Mostra tutte' for tags is remembered `eea2bc3`
- Add MIT License to the project `8fab8f1`
- Da sistemare one group at a time, swiped like the suggestions `b3c7888`
- Senza volti: open the media, read frames another way `92a21ff`
- Fields in dialogs look like fields `e26f0e7`
- One look for a picked face `8330ddf`
- Details figures across the panel, link as a field, deeper face search `a210009`
- Suggestions one at a time, swiped like a feed `c07ceef`
- Opening a face's media opens that media `818179f`
- People: split a group for good, two recognition modes, quick selection `22c90a8`
- Da sistemare: every face of a large group can be seen, and all left out at once `a471b9a`
- Faces: clear faces are no longer dropped, cleared media can be analysed again `cf72ba0`
- Details panel: easier people and intro, figures side by side again `6aa4235`
- Settings: clearer hierarchy and spacing `b84321b`
- Details panel follows DESIGN.md `8fc1234`
- Button labels stay on one line `4c88999`
- Pickers show a person's current face `4747148`
- Home start page as a three-page carousel when the vault is empty `423c78f`
- Cleaner faces: remember deleted ones, fold unclear ones, split, group unassigned, look ahead `bbe3aeb`
- Join the tracks of one person within a media, and join or delete faces in the details `056ef61`
- Icons instead of emojis, Italian icon search, faster vault lists, face engine and cover fixes `c7d8d23`
- Faces on Home covers (following 'Persone in Home'), backup estimate counts people and intro data, current PROJECT_STATUS `2d6ef31`
- Follow DESIGN.md: tonal button, motion on the 120/220/320 scale, 48dp icon targets; document tags over pictures, tag icons, people pages and navigation motion `ff04c9e`
- Backup carries people, faces and intro/outro analysis; navigation slides along X `5e73e4a`
- Faces of the people in a media, top-left on its cover; on/off and size in Settings `4400afa`
- People: profile page with notes and links, face suggestions to confirm, sort, clear faces found by mistake `f75562c`
- Tag icons: Fluent Emoji high-contrast set (MIT), suggested from the tag's name, searchable `08ac5e1`
- Steady top bars, folder scroll kept on Back, smooth filmstrip, tinted tag pills with rim `de64c19`

## 2026-10-31

- + button: the action row no longer pushes it up; shuffle centred over it `c31fac2`
- Remembered rotation lock, sober tag palette with random colours, fuller backup `b5b5660`
- Tag colours off everywhere, star on folder covers, quicker folder change, + as a row `a3b8749`
- Settings: aligned sub-options, one-option section folded, clearer backup, trash and duplicates `d981a18`
- Tag pills by state, folder stacks with real pictures and a chosen cover, new + menu `3f0b5e9`
- Filmstrip, duplicates, intro signatures, dialogs and pills follow the redesign `1d73a28`
- Redesign: one tag pill, stacked folders, cleaner covers, new downloader `4abb586`
- Lock screen: the app mark instead of a padlock, the way in at the bottom `6c6b144`
- Light theme tiers that read apart, first-run steps on the empty Home `ea0ff5f`
- Viewer panel: People and Intro sections match the rest `c6b369c`
- Viewer panel and tags, settings type rhythm, Home spacing, DESIGN.md `6b57a2e`
- Tabs: no empty band above the bottom navigation bar `d8f3d8e`
- No frames left: duplicates, download queue, speed-dial; delete an empty person `4036aaf`
- Selection bar keeps full names, plainer download copy `b3b4c0d`
- Settings: pages by task, no frames, search lands on the option `535dbdb`
- Home and tags: colours from the theme, readable list tags `e51545a`
- UI fixes from the audit: lock-safe dialogs, tag batch, notes, people, import errors `d0a4f91`

## 2026-10-30

- People: remove what is left of automatic grouping `9ad89ac`
- People: made by hand only, "Volto N" for new people, one favourite star `efb2959`
- Preview players keep a small buffer: no more out of memory `036314f`
- People: comparison clips follow the queue, no more out of memory `77835ee`
- People: fewer wrong merges, one doubts queue, video compare, faces by hand `18b85b3`
- People: one notification per scan, unnamed faces, simpler editing `9c8bb19`
- People: review, split, undo, moments, Home row and faster screens `381b9e9`
- People: favourite people `ca699f7`
- People: on-device face recognition across the whole vault `62b630d`
- New app icon, one notification icon, short names in the island `21f870a`

## 2026-10-29

- Intro: manage signatures (rename, merge, delete, duration) `6dc033b`
- Intro: show the signatures found, videos grouped by sound `fa86f6f`
- Intro: extend a sound's segments to every video with the same intro `ac69ae1`
- Intro: marking in the details panel, live status and results with covers `7182c1a`
- Intro: tolerate short, differently compressed intros `75520ab`
- Intro: say exactly why a video has no intro, accept short intros `6180405`
- Intro: start the analysis over from Settings, throwing old data away `d7a568a`
- Intro: show progress while earlier videos are matched again `7220648`
- Intro: two copies of the same video are not an intro `d740ebc`
- CI: folder query test reads only the 14-to-15 migration `a6c8081`
- Intro: match earlier fingerprints, say why a video is skipped `a97d5f8`
- Intro: match during the scan, report on videos analysed on request `8890224`
- Intro: analyse from the selection bar, see what the analysis found `d70eb34`
- Viewer: analyse one video's intro and outro from its panel `9ccbef8`
- Intro: quick analysis of the first and last minute only `34c5134`
- Intro: much faster analysis of a large vault `78b464e`
- Intro: manual marks follow new versions, segments in backups `21c7447`
- Player: skip intros and outros, mark and reject them `8936090`
- Player: skip logic for intros and outros `93174c3`
- Settings: intro and outro section `fadcba2`
- Intro: background analysis of new and existing videos `39499da`
- Intro: repository for fingerprints, matching, marks and rejections `18f666e`
- Intro: decode a video's audio from the encrypted file `06c5f27`
- Intro: detection rules, marked-stretch search and rejected samples `ef6bd33`
- Intro: database tables for fingerprints, segments and rejected samples `58e2ba5`
- Intro: find the longest stretch two fingerprints share `11aac4b`
- Intro: Chromaprint-style audio fingerprint `b1169dd`
- Intro: mono resampler and hash codec for audio fingerprints `cc50399`
- Docs: implementation plan for intro/outro detection and skip `4ae9d0d`
- Docs: design spec for intro/outro detection and skip `bd6a388`
- Player: repeated side taps keep seeking without showing the controls `716abbd`

## 2026-10-28

- Same-name and identical imports: always ask on the replacement screen `19983a9`
- Import banner: "Rimuovi i doppioni" removes only what it counted, via the trash `82ae17d`

## 2026-10-18

- Same-name imports: ask every time, or replace automatically via a setting `9331ebf`
- Viewer: a replacing conversion shows its MP4 copy in the original's place `bcf29fc`
- Fix critical data-loss, auto-lock and crash bugs found by a full review `541a264`
- Import: a re-imported edited file takes the tags of its previous version `68b2505`
- Selection: "Scarica" decrypts the selected files into a folder of choice `de91dce`

## 2026-10-14

- Keep every conversion original choice, hide player progress, route web shares to downloader (**stable**) `33efff0`
- Fix direct folder previews and reliable video downloads (**stable**) `12bf5d2`

## 2026-10-13

- Publish stable build with folder improvements and player controls fix (**stable**) `792b7a1`
- Fix player controls reopening during automatic hide; add Media3 regression tests `49a8acf`
- Add folder favorites, random exclusions, scoped sorting and filters; fix player controls `08e6e2d`

## 2026-09-26

- Fix the issues found by the full code review `e5b08b5`
- Hyper Island: round icon buttons back; Installa opens the installer `9c9cfb6`
- Hyper Island: outcomes turn up in place of the progress, text buttons for choices, short time left `a155ac7`
- One time-left calculation for notifications, Hyper Island and screens `a63a81e`
- Hyper Island UX: no stuck 0% ring, time left in the island, clearer Annulla, quieter cancels `68786a8`
- Hyper Island: results no longer vanish, the import choice stays, buttons declare their intent `d4b5a50`
- Hyper Island: results show at once, not after the clean-up `ae5f312`
- Island results as ongoing for a few seconds; shorter sample operations `1ee3da6`
- Several notifications at once: own group each, unique focus keys `69b4674`
- Hyper Island: every result shows in the island for a few seconds; short import title `d12db44`
- Fix build: opt in to the experimental layout API in the Debug isola section `582b5d2`
- Hyper Island buttons: dark disc with a small white glyph, as the Clock's `7dd87db`
- Debug isola: a sample of every operation, each ending with its real outcome `2921539`
- Debug isola HyperOS: every island variant as a switch, and sample notifications `71093df`
- Hyper Island buttons: Material Symbols Rounded icons (filled, bold) `b336ac3`
- Hyper Island: swipe down opens Cripta in a small floating window `eef5784`
- Hyper Island: round icon buttons, as the system Clock shows them `c678830`
- Hyper Island: one button as the template's pill, two as the large row; shorter lines `d539674`
- Update download: Annulla and a note in Impostazioni; Hyper Island expanded on the chat template `1eb5eb6`
- App update: downloaded in the background, with its notification `a5a35f6`
- Trash: select several elements to restore or destroy just those `ca49ce0`
- Hyper Island expanded: template padding and the operation's icon beside the text `bd1ee03`
- Hyper Island: a short name of the operation next to the icon `597b176`
- HyperOS: ongoing notifications on a default-importance (silent) channel; no diagnosis row `ef7b3a2`
- HyperOS focus: payload in the protocol version the phone speaks; version in the diagnosis `b0f7b07`
- HyperOS with focus active: no Android Live Update request, the focus template alone `effe608`
- Notification icons: about four times fewer points, same look `6572f99`
- Selection bar: the sideways nudge only the very first time `4c4190a`
- HyperOS focus: added on any Xiaomi with the island; diagnosis in Impostazioni › Informazioni `a52a0e8`
- Selection bar: the sideways nudge only the first two times ever, not on every selection `4726712`
- HyperOS: Hyper Island focus notifications with large rounded buttons `956fde5`
- Ongoing notification: at most one update a second, identical ones skipped `d26e668`
- Download notification icon: arrow down onto a base `5c8110e`
- Notification icons: one hand-drawn style for every symbol `64bda2f`
- Notifications: Cripta shield icons, Annulla everywhere, choices kept in the island `e24110e`
- Conversion done: the keep/delete choice stays in the Hyper Island until answered `e20943f`
- Convert icon: two arrows turning in a circle (Autorenew), in the app and the notifications `22cd6bb`
- Notifications: one icon per operation; new convert icon in the app `7b10782`
- Viewer: controls hidden by the details stay hidden until the next touch `92aa72d`
- Selection bar: every action on it, scrolling sideways; convert by codec `f0f6479`
- Player controls no longer flash around the details sheet `b5973b0`
- Landscape details: a scroll closes the sheet again, only when it starts at the top `d081e62`
- Details panel: offer "Converti in MP4" for MP4s with other codecs (AV1, VP9…) `c6cd629`
- Notifications never name what was downloaded, converted or imported `d9b2d27`
- Favorites: one star per cover; portrait player side buttons at the top right `7c86869`
- Live Updates for the ongoing work (Hyper Island on HyperOS 3) `eeb5b5c`
- Downloader: clear all chosen tags at once; preview of the analysed link `0fd8fe7`
- Video covers: software decoder fallback, readable output, files without a seek index `6b084c3`
- Details sheet: scrolling never closes it in landscape; player controls stay off around it `27a91ac`
- Lock-safe reads keep working for background jobs that hold the keys `65e884f`
- Fix crash 'Vault locked' in the viewer's tag and file reads after locking `34fb815`

## 2026-09-25

- Split view: slow scrolls of the panel's content work `b87b13d`
- Viewer: the filmstrip no longer takes the swipe up for the details `db78504`
- Viewer: swipes starting on the tag bar work; split view controls stop blinking `ba16fc8`

## 2026-09-24

- Split view: not in Picture-in-Picture, no Back while locked, handle closes on tap `d11c1b8`
- Split view setting: the viewer's tag bar shows as usual, hidden only while the panel is open `d17ff35`
- Split view: panel inside the viewer, the player laid out above it with its controls `1240a52`
- Split view: title bar and the rest of the chrome hidden while it is open `ff28245`
- Split view: player controls off while the panel is open `a15f52e`
- Split view: same opening every time, margins, media fitted to its own shape `75da697`
- Setting: media above the info panel (Instagram-comments style split view) `d276516`
- Recent tags keep their order while on screen `93c54a6`
- Recent tags optional everywhere; without them the viewer's tag bar lists every tag `fd88288`
- Trash: file names no longer shaved by the rounded corners `b7cf15a`
- Trash screen keeps clear of the camera cutout `963cf61`
- Trash: one element count (files and folders alike), no truncated header `947dcbb`
- Sheets close on one Back; swipes never toggle player controls; downloader tags open in place; trash counts `d45ebc8`
- Nothing of the vault over the lock screen; menus stay narrow in landscape `17b955d`
- Sheets: hidden while the vault is locked; actions slide the sheet away first `9304324`
- Filter sheet: no jump when filters turn on `a94d02b`
- One bottom sheet for the whole app; convert banner asks about the original; clearer convert label `391e427`
- Lock screen and code dialogs fit landscape `0a775e4`
- App code: 4+ digit PIN with an on-screen keypad, or an alphanumeric password `b9444ee`
- Unlock modes with an app PIN `5ef6257`
- Viewer: swipe up is the only way to the details panel; top bar keeps just the name `da20c0a`
- Fix crash 'Vault locked' in the folder covers after locking `4e9a7c6`
- Details panel header: preview, type and file actions; top bar shows only the name `04109c1`
- Viewer details: two-step opening, one-swipe close, stops below the camera; controls stay hidden after closing `14c1669`
- Details panel: one swipe down closes it (no half-open step) `a673bca`
- Viewer: swipe-up hint in landscape, controls hidden after closing the details panel `135e2bd`
- Viewer details: no accidental page change on swipe up, back closes the panel, landscape layout `3d1070d`

## 2026-09-23

- Release 0.1.0 [release] `890fb75`
- Trash as a full browsable screen; single-cover folders no longer repeat it `8f5b66c`
- Trash: deleted folders go to the trash and are browsable there `dd194b2`
- Folders: count under the name, size in the folder menu, no clipped text `11a3a3a`
- Fix crash after lock: backup size estimate followed a stale database `d8f80de`
- Viewer: hint clear of the seek bar, pause video when leaving the app `7ca1555`
- Fix crash on reopen after lock; crash log; drop viewer queue `df5059b`
- Viewer controls hold still on swipe, redesigned filter sheet, centred rail `3ee277f`
- Player strip settings, per-orientation grid columns, ETAs, backup size and compression `ded4821`
- Player, chips and tags: fade on close, animated filmstrip, default tag colour `598be12`
- Motion system, usability/a11y overhaul and backend hardening `cbd3bae`
- Player: controls and app chrome show/hide together `de1ba20`
- Settings: swipe-to-close toggle, more player options, clearer Video page `e8c6fcc`
- Player: free rotation with the phone when follow-video-orientation is off `bdc82e6`
- Viewer: swipe up/down works over the whole video, not only the black bands `5594527`
- Viewer landscape: overlays start below the measured top chrome `80a0b51`
- Fix settings grid span; Prossimo card is a button and clears the filmstrip `ff479df`
- Landscape: collapsing top bars and two-column layouts; fast-scroll bubble `e9f2cb5`
- Cartelle: top bar keeps its colour when content scrolls under it `820e292`
- Viewer: declare inPip before the auto-next state that uses it `576a5cc`
- Viewer: "Prossimo" card with auto-advance (setting, not with loop) and queue side panel `ff420d7`
- Landscape pass: reachable dialogs/empty states, player overlays, cutout insets `530c1a4`
- Unlock screen usable in landscape `4ad18fa`
- Landscape: filmstrip on the left edge in the player, capped width for Settings and Download `87d855e`
- Settings always expanded, separate brightness/volume gestures, swipe down to close, filters at half height `a152f66`
- Viewer: compact filmstrip (toggle), smaller details handle, PiP button `c396edc`
- Updater: read the APK from the release's assets endpoint when the list omits it `e7a7cc2`
- CI: make sure the APK is attached to each release `fff2724`
- Cover tags: fill rows from the bottom `aafdf7d`
- Fix backup restore: saved-filter tag lookup is suspending `f0027ca`
- Updater: setting to include pre-releases `0d89f27`
- Persisted queues, content hashes, full backup, video swipe-up, subtree stats `bc52329`
- Player: details+tags panel, filmstrip, 2x hold, gestures, rotation, PiP `0e471de`
- Home and Cartelle redesign `e465854`
- Downloader: tag picker collapsed by default with a one-line summary `a548edc`
- Settings reorganized: category pages, tools apart, search, less clutter `28c36be`
- Duplicate scan runs in the background with a notification `418f25d`
- Fix build: drop Transformer.setMediaSourceFactory (not in media3 1.4.1) `a672fd2`
- Tags: pinned + recent rows with a fixed library; viewer quick tags; notifications fixed `5cc84d0`
- Custom tag colours; safer, queued, less clunky MP4 conversion `cf07377`
- Usability: resume playback, downloader destination + queue, trash, more `1347c62`
- Resolution backfill: never lose probed results on a cancelled pass `b35bbb8`
- Covers built around tags, more settings, navigable settings screen `e47e771`
- Duplicates: in-place video/photo preview; filters: fix tag chip left gap `a649207`
- Downloader paste/clear, filter sheet fix, link paste, covers, stats, dedup compare, import status `22edd75`

## 2026-09-20

- Keep shared link when locked so download is prepared after unlock `2c1ae74`
- Estimate size for every quality, not only Auto `a313e4f`
- Reactive side-cutout inset + congruent tag-pill spacing `1202385`
- Use the physical camera cutout for the tab-screen side margin `9e17e1e`
- List prereleases for the updater; force raw cutout inset on tabs `cb9d3ff`
- Import java.util.Properties (java is a DSL accessor in Kotlin build script) `0fb4648`
- Cripta — encrypted media vault (personal app built entirely with Claude) `3e20a93`
