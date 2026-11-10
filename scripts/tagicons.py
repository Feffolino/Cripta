"""Generate the Android tag icon assets from verified, unpacked npm packages."""

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET


HERE = Path(__file__).resolve().parent
ASSETS = HERE.parent / "app/src/main/assets/tagicons"
KEEP_FILE = HERE / "tagicons_keep.txt"
TABLER_VERSION = "3.49.0"
LANGUAGES = "en it ar de es fr hi ja ko pt ru zh".split()
EXCLUDED_CATEGORIES = set("Arrows Brand Letters Numbers Text Charts Design".split())
EXCLUDED_NAMES = set("""
    ad ad-circle adjustments adjustments-horizontal affiliate analyze app-window apps aspect-ratio
    assembly asset backspace binary-tree binary-tree-2 blob bounce-left bounce-right capture
    category charging-pile circle-dot circle-rectangle circles click cloud-data-connection
    code-circle code-circle-2 columns-1 columns-2 columns-3 contrast contrast-2 copy current-location
    dashboard database dialpad directions dots dots-vertical download edit exchange exposure
    external-link favicon filter filters function gauge gps graph help icons id lego link list
    list-details live-view menu-2 michelin-star mickey navigation pacman picture-in-picture
    picture-in-picture-top playlist plus point pointer quote relation-many-to-many relation-one-to-many
    relation-one-to-one replace search section send settings sitemap squares stack stack-2 stack-3
    table template tilt-shift timeline-event transform triangle-square-circle versions x keyframes
    clipboard-data clipboard-list clipboard-smile clipboard-text clipboard-typography
    file-analytics file-code file-code-2 file-cv file-delta file-diff file-digit file-dots
    file-download file-function file-info file-lambda file-neutral file-percent file-phone file-power
    file-rss file-sad file-scissors file-settings file-signal file-smile file-time file-typography
    file-unknown file-upload file-vector list-check live-photo report-analytics square-asterisk
    alert-circle alert-hexagon alert-octagon alert-square alert-square-rounded
    alarm-snooze bell-z bubble-text device-unknown direction-sign eye-table exclamation-circle
    presentation-analytics panorama-horizontal panorama-vertical square-dot message-report
    device-imac device-ipad device-vision-pro building-broadcast-tower
""".split())
EXCLUDED_PATTERN = re.compile(
    r"(?:^|-)(?:arrow|arrows|caret|chevron|chevrons|cursor|wifi|signal|select|selection|logo)(?:-|$)"
    r"|^(?:align|battery|brightness|crop|grid|layout|keyframe|lasso|player|sort|swipe|toggle|"
    r"transition|xbox|zoom|help|info)(?:-|$)|-(?:off|plus|minus|x|check)$"
    r"|^(?:play-card-(?:\d+|[ajkq])|square-f\d|badge-(?:3d|4k|8k|ad|ar|cc|hd|sd|tm|vo|vr|wc))$"
)
# A few drawings in UI-oriented categories have recognizable standalone subjects.
KEEP = set("""
    artboard ballpen blade briefcase briefcase-2 bulb candle confetti crown droplet feather
    guitar-pick key magnet paint palette pencil writing writing-sign
""".split())

