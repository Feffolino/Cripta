# Cripta

Cripta è una cassaforte cifrata per Android. Tieni foto, video, note e PDF sul telefono, fuori dalla
galleria di sistema, e li apri solo dentro l'app dopo lo sblocco. Nessun file lascia il dispositivo.

> App scritta per intero con [Claude](https://claude.ai) (Claude Code), conversando: nessuna riga a mano.
> Progetto personale, distribuito così com'è, senza garanzie.

## Cosa fa

### Cassaforte

- Cifra ogni file con una chiave propria (Tink StreamingAead) e il database con SQLCipher; le chiavi
  stanno nel Keystore del telefono.
- Sblocchi con lo sblocco del telefono (impronta, volto o codice), con un PIN o una password di
  Cripta, con l'uno o l'altro, o con entrambi in due passaggi.
- Si blocca da sola dopo il tempo che scegli. Gli screenshot restano bloccati e l'app sparisce
  dall'elenco delle recenti, a meno che tu non li permetta.
- Eliminare un file ne distrugge la chiave (crypto-shredding). Con il cestino attivo il file resta
  recuperabile per i giorni che imposti, da 1 a 90.

### Importare e scaricare

- Importi dalla galleria o condividendo file e link a Cripta da qualsiasi app. A fine import scegli
  se eliminare gli originali.
- Se importi un file con lo stesso nome di uno già nel vault, o identico byte per byte, Cripta te lo
  chiede alla fine: sostituire (la nuova versione eredita cartella ed etichette), scartare la copia,
  tenere entrambi.
- Scarichi video da un link con yt-dlp: scegli la qualità vedendo il peso di ciascuna, la cartella e
  le etichette. La coda riprova dopo un errore di rete e gli estrattori si aggiornano ogni giorno.
- Converti in MP4 i video che non si riescono a scorrere; puoi far prendere al file convertito il
  posto dell'originale, che finisce nel cestino.

### Ordinare e ritrovare

- Cartelle e sottocartelle, con copertina a scelta, colore, icona, preferite in cima, esclusione dal
  file casuale. Ogni cartella ricorda il proprio ordinamento.
- Etichette con nome, sigla, colore e icona. Sulla copertina compare solo il simbolo o la sigla.
- Ricerca, filtri per tipo, etichette, persone e preferiti, filtri salvati da riusare con un tocco.
- La Home riassume foto, video e note, mostra i video da continuare e le persone.

### Persone

- Cripta trova i volti in foto e video sul telefono (YuNet e SFace, nessun servizio esterno) e li
  raggruppa. Tu dai un nome, unisci, dividi o scarti: nessun volto finisce a una persona senza il
  tuo sì.
- Le proposte «Forse è …» arrivano una alla volta, a tutto schermo. I volti senza nome li sistemi a
  gruppi in «Da sistemare».
- Ogni persona ha un profilo con foto, note, link e i suoi media; sulla barra di un video vedi i
  momenti in cui compare.

### Guardare

- Visualizzatore per foto, video (Media3), note in Markdown e PDF, con le anteprime dei file vicini.
- Nei video: riprendi da dove eri rimasto, doppio tocco per saltare, luminosità e volume con il
  dito, tieni premuto per accelerare (1,5×, 2× o 3×), passaggio al file successivo,
  Picture-in-Picture.
- Intro e finali: Cripta li riconosce confrontando l'audio dei video del vault e ti offre «Salta
  intro» o li salta da sola. Puoi segnarli a mano e ritoccarli al decimo di secondo.
- Il pannello dei dettagli mostra persone, etichette, intro e finale, link e dati del file; in
  verticale può dividere lo schermo con il video.

### Spazio

- Trova i duplicati esatti e, con la ricerca approfondita, i media simili (stessa foto ridotta,
  stesso video ricodificato). Li confronti uno accanto all'altro e scegli quale tenere; le etichette
  delle copie eliminate passano a quella tenuta.
- Nel cestino apri anche le cartelle eliminate e ripristini o elimini un file alla volta o una
  selezione.

### Backup

- Crei un archivio cifrato con una passphrase e lo ripristini anche su un altro telefono. Il
  ripristino aggiunge, non sostituisce nulla di ciò che hai già.
- Scegli cosa mettere nell'archivio: file (tutte le cartelle o solo alcune), etichette e filtri
  salvati, persone e volti, analisi di intro e simili, impostazioni, copertine scelte a mano. Cripta
  ricorda la scelta e ti mostra la dimensione stimata.
- Le impostazioni viaggiano tranne blocco automatico, screenshot e rotazione bloccata, che
  riguardano il telefono su cui apri il vault. Il cestino resta fuori.
- Puoi comprimere note, PDF e testi senza perdita; foto e video restano come sono.

### Aggiornamenti

Cripta controlla le nuove versioni pubblicate su GitHub, le scarica in background e apre
l'installazione di sistema. Puoi ricevere anche le pre-release, una per ogni build della CI.

## Limiti di sicurezza

Cripta ti protegge dai curiosi: chi prende il telefono sbloccato, chi guarda la galleria. Non regge
un'analisi forense né un telefono con root. Il crypto-shredding rende illeggibile il file, ma la
memoria flash non garantisce la cancellazione fisica dei blocchi. L'app usa la rete solo per
scaricare video e aggiornamenti.

## Build

La CI (GitHub Actions, `.github/workflows/build-apk.yml`) compila l'APK a ogni push su `main` e la
pubblica come release. In locale:

```bash
gradle :app:assembleDebug :app:testDebugUnitTest
```

Servono JDK 17 e Android SDK 34. Senza `keystore.properties` l'APK usa la chiave di debug di Android
e non aggiorna una Cripta installata dalla CI.

## Stack

Kotlin · Jetpack Compose (Material 3) · Hilt · Room + SQLCipher · Tink · Media3 · ONNX Runtime
(YuNet, SFace) · youtubedl-android (yt-dlp) · Coil · PdfBox.

Aspetto: [DESIGN.md](DESIGN.md) (Manrope, Tabler Icons). Prodotto: [PRODUCT.md](PRODUCT.md).

## Licenza

Uso personale. Nessuna garanzia. Font e icone hanno le proprie licenze in
`app/src/main/assets/licenses/`.
