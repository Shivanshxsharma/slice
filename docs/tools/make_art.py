"""Builds the README artwork from the rendered app screens.

1. Render the real screens (Robolectric, no phone needed):
   ./gradlew :app:testDebugUnitTest --tests '*ReadmeScreenshots*' -Pscreenshots=docs/screens/raw
2. python docs/tools/make_art.py
   → docs/screens/*.png (framed phones) and docs/art/*.png (hero, button, diagrams)

Uses Inter if found (set SLICE_FONT to a .ttf to choose), else Segoe UI.
"""
import math, os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RAW = os.path.join(ROOT, "docs", "screens", "raw")
SCREENS = os.path.join(ROOT, "docs", "screens")
ART = os.path.join(ROOT, "docs", "art")
os.makedirs(ART, exist_ok=True)

BG = (11, 11, 13)
SURF = (22, 22, 26)
SURF2 = (36, 36, 42)
LINE = (52, 52, 60)
TEXT = (244, 242, 238)
MUTED = (142, 140, 148)
ACC = (255, 77, 109)
ACC_DIM = (122, 38, 56)
WARN = (255, 193, 69)

FONT_PATH = next(p for p in [
    os.environ.get("SLICE_FONT", ""),
    r"D:\raasta\app\src\main\res\font\inter.ttf",
    r"C:\Windows\Fonts\segoeui.ttf",
] if p and os.path.exists(p))

S = 2  # draw at 2x, scale down at the end for smooth edges


def font(size, weight=400):
    f = ImageFont.truetype(FONT_PATH, int(size * S))
    try:
        f.set_variation_by_axes([min(32, max(14, size / 2)), weight])
    except Exception:
        if weight >= 600 and "segoeui" in FONT_PATH.lower():
            f = ImageFont.truetype(FONT_PATH.replace("segoeui.ttf", "segoeuib.ttf"), int(size * S))
    return f


class Art:
    def __init__(self, w, h, bg=BG):
        self.w, self.h = w, h
        self.im = Image.new("RGBA", (w * S, h * S), bg + (255,) if len(bg) == 3 else bg)
        self.d = ImageDraw.Draw(self.im)

    def rr(self, x0, y0, x1, y1, r, fill=None, outline=None, width=1):
        self.d.rounded_rectangle([x0 * S, y0 * S, x1 * S, y1 * S], r * S, fill=fill, outline=outline, width=int(width * S))

    def line(self, pts, fill, width, dash=None):
        pts = [(x * S, y * S) for x, y in pts]
        if not dash:
            self.d.line(pts, fill=fill, width=int(width * S), joint="curve")
            return
        (x0, y0), (x1, y1) = pts
        n = math.hypot(x1 - x0, y1 - y0)
        on, off = dash[0] * S, dash[1] * S
        t = 0
        while t < n:
            a, b = t / n, min(t + on, n) / n
            self.d.line([(x0 + (x1 - x0) * a, y0 + (y1 - y0) * a), (x0 + (x1 - x0) * b, y0 + (y1 - y0) * b)], fill=fill, width=int(width * S))
            t += on + off

    def circle(self, cx, cy, r, fill=None, outline=None, width=1):
        self.d.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=fill, outline=outline, width=int(width * S))

    def poly(self, pts, fill):
        self.d.polygon([(x * S, y * S) for x, y in pts], fill=fill)

    def text(self, x, y, s, f, fill, anchor="la"):
        self.d.text((x * S, y * S), s, font=f, fill=fill, anchor=anchor)
        return self.d.textlength(s, font=f) / S

    def tlen(self, s, f):
        return self.d.textlength(s, font=f) / S

    def arrow(self, x0, y, x1, col, width=3):
        self.line([(x0, y), (x1 - 4, y)], col, width)
        self.poly([(x1, y), (x1 - 14, y - 8), (x1 - 14, y + 8)], col)

    def paste(self, img, x, y):
        self.im.alpha_composite(img.resize((img.width * S, img.height * S), Image.LANCZOS) if S != 1 else img, (int(x * S), int(y * S)))

    def save(self, name, folder=ART):
        out = self.im.resize((self.w, self.h), Image.LANCZOS)
        out.save(os.path.join(folder, name), optimize=True)
        return out


