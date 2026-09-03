#!/usr/bin/env python3
"""Draws the launcher icon.

Generated rather than hand-drawn, the same as the iOS one, so it can be reviewed as
code and regenerated after a palette change:

    pip install pillow
    python3 tools/make_icon.py

Writes the adaptive icon's foreground and monochrome layers as PNGs at every density,
the background as a vector, the adaptive-icon XML that binds them, and a preview of
how it looks once a launcher has masked it.

The toilet is the same drawing as the iOS icon, at the same coordinates, minus the
room: an adaptive icon supplies its own background, and the outer ring of one is
masked away by the launcher — so the fixture is fitted to the 72dp safe zone rather
than the full 108dp.
"""

import math
import os

from PIL import Image, ImageDraw

DESIGN = 1024           # the iOS icon's coordinate space, reused
SCALE = 4               # supersample, then downsample for clean edges
S = DESIGN * SCALE

# Adaptive icons are 108dp with a 72dp safe zone. These are the pixel sizes per
# density bucket; anything outside the middle two thirds may be masked off.
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
SAFE = 72 / 108
MARGIN = 0.94           # a little air inside the safe zone

ROOM_TOP = (176, 226, 235)
ROOM_BOTTOM = (108, 178, 196)
PORCELAIN = (255, 255, 255)
PORCELAIN_MID = (232, 238, 245)
PORCELAIN_DARK = (203, 214, 227)
SHADOW = (88, 112, 136)
WATER_LIGHT = (126, 200, 236)
WATER_DARK = (24, 100, 166)
FOAM = (245, 252, 255)
CHROME_LIGHT = (248, 250, 252)
CHROME_MID = (186, 197, 209)
CHROME_DARK = (108, 120, 134)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "app", "src", "main", "res")


def px(value):
    return value * SCALE


def box(cx, cy, w, h):
    return [px(cx - w / 2), px(cy - h / 2), px(cx + w / 2), px(cy + h / 2)]


def draw_fixture():
    """The toilet alone, on transparency, in the 1024 design space."""
    image = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    # Cistern and its lid.
    draw.rounded_rectangle(box(512, 348, 430, 350), radius=px(58), fill=PORCELAIN_MID)
    draw.rounded_rectangle(box(512, 340, 430, 350), radius=px(58), fill=PORCELAIN)
    draw.rounded_rectangle(box(512, 175, 476, 74), radius=px(30), fill=PORCELAIN_MID)
    draw.rounded_rectangle(box(512, 170, 476, 74), radius=px(30), fill=PORCELAIN)

    # Bowl: a tapered body under the seat.
    draw.polygon(
        [(px(250), px(600)), (px(774), px(600)), (px(690), px(880)), (px(334), px(880))],
        fill=PORCELAIN_DARK,
    )
    draw.polygon(
        [(px(258), px(600)), (px(766), px(600)), (px(684), px(872)), (px(340), px(872))],
        fill=PORCELAIN,
    )
    draw.rounded_rectangle(box(512, 890, 400, 62), radius=px(26), fill=PORCELAIN)

    # Seat.
    draw.ellipse(box(512, 610, 540, 250), fill=PORCELAIN_MID)
    draw.ellipse(box(512, 604, 540, 250), fill=PORCELAIN)

    # The hole, and the water in it.
    draw.ellipse(box(512, 612, 404, 168), fill=SHADOW)
    draw.ellipse(box(512, 616, 388, 152), fill=PORCELAIN_DARK)

    pool = box(512, 620, 344, 126)
    for step_index in range(60):
        t = step_index / 59
        colour = tuple(round(a + (b - a) * t) for a, b in zip(WATER_LIGHT, WATER_DARK))
        top = pool[1] + (pool[3] - pool[1]) * t / 2
        bottom = pool[3] - (pool[3] - pool[1]) * t / 2
        draw.ellipse([pool[0], top, pool[2], bottom], fill=colour)
    draw.ellipse(pool, outline=WATER_DARK, width=SCALE * 2)

    # Swirl.
    cx, cy = px(512), px(620)
    rx, ry = px(172), px(63)
    for arm in range(3):
        points = []
        base = arm * (2 * math.pi / 3)
        for step_index in range(41):
            u = step_index / 40
            theta = base + u * 3.2 * math.pi
            radius = 1 - u * 0.92
            points.append((cx + math.cos(theta) * rx * radius, cy + math.sin(theta) * ry * radius))
        draw.line(points, fill=FOAM, width=SCALE * 9, joint="curve")
    draw.ellipse(box(512, 620, 40, 18), fill=WATER_DARK)

    # Glint on the water.
    draw.ellipse(box(440, 588, 110, 34), fill=(255, 255, 255))

    # Handle, on the tank face rather than hanging off its edge.
    #
    # The cistern spans x 297..727. The lever used to run 140..296, which put the
    # whole thing outside the tank in mid-air. The app mounts it inboard: a 58x15
    # capsule from the cistern's left edge to a pivot 58 units in, tilted 10 degrees
    # so the free end hangs low. In icon units that is 297..422 at y 270.
    lever = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    lever_draw = ImageDraw.Draw(lever)
    lever_draw.rounded_rectangle([px(297), px(253), px(424), px(287)], radius=px(17), fill=CHROME_LIGHT)
    lever_draw.rounded_rectangle([px(297), px(275), px(424), px(287)], radius=px(6), fill=CHROME_MID)
    # Negative because Pillow rotates counter-clockwise and the app tilts the other way.
    lever = lever.rotate(-10, resample=Image.BICUBIC, center=(px(422), px(270)))
    image = Image.alpha_composite(image, lever)
    draw = ImageDraw.Draw(image)

    draw.ellipse(box(422, 270, 60, 60), fill=CHROME_MID)
    draw.ellipse(box(422, 270, 52, 52), fill=CHROME_LIGHT)
    draw.ellipse(box(422, 270, 14, 14), fill=CHROME_DARK)

    return image.resize((DESIGN, DESIGN), Image.LANCZOS).crop(image.resize((DESIGN, DESIGN), Image.LANCZOS).getbbox())


