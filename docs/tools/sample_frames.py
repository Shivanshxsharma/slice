"""Paints the sample "video" used in the README screenshots: a flat-art sunset drive.

A wide panorama (afternoon on the left, dusk on the right) is cut into 16:9 frames, the way
a camera panning along a coast would see it. Output: app/src/test/resources/readme/*.png
Run: python docs/tools/sample_frames.py
"""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app", "src", "test", "resources", "readme")
W, H = 7200, 1080
HORIZON = 700
rng = random.Random(7)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# sky: top and horizon colours change from afternoon (x=0) to dusk (x=W)
TOP = [hexc("#3B6FB6"), hexc("#3A3F8F"), hexc("#2A1F5C")]
HOR = [hexc("#F7C9A0"), hexc("#FF9E7A"), hexc("#FF6B6B")]


def sky_cols(t):
    seg = min(int(t * 2), 1)
    u = t * 2 - seg
    return lerp(TOP[seg], TOP[seg + 1], u), lerp(HOR[seg], HOR[seg + 1], u)


img = np.zeros((H, W, 3), dtype=np.float32)
xs = np.linspace(0, 1, W)
top = np.array([sky_cols(t)[0] for t in xs], dtype=np.float32)
hor = np.array([sky_cols(t)[1] for t in xs], dtype=np.float32)
for y in range(HORIZON):
    v = (y / HORIZON) ** 1.6
    img[y] = top * (1 - v) + hor * v

# sun with glow
SUN = (4700, 560, 120)
yy, xx = np.mgrid[0:H, 0:W]
d = np.sqrt((xx - SUN[0]) ** 2 + ((yy - SUN[1]) * 1.0) ** 2)
glow = np.clip(1 - d / 1400, 0, 1) ** 2.2
img += glow[..., None] * np.array([90, 55, 25], dtype=np.float32)
# water: mirrored, darker sky (mirrors the glow, not the disc)
for y in range(HORIZON, H):
    m = 2 * HORIZON - y
    m = max(0, min(HORIZON - 1, m))
    img[y] = img[m] * 0.62 + np.array([10, 18, 40], dtype=np.float32) * 0.38
disc = np.clip((SUN[2] - d) / 3, 0, 1) * (yy < HORIZON)
img = img * (1 - disc[..., None]) + disc[..., None] * np.array(hexc("#FFE7B0"), dtype=np.float32)
img = np.clip(img, 0, 255).astype(np.uint8)
im = Image.fromarray(img, "RGB")
dr = ImageDraw.Draw(im)

# sun path on water
for i in range(46):
    y = HORIZON + 8 + i * 8
    w = 170 - i * 2.2 + rng.uniform(-30, 30)
    x = SUN[0] + rng.uniform(-25, 25)
    dr.rounded_rectangle([x - w / 2, y, x + w / 2, y + 3], 2, fill=lerp(hexc("#FFD9A0"), hexc("#FF8C6E"), i / 46))


def ridge(base, amp, seed, freq):
    r = random.Random(seed)
    phases = [r.uniform(0, 6.28) for _ in range(5)]
    pts = []
    for x in range(0, W + 40, 40):
        y = base
        for k, ph in enumerate(phases):
            y -= amp / (k + 1) * math.sin(x * freq * (k + 1.7) + ph)
        pts.append((x, min(y, HORIZON - 4)))
    return pts


def mountain(pts, col_left, col_right, bottom):
    # draw in vertical strips so the colour drifts from afternoon to dusk
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    ld.polygon(pts + [(W, bottom), (0, bottom)], fill=(255, 255, 255, 255))
    mask = layer.split()[3]
    grad = np.zeros((H, W, 3), dtype=np.uint8)
    for i, t in enumerate(np.linspace(0, 1, W)):
        grad[:, i] = lerp(col_left, col_right, t)
    im.paste(Image.fromarray(grad, "RGB"), (0, 0), mask)


mountain(ridge(560, 120, 1, 0.0011), hexc("#8C8FC4"), hexc("#B0688F"), HORIZON + 2)
mountain(ridge(625, 80, 2, 0.0019), hexc("#55598F"), hexc("#6E3A6E"), HORIZON + 2)

