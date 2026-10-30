# Face recognition ("Persone") — design

Approved 2026-10-30. Groups the vault's videos and photos by person, entirely on the device.

## Decisions

- **Models** (in the APK, no network): YuNet 2026may (face detection, MIT, 230 KB, dynamic input)
  and SFace 2021dec int8 (MobileFaceNet embedding, Apache 2.0, 9.9 MB, 128-d), both from
  opencv_zoo, run with ONNX Runtime (MIT). ML Kit was excluded: it sends usage metrics to Google.
  The same runtime runs on the JVM, so the unit tests use the real models on real photos.
- **Frames**: one every 10 s, at least 8, at most 40 per video, nearest keyframe, longest side
  ~1280 px; detection on a ≤640 px copy, alignment from the 1280 px frame. Photos: decoded
  downsampled to ~1280 px. Everything stays in memory (`seekableChannel`, `decryptingStream`).
- **Quality gate** (each rejection is counted and shown): face narrower than 36 px, detector score
  below 0.80, profile (nose offset from the eye midpoint above 0.6 eye distances), blurred.
- **Thresholds** (cosine of normalised SFace embeddings), calibrated on 133 public-domain faces:
  with the quality gate, same person ≥ 0.41, different people ≤ 0.438 (mother/daughter).
  `AUTO = 0.45` joins automatically, `SUGGEST = 0.36` proposes "Forse è X?".
- **Tracks**: faces of one media that look alike (≥ AUTO, never two faces of the same frame)
  form a track; a track keeps its 3 best embeddings and one face crop (JPEG, 112 px).
- **People**: a new track joins the most similar person (max over up to 20 exemplars) when
  ≥ AUTO and not blocked (same frame as another track of that person, rejected by the user);
  between SUGGEST and AUTO it becomes a suggestion; otherwise a new unnamed person. After each
  group of 12 media, unnamed people with two exemplar pairs ≥ AUTO are merged; named ones
  become a merge suggestion.
- **User actions** override everything: rename, merge, delete ("non è una persona": its tracks
  become ignored and similar new faces are ignored too), remove a media (remembered rejection),
  add a media (pick one of its faces, or a manual link when it has none), accept/reject a
  suggestion. "Ricalcola gruppi" re-clusters from stored embeddings keeping all of these.
- **UI**: Settings › Persone (live status, settings, full-screen results with face avatars and
  counts, person page with covers, face overlay, suggestions; tap opens the viewer 2 s before
  the face); vault filter row "Persone" (all selected people present), saved in saved filters;
  details panel section with the media's faces, add/remove, diagnostics.
- **Diagnostics**: per media, frames read, faces found and rejected by reason, decode errors;
  per face, "Confronta": the 5 closest people with similarity and verdict (below threshold,
  uncertain, same frame, rejected by you, low quality).

## Data (DB v22)

- `face_scans(fileId PK, status, framesRead, facesFound, rejectsJson, error, analyzerVersion, scannedAt)`
- `face_tracks(id PK, fileId, timeMs, box, quality, embeddings BLOB fp16, crop BLOB, personId?, state, similarity)`
- `persons(id PK, name, coverTrackId?, createdAt)`
- `person_media(personId, fileId)` manual links without a face
- `face_rejections(trackId, personId)`; ignored people keep their exemplars as rejected faces

## Testing

Unit: YuNet decoding/NMS against OpenCV's own output, alignment, quality gate, frame plan,
clustering (constraints, rejections, merges, order independence), fp16 codec, migration 21→22.
Realistic (JVM, real models): 26 public-domain photos (official portraits and White House /
State Department candids, single and group shots) with hand-labelled identities: every identity
ends in one person, no person mixes two identities, rejected faces carry the right reason.
Videos are verified on the phone.