def glow(art, cx, cy, radius, col, strength):
    layer = Image.new("RGBA", art.im.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse([(cx - radius) * S, (cy - radius) * S, (cx + radius) * S, (cy + radius) * S], fill=col + (strength,))
    art.im.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius * S * 0.45)))


# ------------------------------------------------------------------ phone frames

def phone(name, width):
    """A screen in a phone body: dark frame, thin edge, punch-hole camera, side buttons."""
    scr = Image.open(os.path.join(RAW, name + ".png")).convert("RGBA")
    sw, sh = scr.size
    pad, r_body, r_scr = 30, 150, 120
    W, H = sw + 2 * pad + 8, sh + 2 * pad
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # side buttons
    d.rounded_rectangle([W - 10, 520, W, 700], 4, fill=(40, 40, 46))
    d.rounded_rectangle([W - 10, 760, W, 880], 4, fill=(40, 40, 46))
    d.rounded_rectangle([0, 0, W - 8, H - 1], r_body, fill=(20, 20, 23), outline=(70, 70, 78), width=5)
    d.rounded_rectangle([12, 12, W - 20, H - 13], r_body - 12, outline=(8, 8, 9), width=10)
    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw - 1, sh - 1], r_scr, fill=255)
    im.paste(scr, (pad, pad), mask)
    cx, cy = pad + sw // 2, pad + 52
    d.ellipse([cx - 20, cy - 20, cx + 20, cy + 20], fill=(4, 4, 5))
    d.ellipse([cx - 8, cy - 8, cx + 8, cy + 8], fill=(22, 26, 38))
    h = round(H * width / W)
    return im.resize((width, h), Image.LANCZOS)


def with_shadow(img, blur=30, alpha=150, dy=24):
    pad = blur * 2
    out = Image.new("RGBA", (img.width + 2 * pad, img.height + 2 * pad + dy), (0, 0, 0, 0))
    sh = Image.new("RGBA", out.size, (0, 0, 0, 0))
    a = img.split()[3].point(lambda v: v * alpha // 255)
    sh.paste((0, 0, 0, 255), (pad, pad + dy), a)
    out.alpha_composite(sh.filter(ImageFilter.GaussianBlur(blur)))
    out.alpha_composite(img, (pad, pad))
    return out, pad


for n in ["start", "parts", "max-length", "splitting", "done", "parts-light"]:
    phone(n, 600).save(os.path.join(SCREENS, n + ".png"), optimize=True)


# ------------------------------------------------------------------ logo (same geometry as the launcher icon)

def logo(size, tile=(8, 8, 10), border=LINE):
    """The launcher icon as the launcher shows it: the centre 72 of its 108 viewport on a tile."""
    import sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import icon
    k = size * S / 72.0
    im = Image.new("RGBA", (size * S, size * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, size * S - 1, size * S - 1], int(size * S * 0.23), fill=tile, outline=border, width=max(1, int(size * S / 90)))
    P = lambda pts: [((x - 18) * k, (y - 18) * k) for x, y in pts]
    hexc = lambda h: tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))
    for poly, holes, band, accent in icon.pieces():
        d.polygon(P(poly), fill=hexc(icon.ACCENT if accent else icon.LIGHT))
        d.polygon(P(band), fill=hexc(icon.BAND_ACCENT if accent else icon.BAND_LIGHT))
        for x0, y0, x1, y1 in holes:
            (a, b), (c, e) = P([(x0, y0), (x1, y1)])
            d.rounded_rectangle([a, b, c, e], (c - a) * 0.22, fill=tile)
    return im.resize((size, size), Image.LANCZOS)


logo(256).save(os.path.join(ART, "logo.png"), optimize=True)


# ------------------------------------------------------------------ hero