# Explicit matches take precedence over an exact match to an English CLDR short name.
# A dash blocks false matches. Additional emoji supply search aliases and migration inputs.
EMOJI = dict(line.split(maxsplit=1) for line in """
    accessible ♿
    aerial-lift 🚡
    air-balloon -
    alarm ⏰
    alien 👽
    apple 🍎
    award 🏅
    ball-bowling 🎳
    baby-carriage -
    ballpen 🖊
    bandage 🩹
    beer 🍺 🍻
    bell 🔔
    bike 🚲
    bolt ⚡
    book 📕
    bowl-chopsticks 🥢
    bowl-spoon 🥣
    bulb 💡
    campfire -
    car 🚗
    carambola -
    caravan -
    chef-hat -
    cherry 🍒
    christmas-tree 🎄
    clover 🍀
    clubs ♣
    confetti 🎊 🎉
    cross ✝
    device-cctv -
    device-desktop 🖥
    device-floppy 💾
    device-gamepad 🎮
    device-mobile 📱
    device-speaker 🔊
    device-tv 📺
    device-watch ⌚
    diamond 💎
    diamonds ♦
    dice 🎲
    disc 💿
    droplet 💧
    egg-fried 🍳
    eyeglass 👓
    face-mask 😷
    flag 🏁
    flame 🔥
    folder 📁
    folder-open 📂
    forbid 🚫
    gift 🎁
    glass-full 🍷
    globe 🌐 🌍 🌎 🌏
    golf ⛳
    headphones 🎧
    heart ❤
    heart-broken 💔
    home 🏠 🏡
    hospital-circle 🏥
    hourglass ⌛ ⏳
    jewish-star ✡
    lock 🔒 🔐
    mail ✉
    map-pin 📍
    medical-cross -
    meteor ☄
    mood-angry 😠 😡
    mood-crazy-happy 😂 🤣
    mood-happy 😀 😁
    mood-kid 👶
    mood-sad 😢 😭
    mood-smile 🙂 😊
    moon 🌙
    motorbike 🏍
    mouse 🖱
    mug ☕
    palette 🎨
    paw 🐾 🐱 🐈 🐈‍⬛ 🐶 🐕 🦮 🐩 🐕‍🦺
    photo 🖼
    pig 🐷 🐖
    pin 📌
    plane ✈
    plane-arrival 🛬
    plane-departure 🛫
    play-card 🃏
    poo 💩
    puzzle 🧩
    radioactive ☢
    rosette 🏵
    scale ⚖
    scuba-diving-tank -
    shirt 👕
    spade ♠
    thumb-down 👎
    thumb-up 👍
    tools-kitchen-2 🍴
    ufo 🛸
    user 👤
    video 📹 🎥
    world 🗺
    writing ✍
""".strip().splitlines())


def allowed(name, category):
    return name in KEEP or (
        category not in EXCLUDED_CATEGORIES
        and name not in EXCLUDED_NAMES
        and not EXCLUDED_PATTERN.search(name)
    )


def glyph(svg):
    root = ET.parse(svg).getroot()
    if root.get("viewBox") != "0 0 24 24":
        raise ValueError(f"Unexpected viewport in {svg}")
    paths = []
    for shape in root:
        if shape.get("fill") == "none":
            continue
        if not shape.tag.endswith("}path") or shape.get("stroke") or shape.get("fill") not in (None, "currentColor"):
            raise ValueError(f"Unexpected shape in {svg}: {shape.attrib}")
        prefix = "e:" if shape.get("fill-rule") == "evenodd" else "f:"
        paths.append(prefix + " ".join(shape.attrib["d"].split()))
    if not paths:
        raise ValueError(f"No visible paths in {svg}")
    return "|".join(paths)


def annotations(root, lang):
    result = {}
    for package, folder in (("cldr-annotations-derived-full", "annotationsDerived"),
                            ("cldr-annotations-full", "annotations")):
        path = root / package / "package" / folder / lang / "annotations.json"
        data = json.loads(path.read_text(encoding="utf-8"))[folder]["annotations"]
        result.update({emoji.replace("\ufe0f", ""): entry for emoji, entry in data.items()})
    return result


def normalize(text):
    return "-".join(re.findall(r"[a-z0-9]+", text.lower()))


def write(name, contents):
    (ASSETS / name).write_text(contents, encoding="utf-8", newline="\n")


