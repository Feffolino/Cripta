# Face bench

Public-domain works of the US federal government from Wikimedia Commons (official portraits,
White House, State Department, Defense, NASA photos, and frames of public-domain videos), one
square crop of three face widths around one face, at most 224 px. Used only by `FaceBenchTest`.

- `bench.csv`: file, who, kind (photo / video), media (frames of one video share it), time (s).
- Each crop is the one clear subject of its picture (the largest face, much larger than any
  other). Labels come from the Commons search; crops whose face was closer to another identity
  than to its own were dropped, and so were the Kelly twins (no face model tells them apart).
- The test also makes every photo "video-like" (a 40-75 px face in a 640 x 360 frame, softened,
  noisy), the way most faces in a phone's videos are.