hero = Art(2000, 1040)
glow(hero, 1500, 980, 620, ACC, 70)
glow(hero, 200, -100, 520, (90, 60, 160), 55)
hero.paste(logo(132), 120, 150)
f_name = font(168, 800)
w = hero.text(112, 470, "Slice", f_name, TEXT, "ls")
hero.text(112 + w + 4, 470, ".", f_name, ACC, "ls")
f_tag = font(54, 500)
hero.text(120, 560, "Cut any video into parts,", f_tag, TEXT)
hero.text(120, 628, "without losing a single frame.", f_tag, MUTED)
f_pill = font(30, 500)
msg = "Every frame is copied as it is. Nothing is re-compressed."
pw = hero.tlen(msg, f_pill) + 100
hero.rr(120, 748, 120 + pw, 828, 40, fill=SURF + (255,), outline=LINE + (255,), width=2)
hero.circle(160, 788, 9, fill=ACC)
hero.text(186, 788, msg, f_pill, TEXT, "lm")
f_small = font(30, 500)
x = 120
for i, s in enumerate(["Lossless", "Offline", "Free & open source"]):
    if i:
        hero.circle(x + 16, 916, 3, fill=MUTED)
        x += 32
    x += hero.text(x, 916, s, f_small, MUTED, "lm")

back_l, pad = with_shadow(phone("start", 372), blur=26, alpha=170)
back_r, _ = with_shadow(phone("done", 372), blur=26, alpha=170)
front, fpad = with_shadow(phone("parts", 420), blur=34, alpha=200)
hero.paste(back_l, 1140 - pad, 150 - pad)
hero.paste(back_r, 1590 - pad, 150 - pad)
hero.paste(front, 1340 - fpad, 70 - fpad)
hero.save("hero.png")


# ------------------------------------------------------------------ download button

btn = Art(820, 150, (0, 0, 0, 0))
btn.rr(0, 0, 820, 150, 75, fill=ACC)
cx, cy = 92, 75
btn.circle(cx, cy, 42, fill=(10, 10, 10))
btn.line([(cx, cy - 20), (cx, cy + 14)], ACC, 7)
btn.poly([(cx - 17, cy + 2), (cx + 17, cy + 2), (cx, cy + 20)], ACC)
btn.line([(cx - 18, cy + 27), (cx + 18, cy + 27)], ACC, 6)
btn.text(162, 62, "Download for Android", font(48, 700), (10, 10, 10), "lm")
btn.text(164, 108, "Free APK  ·  Android 10 or newer", font(26, 500), (40, 14, 22), "lm")
btn.save("download.png")


# ------------------------------------------------------------------ re-encode vs copy

cmp_ = Art(1800, 820)
f_h = font(40, 700)
f_b = font(28, 600)
f_r = font(28, 500)


def lane(y, title, sub, boxes, result, accent):
    cmp_.rr(60, y, 1740, y + 340, 36, fill=SURF + (255,), outline=(ACC if accent else LINE) + (255,), width=3 if accent else 2)
    w = cmp_.text(110, y + 64, title, f_h, TEXT if accent else MUTED, "lm")
    cmp_.text(110 + w + 22, y + 66, sub, f_r, MUTED, "lm")
    x = 110
    for i, (label, kind) in enumerate(boxes):
        bw = cmp_.tlen(label, f_b) + 70
        fill = {"io": SURF2, "bad": (58, 36, 30), "good": ACC_DIM}[kind]
        edge = {"io": LINE, "bad": (120, 70, 40), "good": ACC}[kind]
        cmp_.rr(x, y + 120, x + bw, y + 200, 22, fill=fill + (255,), outline=edge + (255,), width=2)
        cmp_.text(x + bw / 2, y + 160, label, f_b, TEXT, "mm")
        x += bw
        if i < len(boxes) - 1:
            cmp_.arrow(x + 22, y + 160, x + 82, MUTED if not accent else ACC, 3)
            x += 104
    col = ACC if accent else WARN
    xx = 110
    for i, r in enumerate(result):
        if i:
            cmp_.circle(xx + 20, y + 270, 4, fill=MUTED)
            xx += 40
        cmp_.circle(xx + 10, y + 270, 8, fill=col)
        xx += 30
        xx += cmp_.text(xx, y + 270, r, f_r, TEXT, "lm")


