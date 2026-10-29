# Intro/outro: rilevamento e skip — design

Data: 2026-10-29 · Stato: approvato in brainstorming, in attesa di revisione della spec

## Obiettivo

Nel player video, riconoscere l'intro (sigla iniziale) e l'outro (sigla/titoli finali) e permettere
di saltarle con un pulsante o in automatico. Il rilevamento è sia automatico sia manuale e funziona
su tutto il vault: video in cartelle, in sottocartelle o nella radice.

## Fuori scope

- Euristica nero/silenzio per video senza corrispondenze (poco affidabile).
- Marker di intro/outro disegnati sulla seek bar.
- Impronte audio nel backup (si ricalcolano).

## Comportamento

### Rilevamento automatico

- Ogni video importato, scaricato, convertito o sostituito viene analizzato in background, se
  l'impostazione "Analizza nuovi video" è attiva (default: attiva).
- Impostazioni → Video → "Analizza video esistenti" avvia la scansione dei video senza impronta.
- L'analisi calcola un'impronta audio dei primi 10 minuti (parte *head*) e degli ultimi 5 minuti
  (parte *tail*) di ogni video.
- Ogni video nuovo viene confrontato con le impronte di **tutti** gli altri video del vault, senza
  vincoli di cartella.
- Intro = tratto audio comune lungo almeno 15 s nella parte head di due o più video.
- Outro = tratto audio comune lungo almeno 10 s nella parte tail di due o più video.
- Un video senza corrispondenze non riceve segmenti automatici; resta il segno manuale.
- Video sotto i 2 minuti, senza traccia audio o con audio non decodificabile: marcato "non
  analizzabile", nessun errore all'utente. La notifica finale li conta ("12 analizzati, 2 saltati").

### Segno manuale

- Nuova icona nel top bar del player, accanto ad aspetto, rotazione e PiP. Apre un `CriptaSheet`
  "Intro e finale".
- Lo sheet mostra lo stato attuale per intro e finale: intervallo e origine (es. "1:32–3:01, auto").
- Azioni per intro (identiche per il finale):
  - "Inizio intro qui" e "Fine intro qui": usano la posizione corrente del player.
  - "Rimuovi": il video resta senza intro, scelta manuale. Una scansione successiva non la ricrea.
  - "Cerca negli altri video": usa il tratto segnato come campione e lo cerca nelle impronte di
    tutti i video. Dove lo trova crea un segmento automatico, mai sopra un segmento manuale.
- Un segmento manuale vince sempre su uno automatico.

### Scarto di un falso positivo

- Per un segmento automatico lo sheet offre "Non è un'intro" / "Non è un finale".
- L'azione chiede ogni volta l'ambito:
  - **Solo qui**: il video resta senza quel segmento (scelta manuale, come "Rimuovi").
  - **In tutti i video**: il tratto audio diventa un campione negativo. Il matcher ignora quel
    tratto in tutti i video, presenti e futuri. I segmenti automatici già creati da quel tratto
    vengono tolti. I segmenti manuali restano.
- Impostazioni → Video → "Segmenti scartati" elenca i campioni negativi (video d'origine, tipo,
  durata). Eliminarne uno lo riattiva alla scansione successiva.

### Skip nel player

- Quando la posizione entra in un segmento, compare "Salta intro" / "Salta finale" in basso a
  destra sopra la seek bar, nello stile della card "Prossimo". Resta visibile per tutto il segmento,
  anche con i controlli nascosti, e si chiude con la X.
- "Salta intro" va a `endMs`.
- "Salta finale" passa al file successivo, se esiste e il loop è spento; altrimenti va a fine video.
- Impostazioni "Salta intro in automatico" e "Salta finale in automatico" (default: spente). Con
  lo skip automatico il player salta da solo e mostra per 3 s un chip "Intro saltata · Annulla".
  "Annulla" torna a `startMs` e non salta più quel segmento per la visione in corso.
- Nessuno skip, automatico o con pulsante, mentre l'utente trascina la seek bar.

## Architettura

### `media/intro/AudioFingerprinter`

- Legge il file cifrato con `ChannelMediaDataSource` (già usato in `ThumbnailLoader.kt`) tramite
  `MediaExtractor` + `MediaCodec`. Nessun file in chiaro su disco.
- Converte l'audio in mono a 5512 Hz.
- Produce circa 16 hash da 32 bit al secondo, stile Chromaprint: ogni bit confronta l'energia di
  bande di frequenza adiacenti tra frame consecutivi.
- Il passo PCM → hash è una funzione pura, separata dalla decodifica, e si testa senza Android.

### `media/intro/SegmentMatcher`

- Funzione pura. Input: due sequenze di hash con il loro tempo di inizio. Output: il tratto comune
  più lungo (inizio e fine in entrambe le sequenze) oppure nessun match.
- Candidati di allineamento da hash uguali nei 20 bit alti; verifica dell'allineamento con soglia
  di bit diversi per hash; il tratto si estende finché la media di bit diversi resta sotto soglia.
- Soglie di durata minima: 15 s intro, 10 s outro.
- Esclude i tratti che coincidono con un campione negativo.

### Dati (DB v15 → v16, SQLCipher)

- `audio_prints`: `fileId`, `part` (head/tail), `startMs`, `hashes` (BLOB), `status`
  (ok / non analizzabile). Circa 60 KB a video. Chiave (`fileId`, `part`).
- `media_segments`: `fileId`, `kind` (intro/outro), `startMs`, `endMs`, `source`
  (auto / manual / manual_none). Chiave (`fileId`, `kind`). `manual_none` = "nessun segmento" scelto
  dall'utente.
- `rejected_prints`: `id`, `kind`, `originFileId`, `hashes` (BLOB), `durationMs`. Campioni negativi.
- Cancellazione a cascata di `audio_prints` e `media_segments` insieme al file.
- Sostituzione (re-import) o conversione in MP4: il file nuovo viene rianalizzato. I segmenti
  manuali passano al file nuovo solo se la sua durata differisce di al massimo 2 s da quella del
  vecchio; altrimenti vengono scartati, perché i tempi non sono più affidabili.

### Scansione

- Nuova modalità di `ConversionService`, come la scansione duplicati: notifica con avanzamento,
  annullabile.
- Carica in memoria le impronte del vault (circa 60 MB ogni 1000 video) e confronta ogni video
  nuovo con tutti gli altri.
- Annullata o interrotta, riprende dai video senza impronta: le impronte salvate restano.
- Vault bloccato o app chiusa: stesso comportamento delle altre modalità del servizio.

### Player

- `ViewerViewModel` carica i segmenti del file corrente.
- La logica "quale segmento è attivo, mostrare il pulsante, saltare in automatico, annullato per
  questa visione" è una classe pura testabile, collegata al player in `ViewerScreen.kt`.

### Backup

- I segmenti (`media_segments`) vanno nel manifest JSON come campo opzionale per file. I backup
  vecchi restano validi.
- Impronte e campioni negativi non vanno nel backup.

## Test

- JUnit su `AudioFingerprinter` (parte pura): stesso segnale sintetico = stessi hash; rumore
  leggero = pochi bit diversi; segnali diversi = molti bit diversi.
- JUnit su `SegmentMatcher`: intro comune a offset diversi; nessun match; match più corto della
  soglia; tratto escluso da un campione negativo.
- JUnit sulle regole dei segmenti: manuale contro automatico, `manual_none`, scarto "solo qui" e
  "in tutti i video".
- JUnit sulla logica di skip del player.
- Test di migrazione DB 15 → 16.
- Prova reale: APK di CI su 3–4 episodi della stessa serie e su un video singolo.