def layer(fixture, size, silhouette=False):
    """One density's worth of foreground: the fixture, fitted to the safe zone."""
    target = size * SAFE * MARGIN
    ratio = min(target / fixture.width, target / fixture.height)
    fitted = fixture.resize((max(1, round(fixture.width * ratio)),
                             max(1, round(fixture.height * ratio))), Image.LANCZOS)

    if silhouette:
        # Themed icons want one flat shape, not a picture.
        white = Image.new("RGBA", fitted.size, (255, 255, 255, 255))
        white.putalpha(fitted.getchannel("A"))
        fitted = white

    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(fitted, ((size - fitted.width) // 2, (size - fitted.height) // 2), fitted)
    return out


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("wrote", os.path.relpath(path, ROOT))


def main():
    fixture = draw_fixture()

    for bucket, size in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-" + bucket)
        os.makedirs(folder, exist_ok=True)
        layer(fixture, size).save(os.path.join(folder, "ic_launcher_foreground.png"))
        layer(fixture, size, silhouette=True).save(os.path.join(folder, "ic_launcher_monochrome.png"))
    print("wrote foreground and monochrome at %d densities" % len(DENSITIES))

    # The room, as a gradient. A vector rather than five more PNGs, because it is two
    # colours and a direction.
    write(os.path.join(RES, "drawable", "ic_launcher_background.xml"), """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/make_icon.py. The room the toilet stands in. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr xmlns:aapt="http://schemas.android.com/aapt" name="android:fillColor">
            <gradient
                android:startX="54" android:startY="0"
                android:endX="54" android:endY="108"
                android:type="linear">
                <item android:offset="0" android:color="#%02X%02X%02X" />
                <item android:offset="1" android:color="#%02X%02X%02X" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
""" % (ROOM_TOP + ROOM_BOTTOM))

    adaptive = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/make_icon.py. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""
    write(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher.xml"), adaptive)
    write(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher_round.xml"), adaptive)

    # And what a launcher will actually show, so it can be looked at.
    size = DENSITIES["xxxhdpi"]
    preview = Image.new("RGBA", (size, size))
    for y in range(size):
        t = y / (size - 1)
        colour = tuple(round(a + (b - a) * t) for a, b in zip(ROOM_TOP, ROOM_BOTTOM))
        ImageDraw.Draw(preview).line([(0, y), (size, y)], fill=colour + (255,))
    front = layer(fixture, size)
    preview.paste(front, (0, 0), front)

    # The circle is the harshest common mask; if it survives that it survives the rest.
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    masked = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    masked.paste(preview, (0, 0), mask)

    sheet = Image.new("RGBA", (size * 2 + 24, size), (28, 30, 34, 255))
    sheet.paste(preview, (0, 0))
    sheet.paste(masked, (size + 24, 0), masked)
    sheet.convert("RGB").save(os.path.join(HERE, "icon-preview.png"))
    print("wrote", os.path.relpath(os.path.join(HERE, "icon-preview.png"), ROOT))


if __name__ == "__main__":
    main()
