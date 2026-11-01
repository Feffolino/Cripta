"""
Build app/src/main/assets/tagicons/it.txt: for every tag icon, its emoji and its Italian name and
keywords, from Unicode CLDR (cldr-json, annotations + annotationsDerived, "en" and "it").

The icons are named after the English CLDR name ("red-heart"), so the English annotations lead from
an icon to its emoji and the Italian ones from the emoji to the Italian words.

Usage:
    python -I scripts/tagicons_it.py <cldr dir with ann-en.json ann-it.json der-en.json der-it.json>

Output line: name <TAB> emoji (no FE0F) <TAB> Italian name <TAB> keywords separated by "|".
"""
import json
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ICONS = ROOT / "app/src/main/assets/tagicons/fluent-emoji-high-contrast.txt"
OUT = ROOT / "app/src/main/assets/tagicons/it.txt"


# Fluent names from older Unicode versions -> today's CLDR English name.
RENAMED = {
    "blackbird": "black bird", "phoenix-bird": "phoenix", "hugging-face": "smiling face with open hands",
    "knocked-out-face": "face with crossed-out eyes", "pouting-face": "enraged face",
    "red-triangle": "red triangle pointed up", "keycap-asterisk": "keycap: *", "keycap-hashtag": "keycap: #",
    "person-elf": "elf", "person-fairy": "fairy", "person-genie": "genie", "person-mage": "mage",
    "person-superhero": "superhero", "person-supervillain": "supervillain", "person-vampire": "vampire",
    "person-zombie": "zombie", "person-merpeople": "merperson", "man-merpeople": "merman",
    "woman-merpeople": "mermaid", "person-deaf": "deaf person", "man-deaf": "deaf man", "woman-deaf": "deaf woman",
    "person-blonde-hair": "person: blond hair", "man-blonde-hair": "man: blond hair",
    "woman-blonde-hair": "woman: blond hair", "person-with-bunny-ears": "people with bunny ears",
    "man-with-bunny-ears": "men with bunny ears", "woman-with-bunny-ears": "women with bunny ears",
    "person-wrestling": "people wrestling", "man-wrestling": "men wrestling", "woman-wrestling": "women wrestling",
}


def annotations(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    for key in ("annotations", "annotationsDerived"):
        if key in data:
            return data[key]["annotations"]
    raise SystemExit(f"no annotations in {path}")


def slug(text: str) -> str:
    text = unicodedata.normalize("NFKD", text)
    text = "".join(c for c in text if not unicodedata.combining(c)).lower()
    text = re.sub(r"['’]", "", text)
    return re.sub(r"[^a-z0-9]+", "-", text).strip("-")


def plain(glyph: str) -> str:
    return glyph.replace("️", "")


def main(cldr: Path) -> None:
    en, it = {}, {}
    for name in ("ann", "der"):
        en.update(annotations(cldr / f"{name}-en.json"))
        it.update(annotations(cldr / f"{name}-it.json"))
    it_plain = {plain(g): v for g, v in it.items()}

    by_slug = {}
    for glyph, v in en.items():
        for tts in v.get("tts", []):
            by_slug.setdefault(slug(tts), glyph)

    names = [l.split("\t", 1)[0] for l in ICONS.read_text(encoding="utf-8").splitlines() if "\t" in l]
    lines, missing = [], []
    for name in names:
        glyph = by_slug.get(name) or by_slug.get(slug(RENAMED.get(name, "")))
        entry = it_plain.get(plain(glyph)) if glyph else None
        if not entry:
            missing.append(name)
            continue
        title = (entry.get("tts") or [""])[0]
        words = [w for w in entry.get("default", []) if w != title]
        lines.append("\t".join([name, plain(glyph), title, "|".join(words)]))
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(lines)} icons with Italian words, {len(missing)} without: {' '.join(missing[:40])}")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
