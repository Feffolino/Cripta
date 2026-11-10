"""SVG bodies of Fluent Emoji Flat (Iconify) to coloured path parts, for scripts/tagicons.py."""
import math
import re
import xml.etree.ElementTree as ET

NS = "http://www.w3.org/2000/svg"
XL = "http://www.w3.org/1999/xlink"
FULL_CLIP = re.compile(r"^M0 ?0h32v32H0z$")


class Unsupported(Exception):
    pass


def fmt(x):
    s = f"{x:.3f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


# ---- affine -----------------------------------------------------------------
def mul(m, n):
    a, b, c, d, e, f = m
    A, B, C, D, E, F = n
    return (a * A + c * B, b * A + d * B, a * C + c * D, b * C + d * D, a * E + c * F + e, b * E + d * F + f)


IDENT = (1, 0, 0, 1, 0, 0)


def parse_transform(text):
    m = IDENT
    for name, args in re.findall(r"(\w+)\(([^)]*)\)", text or ""):
        v = [float(x) for x in re.findall(r"-?[\d.]+(?:e-?\d+)?", args)]
        if name == "translate":
            t = (1, 0, 0, 1, v[0], v[1] if len(v) > 1 else 0)
        elif name == "scale":
            t = (v[0], 0, 0, v[1] if len(v) > 1 else v[0], 0, 0)
        elif name == "rotate":
            r = math.radians(v[0])
            co, si = math.cos(r), math.sin(r)
            t = (co, si, -si, co, 0, 0)
            if len(v) == 3:
                t = mul(mul((1, 0, 0, 1, v[1], v[2]), t), (1, 0, 0, 1, -v[1], -v[2]))
        elif name == "matrix":
            t = tuple(v)
        else:
            raise Unsupported("transform " + name)
        m = mul(m, t)
    return m


def apply(m, x, y):
    return m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5]


# ---- path data --------------------------------------------------------------
TOKEN = re.compile(r"([MmLlHhVvCcSsQqTtAaZz])|(-?(?:\d+\.?\d*|\.\d+)(?:e[-+]?\d+)?)")
ARGS = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


def parse_path(d):
    """Absolute segments [(cmd, [numbers])] with cmd in M L C Q A Z."""
    pos = [m for m in TOKEN.finditer(d)]
    i = 0
    segs = []
    cx = cy = sx = sy = 0.0
    last_c = last_q = None
    cmd = None
    n = len(pos)

    def num():
        nonlocal i
        t = pos[i]
        if t.group(2) is None:
            raise Unsupported("path syntax")
        i += 1
        return float(t.group(2))

    def flag():
        # An arc flag is one digit; it can be glued to the next number ("011.5").
        nonlocal i
        t = pos[i]
        s = t.group(2)
        if s is None:
            raise Unsupported("path syntax")
        if s[0] in "01" and (len(s) == 1 or s[1] != "."):
            rest = s[1:]
            if rest:
                pos[i] = _Tok(rest)
            else:
                i += 1
            return float(s[0])
        raise Unsupported("arc flag")

    while i < n:
        t = pos[i]
        if t.group(1):
            cmd = t.group(1)
            i += 1
            if cmd in "Zz":
                segs.append(("Z", []))
                cx, cy = sx, sy
                last_c = last_q = None
                continue
        elif cmd is None or cmd in "Zz":
            raise Unsupported("path syntax")
        rel = cmd.islower()
        c = cmd.upper()
        if c == "M":
            x, y = num(), num()
            if rel:
                x, y = x + cx, y + cy
            segs.append(("M", [x, y]))
            cx, cy = sx, sy = x, y
            cmd = "l" if rel else "L"
            last_c = last_q = None
        elif c == "L":
            x, y = num(), num()
            if rel:
                x, y = x + cx, y + cy
            segs.append(("L", [x, y]))
            cx, cy = x, y
            last_c = last_q = None
        elif c == "H":
            x = num() + (cx if rel else 0)
            segs.append(("L", [x, cy]))
            cx = x
            last_c = last_q = None
        elif c == "V":
            y = num() + (cy if rel else 0)
            segs.append(("L", [cx, y]))
            cy = y
            last_c = last_q = None
        elif c == "C":
            v = [num() for _ in range(6)]
            if rel:
                v = [v[k] + (cx if k % 2 == 0 else cy) for k in range(6)]
            segs.append(("C", v))
            last_c = (v[2], v[3])
            last_q = None
            cx, cy = v[4], v[5]
        elif c == "S":
            v = [num() for _ in range(4)]
            if rel:
                v = [v[k] + (cx if k % 2 == 0 else cy) for k in range(4)]
            x1, y1 = (2 * cx - last_c[0], 2 * cy - last_c[1]) if last_c else (cx, cy)
            segs.append(("C", [x1, y1] + v))
            last_c = (v[0], v[1])
            last_q = None
            cx, cy = v[2], v[3]
        elif c == "Q":
            v = [num() for _ in range(4)]
            if rel:
                v = [v[k] + (cx if k % 2 == 0 else cy) for k in range(4)]
            segs.append(("Q", v))
            last_q = (v[0], v[1])
            last_c = None
            cx, cy = v[2], v[3]
        elif c == "T":
            x, y = num(), num()
            if rel:
                x, y = x + cx, y + cy
            qx, qy = (2 * cx - last_q[0], 2 * cy - last_q[1]) if last_q else (cx, cy)
            segs.append(("Q", [qx, qy, x, y]))
            last_q = (qx, qy)
            last_c = None
            cx, cy = x, y
        elif c == "A":
            rx, ry, rot = num(), num(), num()
            fa, fs = flag(), flag()
            x, y = num(), num()
            if rel:
                x, y = x + cx, y + cy
            segs.append(("A", [rx, ry, rot, fa, fs, x, y]))
            cx, cy = x, y
            last_c = last_q = None
        else:
            raise Unsupported("path command " + cmd)
    return segs