lane(40, "Most video editors", "trim by re-encoding", [("Your video", "io"), ("Decode every frame", "bad"), ("Compress it again", "bad"), ("Parts", "io")],
     ["Detail gets softer", "Slow on long videos", "Heavy on battery"], False)
lane(440, "Slice", "trims by copying", [("Your video", "io"), ("Copy the frames exactly as they are", "good"), ("Parts", "io")],
     ["Identical frames", "As fast as copying a file", "Light on battery"], True)
cmp_.save("copy-vs-reencode.png")


# ------------------------------------------------------------------ keyframes diagram

kf = Art(1800, 700)
N, fw, gap, x0, y0, fh = 46, 30, 6, 75, 250, 120
keys = [0, 6, 13, 19, 25, 32, 38, 44]
total = N * (fw + gap) - gap
X = lambda i: x0 + i * (fw + gap)
for i in range(N):
    k = i in keys
    kf.rr(X(i), y0, X(i) + fw, y0 + fh, 6, fill=((236, 234, 229) if k else SURF2) + (255,))
    if k:
        kf.text(X(i) + fw / 2, y0 + fh / 2, "K", font(20, 800), (20, 20, 24), "mm")
f_lab = font(28, 600)
f_note = font(26, 500)
ideal = [total / 3, 2 * total / 3]
cuts = []
for t in ideal:
    xi = x0 + t
    kf.line([(xi, y0 - 70), (xi, y0 + fh + 20)], MUTED, 3, dash=(10, 8))
    best = min(keys, key=lambda k: abs(X(k) - 3 - xi))
    cuts.append(best)
    xc = X(best) - 3
    kf.line([(xc, y0 - 30), (xc, y0 + fh + 40)], ACC, 6)
    if abs(xc - xi) > 4:
        kf.line([(xi, y0 - 40), (xc + (8 if xc > xi else -8), y0 - 40)], ACC, 3)
        kf.poly([(xc, y0 - 40), (xc - 12 * (1 if xc > xi else -1), y0 - 48), (xc - 12 * (1 if xc > xi else -1), y0 - 32)], ACC)
kf.line([(x0 + 0, 120), (x0 + 40, 120)], MUTED, 3, dash=(10, 8))
kf.text(x0 + 56, 120, "Where an exact split would cut", f_note, MUTED, "lm")
kf.line([(x0 + 620, 120), (x0 + 660, 120)], ACC, 6)
kf.text(x0 + 676, 120, "Where Slice cuts: the nearest keyframe", f_note, TEXT, "lm")
bounds = [0] + cuts + [N]
fps_frames = 46 / 3  # pretend the strip is 3 seconds per part
for p in range(3):
    a, b = X(bounds[p]), X(bounds[p + 1]) - gap
    kf.rr(a, y0 + fh + 70, b, y0 + fh + 140, 18, fill=(ACC if p % 2 == 0 else ACC_DIM) + (255,))
    kf.text((a + b) / 2, y0 + fh + 105, f"Part {p + 1}  ·  {bounds[p + 1] - bounds[p]} frames", f_lab, (10, 10, 10) if p % 2 == 0 else TEXT, "mm")
ly = 625
kf.rr(x0, ly - 18, x0 + 22, ly + 18, 5, fill=(236, 234, 229) + (255,))
kf.text(x0 + 40, ly, "Keyframe: a complete picture", f_note, TEXT, "lm")
kf.rr(x0 + 560, ly - 18, x0 + 582, ly + 18, 5, fill=SURF2 + (255,))
kf.text(x0 + 600, ly, "In-between frame: stores only what changed since the last one", f_note, MUTED, "lm")
kf.save("keyframes.png")

print("art written to", ART, "and", SCREENS)