def selection(path, available):
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    if (data.get("version"), data.get("source"), data.get("tablerVersion"), data.get("style")) != (
        1, "@tabler/icons", TABLER_VERSION, "filled",
    ):
        raise ValueError("The selection must come from the Tabler Filled picker for this version")
    keep, exclude = data["keep"], data["exclude"]
    if not isinstance(keep, list) or not isinstance(exclude, list):
        raise ValueError("Expected keep and exclude lists")
    kept, excluded = set(keep), set(exclude)
    if len(kept) != len(keep) or len(excluded) != len(exclude) or kept & excluded or kept | excluded != available:
        raise ValueError("The selection must partition the entire Filled catalog without duplicates")
    return kept


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("packages", type=Path, help="Directory containing icons and both CLDR packages")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--selection", type=Path, help="Import a JSON export from the manual icon picker")
    mode.add_argument("--automatic", action="store_true", help="Preview the original automatic filter")
    args = parser.parse_args()
    tabler = args.packages / "icons/package"
    if json.loads((tabler / "package.json").read_text(encoding="utf-8"))["version"] != TABLER_VERSION:
        raise ValueError(f"Expected @tabler/icons {TABLER_VERSION}")
    metadata = json.loads((tabler / "icons.json").read_text(encoding="utf-8"))
    files = sorted((tabler / "icons/filled").glob("*.svg"))
    available = {svg.stem for svg in files}
    if args.selection:
        keep = selection(args.selection, available)
    elif args.automatic:
        keep = {name for name in available if allowed(name, metadata[name]["category"])}
        print(f"{len(keep)} kept, {len(files) - len(keep)} excluded by the automatic filter")
        return
    else:
        keep = set(KEEP_FILE.read_text(encoding="utf-8").splitlines())
    if not keep or keep - available:
        raise ValueError("The selection must contain only known Filled icons")
    icons = {svg.stem: glyph(svg) for svg in files if svg.stem in keep}
    catalog = "".join(f"{name}\t{paths}\n" for name, paths in icons.items())
    digest = hashlib.sha256(catalog.encode()).hexdigest()
    write("tabler-filled.txt", f"{digest}\n{catalog}")

    english = annotations(args.packages, "en")
    exact = {normalize(entry["tts"][0]): emoji for emoji, entry in english.items() if entry.get("tts")}
    emoji_for = {name: EMOJI.get(name, exact.get(name, "")).split() for name in icons}
    translations = {}
    for row in (HERE / "tagicons_words.txt").read_text(encoding="utf-8").splitlines():
        if row and not row.startswith("#"):
            word, *cells = row.split("|")
            if len(cells) != len(LANGUAGES) - 1:
                raise ValueError(f"Wrong column count for {word}")
            translations[word] = [word.replace("-", " "), *cells]
    used_words = set()

    for index, lang in enumerate(LANGUAGES):
        cldr = english if lang == "en" else annotations(args.packages, lang)
        rows, sources = [], Counter()
        for name in icons:
            emojis = [emoji for emoji in emoji_for[name] if emoji != "-"]
            entry = cldr.get(emojis[0], {}) if emojis else {}
            keywords = set(entry.get("default", []))
            for alias in emojis[1:]:
                keywords.update(cldr.get(alias, {}).get("tts", []))
                keywords.update(cldr.get(alias, {}).get("default", []))
            if lang == "en":
                keywords.update(map(str, metadata[name]["tags"]))
            if entry.get("tts"):
                title = entry["tts"][0]
                sources["CLDR"] += 1
            else:
                parts = [name] if name in translations else name.split("-")
                used_words.update(parts)
                title = " ".join(translations.get(part, [part] * len(LANGUAGES))[index]
                                 for part in parts).strip()
                title = title or name.replace("-", " ")
                sources["dictionary"] += 1
            rows.append(f"{name}\t{' '.join(emojis)}\t{title}\t{'|'.join(sorted(keywords or {title}))}\n")
        write(f"{lang}.txt", "".join(rows))
        print(lang, dict(sources))
    print(f"{len(icons)} kept, {len(files) - len(icons)} excluded")
    print("Missing dictionary entries:", " ".join(sorted(used_words - translations.keys())))
    if args.selection:
        KEEP_FILE.write_text("".join(f"{name}\n" for name in sorted(keep)), encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