class _Tok:
    def __init__(self, s):
        self.s = s

    def group(self, k):
        return self.s if k == 2 else None


def write_path(segs):
    out = []
    for c, v in segs:
        if c == "Z":
            out.append("Z")
        elif c == "A":
            out.append("A" + " ".join([fmt(v[0]), fmt(v[1]), fmt(v[2]), str(int(v[3])), str(int(v[4])), fmt(v[5]), fmt(v[6])]))
        else:
            out.append(c + " ".join(fmt(x) for x in v))
    return "".join(out)


def transform_path(segs, m):
    a, b, c, d = m[:4]
    det = a * d - b * c
    s = math.sqrt(abs(det))
    if s == 0 or abs(a * a + b * b - s * s) > 1e-6 * max(1, s * s) or abs(a * c + b * d) > 1e-6 * max(1, s * s):
        raise Unsupported("non-similarity transform")
    angle = math.degrees(math.atan2(b, a))
    out = []
    for cmd, v in segs:
        if cmd == "A":
            x, y = apply(m, v[5], v[6])
            out.append((cmd, [v[0] * s, v[1] * s, (v[2] + angle) % 360, v[3], v[4] if det > 0 else 1 - v[4], x, y]))
        elif cmd == "Z":
            out.append((cmd, v))
        else:
            pts = []
            for k in range(0, len(v), 2):
                pts.extend(apply(m, v[k], v[k + 1]))
            out.append((cmd, pts))
    return out


# ---- shapes -----------------------------------------------------------------
def ellipse(cx, cy, rx, ry):
    return [("M", [cx - rx, cy]), ("A", [rx, ry, 0, 1, 0, cx + rx, cy]), ("A", [rx, ry, 0, 1, 0, cx - rx, cy]), ("Z", [])]


def rect(x, y, w, h, rx, ry):
    if not rx and not ry:
        return [("M", [x, y]), ("L", [x + w, y]), ("L", [x + w, y + h]), ("L", [x, y + h]), ("Z", [])]
    rx = rx or ry
    ry = ry or rx
    rx, ry = min(rx, w / 2), min(ry, h / 2)
    return [
        ("M", [x + rx, y]), ("L", [x + w - rx, y]), ("A", [rx, ry, 0, 0, 1, x + w, y + ry]),
        ("L", [x + w, y + h - ry]), ("A", [rx, ry, 0, 0, 1, x + w - rx, y + h]),
        ("L", [x + rx, y + h]), ("A", [rx, ry, 0, 0, 1, x, y + h - ry]),
        ("L", [x, y + ry]), ("A", [rx, ry, 0, 0, 1, x + rx, y]), ("Z", []),
    ]


# ---- colours ----------------------------------------------------------------
NAMED = {"white": "#ffffff", "black": "#000000"}


def hex6(c):
    c = NAMED.get(c, c)
    if re.fullmatch(r"#[0-9a-fA-F]{6}", c):
        return c[1:].lower()
    if re.fullmatch(r"#[0-9a-fA-F]{3}", c):
        return "".join(ch * 2 for ch in c[1:]).lower()
    raise Unsupported("colour " + c)


def mix(colors):
    chans = [[int(c[i:i + 2], 16) for i in (0, 2, 4)] for c in colors]
    return "".join(f"{round(sum(ch[k] for ch in chans) / len(chans)):02x}" for k in range(3))


def with_alpha(color, opacity):
    a = round(max(0.0, min(1.0, opacity)) * 255)
    return color if a == 255 else color + f"{a:02x}"


# ---- walk -------------------------------------------------------------------
def tag(el):
    return el.tag.split("}")[-1]


