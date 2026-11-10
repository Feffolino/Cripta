"""
Build app/src/main/assets/tagicons/legacy.txt: the Tabler icon shown for a tag or folder that still
carries an old Fluent Emoji icon name, so a migrated vault keeps as many icons as possible.

Sources, first match wins:
1. scripts/tagicons_legacy.txt, picked by hand;
2. the same emoji: a Tabler icon whose CLDR emoji (column 2 of en.txt) is the Fluent icon's emoji;
3. a word of the Fluent name that is a Tabler name ("flag-italy" -> flag, "woman-cook" -> woman),
   a person first, then from the end ("baby-bottle" -> bottle), minus the false friends in BLOCKED;
4. a CLDR English keyword of the Fluent emoji, only towards the few Tabler icons in SAFE_KEYWORD
   (faces -> mood-*, clocks, mail...): elsewhere keywords lead too far ("lime" -> key).
Names Tabler draws itself are left out: a stored name that is a Tabler icon stays that icon.

Usage:
    git show 939e2c7:app/src/main/assets/tagicons/it.txt > fluent-it.txt
    python -I scripts/tagicons_legacy.py fluent-it.txt <dir with cldr-annotations-full and
        cldr-annotations-derived-full unpacked, as for tagicons.py>

fluent-it.txt is the last Fluent keyword asset: name <TAB> emoji <TAB> Italian name <TAB> keywords.
Output: a SHA-256 of the body, then fluent name <TAB> tabler name <TAB> emoji, sorted.
"""
import hashlib
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parent / "app/src/main/assets/tagicons"

# Name words that point at the wrong Tabler drawing.
BLOCKED = {
    ("bell-pepper", "bell"), ("mouse-face", "mouse"), ("mouse-trap", "mouse"),
    ("fountain-pen", "fountain"), ("magnifying-glass-tilted-left", "glass"),
    ("magnifying-glass-tilted-right", "glass"), ("speech-balloon", "balloon"),
    ("thought-balloon", "balloon"), ("cross-mark", "cross"), ("cross-mark-button", "cross"),
    ("bow-and-arrow", "bow"), ("nut-and-bolt", "bolt"), ("fire-engine", "engine"),
}
PEOPLE = {"man", "woman"}
SAFE_KEYWORD = re.compile(r"^(?:mood-.+|clock|mail|heart|bread|train|bus|mountain|plane|phone|flower|bed)$")


def read_lines(path: Path) -> list[str]:
    return path.read_text(encoding="utf-8").replace("\r\n", "\n").splitlines()


def annotations(root: Path) -> dict:
    result = {}
    for package, folder in (("cldr-annotations-derived-full", "annotationsDerived"),
                            ("cldr-annotations-full", "annotations")):
        path = root / package / "package" / folder / "en" / "annotations.json"
        data = json.loads(path.read_text(encoding="utf-8"))[folder]["annotations"]
        result.update({emoji.replace("️", ""): entry for emoji, entry in data.items()})
    return result


def normalize(text: str) -> str:
    return "-".join(re.findall(r"[a-z0-9]+", text.lower()))


def by_word(name: str, tabler: set[str]) -> str | None:
    words = name.split("-")
    if words[0] in PEOPLE or (len(words) > 1 and words[1] in PEOPLE and words[0] in ("old", "pregnant")):
        person = words[0] if words[0] in PEOPLE else words[1]
        if person in tabler:
            return person
    for end in range(len(words), 0, -1):
        for size in (2, 1):
            candidate = "-".join(words[max(0, end - size):end])
            if len(candidate) > 2 and candidate in tabler and (name, candidate) not in BLOCKED:
                return candidate
    return None


def main(fluent_path: Path, cldr: Path) -> None:
    fluent = {}
    for line in read_lines(fluent_path):
        fields = line.split("\t")
        if len(fields) >= 2:
            fluent[fields[0]] = fields[1].replace("️", "")
    english = {}
    tabler_emoji = {}
    for line in read_lines(ASSETS / "en.txt"):
        name, emojis = line.split("\t")[:2]
        english[name] = emojis
        for emoji in emojis.split():
            tabler_emoji.setdefault(emoji.replace("️", ""), name)
    tabler = set(english)
    cldr_en = annotations(cldr)

    rows, counts = {}, {"manual": 0, "emoji": 0, "word": 0, "keyword": 0}
    for line in read_lines(HERE / "tagicons_legacy.txt"):
        if line and not line.startswith("#"):
            fields = line.split("\t")
            if fields[1] not in tabler:
                raise ValueError(f"Unknown Tabler icon in tagicons_legacy.txt: {line}")
            rows[fields[0]] = fields[1:]
            counts["manual"] += 1
    for name, emoji in sorted(fluent.items()):
        if name in rows or name in tabler:
            continue
        target, source = tabler_emoji.get(emoji), "emoji"
        if not target:
            target, source = by_word(name, tabler), "word"
        if not target:
            source = "keyword"
            for keyword in cldr_en.get(emoji, {}).get("default", []):
                key = normalize(keyword)
                target = next((c for c in (key, f"mood-{key}") if c in tabler and SAFE_KEYWORD.match(c)), None)
                if target:
                    break
        if target:
            rows[name] = [target, emoji] if emoji else [target]
            counts[source] += 1

    body = "".join("\t".join([name, *rows[name]]) + "\n" for name in sorted(rows))
    digest = hashlib.sha256(body.encode("utf-8")).hexdigest()
    (ASSETS / "legacy.txt").write_text(f"{digest}\n{body}", encoding="utf-8", newline="\n")
    unmapped = len([n for n in fluent if n not in rows and n not in tabler])
    print(f"{len(rows)} names mapped {counts}, {unmapped} left without an icon")


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