# near headland on the left, dropping into the sea before the sunset; pines on top
def near_y(x):
    hill = 690 - 150 * math.exp(-((x - 1400) / 1200) ** 2) - 28 * math.sin(x / 210)
    fall = 1 / (1 + math.exp(-(x - 3300) / 220))  # 0 on the left, 1 past x≈3700
    return hill * (1 - fall) + 960 * fall


near = [(x, near_y(x)) for x in range(0, W + 40, 40)]
layer = Image.new("L", (W, H), 0)
ImageDraw.Draw(layer).polygon(near + [(W, H), (0, H)], fill=255)
shade = np.zeros((H, W, 3), dtype=np.float32)
left, right = np.array(hexc("#2B3163"), np.float32), np.array(hexc("#331C45"), np.float32)
deep = np.array(hexc("#12142C"), np.float32)
for y in range(H):
    v = np.clip((y - 520) / 420, 0, 1)
    row = left[None, :] * (1 - xs[:, None]) + right[None, :] * xs[:, None]
    shade[y] = row * (1 - v) + deep * v
im.paste(Image.fromarray(shade.astype(np.uint8), "RGB"), (0, 0), layer)
dr = ImageDraw.Draw(im)
for row, (count, lo, hi, dy, col) in enumerate([
    (120, 40, 110, 0, "#181C3C"),
    (70, 30, 70, 70, "#14172F"),
    (50, 26, 56, 140, "#101226"),
]):
    for _ in range(count):
        x = rng.uniform(0, 3300)
        gy = near_y(x) + dy + rng.uniform(-6, 6)
        h = rng.uniform(lo, hi)
        dr.polygon([(x, gy - h), (x - h * 0.28, gy + 4), (x + h * 0.28, gy + 4)], fill=hexc(col))

# coast road: a band at the bottom with dashed centre line and a car's lights
road_top = 930
dr.polygon([(0, road_top), (W, road_top - 40), (W, H), (0, H)], fill=hexc("#14152A"))
for x in range(0, W, 120):
    y = road_top + 70 - 40 * x / W
    dr.rounded_rectangle([x, y, x + 60, y + 6], 3, fill=hexc("#F2D49B"))
for cx in (1100, 3900, 6100):
    y = road_top + 30 - 40 * cx / W
    dr.rounded_rectangle([cx - 70, y - 26, cx + 70, y + 14], 14, fill=hexc("#0C0D1C"))
    dr.ellipse([cx + 50, y - 10, cx + 72, y + 6], fill=hexc("#FFF2C2"))
    dr.ellipse([cx - 72, y - 10, cx - 56, y + 4], fill=hexc("#FF4D6D"))

# birds
for _ in range(14):
    x, y, s = rng.uniform(2500, 6800), rng.uniform(180, 420), rng.uniform(10, 20)
    dr.line([(x - s, y - s * 0.4), (x, y), (x + s, y - s * 0.4)], fill=hexc("#2A2350"), width=4, joint="curve")

im = im.filter(ImageFilter.GaussianBlur(0.6))

os.makedirs(OUT, exist_ok=True)


def frame(t, w=480):
    """Crop at position t (0..1 of the drive) as a 16:9 frame, scaled to width w."""
    fw = 1920
    x0 = int(t * (W - fw))
    return im.crop((x0, 0, x0 + fw, 1080)).resize((w, w * 9 // 16), Image.LANCZOS)


frame(0.62, 960).save(os.path.join(OUT, "poster.png"), optimize=True)
for i in range(10):
    frame((2 * i + 1) / 20, 240).save(os.path.join(OUT, f"strip_{i}.png"), optimize=True)
for i in range(4):
    frame(i / 4 + 0.02, 480).save(os.path.join(OUT, f"part_{i + 1}.png"), optimize=True)
if os.environ.get('PANO'): im.resize((W // 4, H // 4), Image.LANCZOS).save(os.environ['PANO'])
print("frames written to", OUT)