def convert(body):
    """Return [(kind, colour, extra, d)] kind f / e / s. Raises Unsupported."""
    root = ET.fromstring(f'<svg xmlns="{NS}" xmlns:xlink="{XL}">{body}</svg>')
    ids = {}
    gradients = {}
    for el in root.iter():
        if el.get("id"):
            ids[el.get("id")] = el
        if tag(el) in ("linearGradient", "radialGradient"):
            stops = [hex6(s.get("stop-color", "#000000")) for s in el if tag(s) == "stop"]
            if not stops:
                raise Unsupported("gradient")
            gradients[el.get("id")] = mix([stops[0], stops[-1]] if len(stops) > 1 else stops)
    parts = []

    def clip_ok(ref):
        el = ids.get(ref)
        kids = [k for k in el] if el is not None else []
        if len(kids) == 1 and tag(kids[0]) == "path" and FULL_CLIP.match(kids[0].get("d", "")):
            return True
        raise Unsupported("clip-path")

    def paint(value):
        if value in (None, "none"):
            return None
        m = re.fullmatch(r"url\(#([^)]+)\)", value)
        if m:
            if m.group(1) not in gradients:
                raise Unsupported("fill ref")
            return gradients[m.group(1)]
        return hex6(value)

    def walk(el, st):
        t = tag(el)
        if t in ("defs", "linearGradient", "radialGradient", "clipPath", "stop"):
            return
        if el.get("filter") or el.get("mask"):
            raise Unsupported("filter/mask")
        st = dict(st)
        if el.get("clip-path"):
            clip_ok(re.fullmatch(r"url\(#([^)]+)\)", el.get("clip-path")).group(1))
        for key in ("fill", "fill-rule", "stroke", "stroke-width", "stroke-linecap", "stroke-miterlimit"):
            if el.get(key) is not None:
                st[key] = el.get(key)
        if el.get("opacity") is not None:
            st["opacity"] = st["opacity"] * float(el.get("opacity"))
        if el.get("transform"):
            st["m"] = mul(st["m"], parse_transform(el.get("transform")))
        if t == "g":
            for k in el:
                walk(k, st)
            return
        if t == "use":
            ref = (el.get("href") or el.get(f"{{{XL}}}href") or "")[1:]
            target = ids.get(ref)
            if target is None:
                raise Unsupported("use")
            st["m"] = mul(st["m"], (1, 0, 0, 1, float(el.get("x", 0)), float(el.get("y", 0))))
            walk(target, st)
            return
        if t == "path":
            segs = parse_path(el.get("d", ""))
            raw = el.get("d", "")
        elif t == "ellipse":
            segs = ellipse(*(float(el.get(k, 0)) for k in ("cx", "cy", "rx", "ry")))
            raw = None
        elif t == "circle":
            r = float(el.get("r"))
            segs = ellipse(float(el.get("cx", 0)), float(el.get("cy", 0)), r, r)
            raw = None
        elif t == "rect":
            segs = rect(float(el.get("x", 0)), float(el.get("y", 0)), float(el.get("width")), float(el.get("height")),
                        float(el.get("rx", 0)), float(el.get("ry", 0)))
            raw = None
        else:
            raise Unsupported("element " + t)
        if st["m"] != IDENT:
            segs = transform_path(segs, st["m"])
            raw = None
        d = raw if raw is not None else write_path(segs)
        d = " ".join(d.split())
        fill = paint(st.get("fill", "#000000"))
        if fill:
            kind = "e" if st.get("fill-rule") == "evenodd" else "f"
            parts.append((kind, with_alpha(fill, st["opacity"]), None, d))
        stroke = paint(st.get("stroke"))
        if stroke:
            scale = math.sqrt(abs(st["m"][0] * st["m"][3] - st["m"][1] * st["m"][2]))
            width = float(st.get("stroke-width", 1)) * scale
            cap = {"round": "r", "square": "s"}.get(st.get("stroke-linecap"), "b")
            parts.append(("s", with_alpha(stroke, st["opacity"]), f"{fmt(width)}:{cap}", d))

    base = {"fill": "#000000", "opacity": 1.0, "m": IDENT}
    for el in root:
        walk(el, base)
    if not parts:
        raise Unsupported("empty")
    return parts


def spec(parts):
    out = []
    for kind, color, extra, d in parts:
        out.append(f"{kind}:{color}:{extra}:{d}" if extra else f"{kind}:{color}:{d}")
    return "|".join(out)


def svg_of(parts, size=32, viewbox="0 0 32 32"):
    els = []
    for kind, color, extra, d in parts:
        rgb, alpha = "#" + color[:6], (int(color[6:], 16) / 255 if len(color) > 6 else 1)
        if kind == "s":
            w, cap = extra.split(":")
            els.append(f'<path d="{d}" fill="none" stroke="{rgb}" stroke-opacity="{alpha}" stroke-width="{w}" '
                       f'stroke-linecap="{ {"r": "round", "s": "square", "b": "butt"}[cap] }"/>')
        else:
            rule = "evenodd" if kind == "e" else "nonzero"
            els.append(f'<path d="{d}" fill="{rgb}" fill-opacity="{alpha}" fill-rule="{rule}"/>')
    return f'<svg xmlns="{NS}" width="{size}" height="{size}" viewBox="{viewbox}">{"".join(els)}</svg>'
