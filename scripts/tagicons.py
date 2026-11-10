"""Generate the Android tag and folder icon assets from Fluent Emoji Flat.

Sources, unpacked from npm (not kept in the repository):
    @iconify-json/fluent-emoji-flat 1.2.6   icons.json, chars.json, metadata.json
                                            (Microsoft Fluent Emoji, MIT)
    cldr-annotations-full 49.0.0 and cldr-annotations-derived-full 49.0.0   (Unicode CLDR)

Usage:
    python -I scripts/tagicons.py <dir with the three unpacked packages>
    <dir>/fluent-emoji-flat/{icons,chars,metadata}.json
    <dir>/cldr-annotations-full/package/annotations/<lang>/annotations.json
    <dir>/cldr-annotations-derived-full/package/annotationsDerived/<lang>/annotations.json

Output, in app/src/main/assets/tagicons/:
    fluent-emoji-flat.txt   SHA-256 of the body, then: name <TAB> parts, parts separated by "|".
                            A part is "f:RRGGBB[AA]:path" or "e:..." (even-odd) for a fill, and
                            "s:RRGGBB[AA]:width:cap:path" for a stroke (cap: b, r or s). The
                            viewport is 32 x 32; ui/tagicons/TagIcons.kt crops it to 1..31.
    <lang>.txt              name <TAB> emoji <TAB> name in that language <TAB> keywords, "|" apart.
legacy.txt (old name <TAB> icon: the Tabler names of the previous catalog and the Fluent ones this one
dropped, with a SHA-256 header like the catalog's) is kept by hand.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import tagicons_svg as svgconv  # noqa: E402

ASSETS = HERE.parent / "app/src/main/assets/tagicons"
LANGUAGES = "en it ar de es fr hi ja ko pt ru zh".split()
SKIN = re.compile(r"-(?:medium-light|medium-dark|medium|light|dark)$")
TONE_CODEPOINTS = {"1f3fb", "1f3fc", "1f3fd", "1f3fe", "1f3ff"}
# Too much detail to read at 14-18 dp (more than 7000 characters of path data) is dropped too.
MAX_SPEC = 7000
CATEGORIES = ["Smileys & Emotion", "People & Body", "Animals & Nature", "Food & Drink", "Travel & Places",
              "Activities", "Objects", "Symbols", "Flags"]
# Abstract signs and UI glyphs that make poor tag or folder icons.
EXCLUDED = re.compile(
    r"arrow|^keycap-|^input-|^japanese-.*-button$|^japanese-symbol-for-beginner$|-blood-type$|"
    r"^(?:copyright|registered|trade-mark|cl-button|id-button|ng-button|vs-button|p-button|circled-m|"
    r"part-alternation-mark|wavy-dash|curly-loop|double-curly-loop|divide|multiply|plus|minus|"
    r"heavy-equals-sign|eight-spoked-asterisk|diamond-with-a-dot|radio-button|white-square-button|"
    r"black-square-button|black-small-square|black-medium-small-square|white-small-square|"
    r"white-medium-small-square|downwards-button|upwards-button|fast-up-button|fast-down-button|"
    r"antenna-bars|bright-button|dim-button|vibration-mode|mobile-phone-off|wireless|atm-sign)$"
)
KEEP_ARROW = {"bow-and-arrow"}


def emoji_of(codes):
    return "".join(chr(int(c, 16)) for c in codes.split("-")).replace("️", "")


def annotations(root, lang):
    result = {}
    for package, folder in (("cldr-annotations-derived-full", "annotationsDerived"),
                            ("cldr-annotations-full", "annotations")):
        path = root / package / "package" / folder / lang / "annotations.json"
        data = json.loads(path.read_text(encoding="utf-8"))[folder]["annotations"]
        result.update({emoji.replace("️", ""): entry for emoji, entry in data.items()})
    return result


def write(name, contents):
    (ASSETS / name).write_text(contents, encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("packages", type=Path)
    args = parser.parse_args()
    pack = args.packages / "fluent-emoji-flat"
    icons = json.loads((pack / "icons.json").read_text(encoding="utf-8"))["icons"]
    chars = {name: codes for codes, name in json.loads((pack / "chars.json").read_text(encoding="utf-8")).items()}

    catalog, emojis, skipped = {}, {}, {}
    # In the emoji picker's order: its categories, each by code points, older symbols last. An icon without a category
    # (a newer emoji) joins the one of the icon before it.
    groups = json.loads((pack / "metadata.json").read_text(encoding="utf-8"))["categories"]
    rank = {name: i for i, category in enumerate(CATEGORIES) for name in groups[category]}
    order, last = [], 0
    def by_code(name):
        codes = [int(code, 16) for code in chars[name].split("-")]
        return codes[0] < 0x1F000, codes  # the older text-style symbols after the picture emoji

    for name in sorted(chars, key=by_code):
        last = rank.get(name, last)
        order.append((last, name))
    order = [name for _, name in sorted(order, key=lambda pair: pair[0])]
    for name in order:
        icon = icons.get(name)
        if icon is None:
            continue
        codes = chars[name]
        if TONE_CODEPOINTS & set(codes.split("-")) or (SKIN.search(name) and SKIN.sub("", name) in icons):
            continue
        if EXCLUDED.search(name) and name not in KEEP_ARROW:
            skipped[name] = "abstract sign"
            continue
        try:
            spec = svgconv.spec(svgconv.convert(icon["body"]))
        except svgconv.Unsupported as why:
            skipped[name] = str(why)
            continue
        if len(spec) > MAX_SPEC:
            skipped[name] = "too detailed"
            continue
        catalog[name] = spec
        emojis[name] = emoji_of(codes)
    body = "".join(f"{name}\t{spec}\n" for name, spec in catalog.items())
    write("fluent-emoji-flat.txt", f"{hashlib.sha256(body.encode()).hexdigest()}\n{body}")
    print(f"{len(catalog)} icons, {len(body) // 1024} KiB; skipped:",
          {reason: sum(r == reason for r in skipped.values()) for reason in set(skipped.values())})

    english = annotations(args.packages, "en")
    for lang in LANGUAGES:
        cldr = english if lang == "en" else annotations(args.packages, lang)
        rows, missing = [], 0
        for name in catalog:
            entry = cldr.get(emojis[name]) or english.get(emojis[name]) or {}
            if emojis[name] not in cldr:
                missing += 1
            title = entry["tts"][0] if entry.get("tts") else name.replace("-", " ")
            keywords = sorted(set(entry.get("default", [])) or {title})
            rows.append(f"{name}\t{emojis[name]}\t{title}\t{'|'.join(keywords)}\n")
        write(f"{lang}.txt", "".join(rows))
        print(lang, "without its own annotation:", missing)


if __name__ == "__main__":
    main()
