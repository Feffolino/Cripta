# Face test photos

Public-domain works of the US federal government (official portraits and White House / State
Department photos) from Wikimedia Commons, scaled to at most 960 px. Used only by the unit tests.

- `labels.csv`: hand-checked identities, one row per face (image, nose x, nose y, who).
- `opencv_golden.csv`: OpenCV 5 FaceDetectorYN (YuNet 2026may) and FaceRecognizerSF (SFace int8)
  output on four of the photos, to check the Kotlin pipeline against the reference implementation.

Models in `app/src/main/assets/faces`: YuNet (MIT) and SFace (Apache 2.0) from opencv_zoo; the SFace
graph had its initializers removed from the graph inputs (same weights).
