"""Build app/src/main/assets/gallery-dl.zip: gallery-dl and the pure-Python packages it needs.

The Python that comes with youtubedl-android has no `requests`, so gallery-dl cannot run on it
alone. The wheels are taken from PyPI at the versions pinned here and checked against their
SHA-256 before anything is unpacked. To update: change a version and its checksum (from the
package's page on PyPI), run this script, commit the zip.

    python scripts/build_gallery_dl.py
"""
import hashlib
import io
import json
import pathlib
import urllib.request
import zipfile

PINS = [
    ("gallery-dl", "1.32.19", "f6b1eb5e9a7409a988b754335d99dbb1873e130d133ed5d164d32e5e645d1c50"),
    ("requests", "2.34.2", "2a0d60c172f83ac6ab31e4554906c0f3b3588d37b5cb939b1c061f4907e278e0"),
    ("urllib3", "2.8.0", "0cf3cae568d36aa9576b28dfb35f11328f1cb974ca7647d9475ebb86c75ac6e3"),
    ("idna", "3.20", "ab7ae7122974553370f0bdb919e1a960b2cd1bc1ef0276416d896db81c14582c"),
    ("certifi", "2026.10.23", "e9b54de76e530bb6cf0ccd036787103acb58fd42ba930ea8bf948ddbaa2f61d1"),
    ("charset-normalizer", "3.5.2", "b6b751274acb69d77b3323d6b7dbaa3c7fdfc1eb829b7eb61d262f32e1af9685"),
]
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/gallery-dl.zip"


def wheel(name: str, version: str, sha256: str) -> bytes:
    meta = json.load(urllib.request.urlopen(f"https://pypi.org/pypi/{name}/{version}/json", timeout=30))
    url = next(u["url"] for u in meta["urls"] if u["filename"].endswith("-py3-none-any.whl"))
    data = urllib.request.urlopen(url, timeout=120).read()
    if hashlib.sha256(data).hexdigest() != sha256:
        raise SystemExit(f"{name} {version}: checksum mismatch")
    return data


def main() -> None:
    files: dict[str, bytes] = {}
    for name, version, sha256 in PINS:
        with zipfile.ZipFile(io.BytesIO(wheel(name, version, sha256))) as z:
            for info in z.infolist():
                n = info.filename
                # Shell completions and man pages (*.data), and the wheels' file lists, are not code.
                if info.is_dir() or ".data/" in n or n.endswith("/RECORD"):
                    continue
                files[n] = z.read(info)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    # Same bytes on every run: sorted names, a fixed date.
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for n in sorted(files):
            z.writestr(zipfile.ZipInfo(n, date_time=(2020, 1, 1, 0, 0, 0)), files[n], zipfile.ZIP_DEFLATED, 9)
    print(f"{OUT} · {OUT.stat().st_size // 1024} KB · {len(files)} files")


if __name__ == "__main__":
    main()
