# Cripta

Cripta is an encrypted vault for Android. You keep photos, videos, notes and PDFs on your phone,
out of the system gallery, and open them only inside the app once you unlock it. No file leaves
the device.

> Written entirely with [Claude](https://claude.ai) (Claude Code), through conversation: no line typed by hand.
> A personal project, shared as is, with no warranty. The interface supports twelve languages.

## What it does

### Vault

- Each file gets its own key (Tink StreamingAead) and the database uses SQLCipher; the keys live
  in the phone's Keystore.
- You unlock with the phone's own lock (fingerprint, face or device code), with a Cripta PIN or
  password, with either one, or with both in two steps.
- It locks itself after the time you pick. Screenshots stay blocked and the app hides from the
  recents screen unless you allow them.
- Deleting a file destroys its key (crypto-shredding). With the trash on, you can get the file back
  for the number of days you set, from 1 to 90.

### Importing and downloading

- Import from the gallery, or share files and links to Cripta from any app. After an import you
  choose whether to delete the originals.
- When you import a file with the same name as one in the vault, or identical byte for byte, Cripta
  asks at the end: replace it (the new version keeps the folder and tags), drop the copy, or keep
  both.
- Download videos from a link with yt-dlp: you pick the quality with each one's size shown, the
  folder and the tags. The queue retries after network errors and the extractors update every day.
- Convert videos you can't seek through to MP4; the converted file can take the original's place,
  and the original goes to the trash.

### Organising and finding

- Folders and subfolders with a cover you choose, colour, icon, favourites on top and an opt-out
  from the random pick. Each folder remembers its own sort order.
- Tags with a name, short alias, colour and icon. A cover shows only the tag's symbol or alias.
- Search, filters by type, tag, person and favourite, and saved filters you reuse with one tap.
- Home sums up your photos, videos and notes, lists the videos to resume and shows your people.

### People

- Cripta finds faces in photos and videos on the phone (YuNet and SFace, no outside service) and
  groups them. You name, merge, split or discard them: no face goes to a person without your yes.
- Suggestions ("Maybe it's …?") come one at a time, full screen. You sort unnamed faces a group at a
  time in "To sort".
- Each person has a profile with a photo, notes, links and their media; a video's progress bar marks
  the moments they appear.

### Watching

- A viewer for photos, videos (Media3), Markdown notes and PDFs, with a strip of the neighbouring
  files.
- In videos: resume where you stopped, double tap to skip, brightness and volume with a swipe, hold
  to speed up (1.5×, 2× or 3×), move on to the next file, Picture-in-Picture.
- Intros and outros: Cripta spots them by comparing the audio of the videos in the vault, then
  offers "Skip intro" or skips them for you. You can mark them by hand and trim them to a tenth of
  a second.
- The details panel shows people, tags, intro and outro, the link and the file's figures; in
  portrait it can share the screen with the video.

### Space

- Finds exact duplicates and, with the deep search, similar media (a smaller copy of a photo, a
  re-encoded video). You compare them side by side and pick the one to keep; tags from the deleted
  copies move to it.
- In the trash you open deleted folders too, and restore or delete one file or a selection.

### Backup

- You create an archive encrypted with a passphrase and restore it on this or another phone. A
  restore adds to what you have and replaces nothing.
- You choose what goes in: files (every folder or only some), tags and saved filters, people and
  faces, intro and similar-media analysis, settings, hand-picked covers. Cripta remembers the choice
  and shows the estimated size.
- Settings travel except auto-lock, screenshots and rotation lock, which belong to the phone you
  open the vault on. The trash stays out.
- You can compress notes, PDFs and text losslessly; photos and videos stay as they are.

### Language

Cripta supports English, Italian, Spanish, French, German, Brazilian Portuguese, Simplified Chinese,
Japanese, Korean, Hindi, Arabic and Russian. It follows the phone's language, with English as the
fallback for unsupported languages. On Android 13 and later you can choose the language for Cripta
alone, from Settings › Appearance and folders › Language or the system's
Settings › Apps › Cripta › Language.

### Updates

Cripta checks for new versions published on GitHub, downloads them in the background and opens the
system installer. You can opt into pre-releases too, one for every CI build.

## Security limits

Cripta keeps out the curious: someone holding your unlocked phone, someone browsing your gallery.
It does not stand up to forensic analysis or a rooted phone. Crypto-shredding makes a file
unreadable, but flash storage does not guarantee that the blocks are physically erased. The app
uses the network only to download videos and updates.

## Build

CI (GitHub Actions, `.github/workflows/build-apk.yml`) builds the APK on every push to `main` and
publishes it as a release. Locally:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The Gradle wrapper fetches the Gradle version the project uses. You need JDK 17 and the Android SDK
with platform `android-37.0` (`compileSdk = 37`) and build tools 36, as CI installs them. Without
`keystore.properties` the APK is signed with Android's debug key and won't update a Cripta
installed from CI.

## Stack

Kotlin · Jetpack Compose (Material 3) · Hilt · Room + SQLCipher · Tink · Media3 · ONNX Runtime
(YuNet, SFace) · youtubedl-android (yt-dlp) · Coil · PdfBox.

Look and feel: [DESIGN.md](DESIGN.md) (Manrope, Tabler Icons). Product: [PRODUCT.md](PRODUCT.md).

## Licence

Personal use. No warranty. Fonts and icons carry their own licences in
`app/src/main/assets/licenses/`.
