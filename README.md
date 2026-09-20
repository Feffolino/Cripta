# Cripta

Cripta è un **vault cifrato** per Android: foto, video, note e PDF restano cifrati sul dispositivo
e visibili solo dentro l'app, sbloccata con la biometria/credenziale del telefono.

> **App realizzata interamente con [Claude](https://claude.ai) (Claude Code).**
> Progetto personale, scritto conversando con l'AI — nessuna riga scritta a mano.
> Uso personale, "as-is", senza garanzie.

## Funzioni

- 🔒 Vault cifrato (Tink + SQLCipher), sblocco biometrico, blocco automatico
- 🖼️ Visualizzatore integrato per immagini, video (ExoPlayer), note e PDF
- 📥 Import cifrato dalla galleria; condivisione di file/link verso l'app
- ⬇️ Download in-app di video da link (yt-dlp) con scelta risoluzione e stima peso
- 🗂️ Cartelle, etichette, preferiti, ricerca e filtri
- 🎞️ Conversione video in MP4 scorribile
- 💾 Backup cifrato esporta/importa con passphrase
- 🌗 Tema chiaro/scuro + Material You

## Sicurezza — limiti

Cripta protegge da curiosi occasionali. **Non** è pensata contro analisi forense o dispositivi con
root. L'eliminazione usa crypto-shredding (distrugge la chiave del file); la cancellazione fisica su
memoria flash non è garantita dal sistema. **Nessun permesso di rete** oltre a quello necessario per
il downloader. Nessun dato lascia il dispositivo.

## Build

APK generata da CI (GitHub Actions, `.github/workflows/build-apk.yml`) a ogni push su `main` e
pubblicata come release. Build locale:

```bash
gradle :app:assembleDebug
```

Requisiti: JDK 17, Android SDK 34.

## Stack

Kotlin · Jetpack Compose (Material 3) · Hilt · Room + SQLCipher · Tink · Media3/ExoPlayer ·
youtubedl-android (yt-dlp) · Coil · PdfBox.

## Licenza

Uso personale. Nessuna garanzia.
