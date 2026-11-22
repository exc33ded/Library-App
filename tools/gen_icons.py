"""Generate the launcher-icon resources (adaptive icons per theme) and the store/promo artwork sources.

Run from the repo root:  python tools/gen_icons.py
Writes: app/src/main/res/{values/ic_colors.xml, drawable/ic_fg_*.xml, drawable/ic_mono.xml, mipmap-anydpi-v26/ic_launcher_*.xml}
        docs/icons/svg/icon_<theme>.svg  (square 108x108 art, used for the PNG store icons and the promo banner)
Palette hues/saturations mirror ui/theme/Palette.kt (tone-generated schemes); Ink & amber is hand-picked like the app.
"""
import colorsys
import os
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
SVG = ROOT / "docs/icons/svg"

# (id, label, hue, sat, accent) ; ink uses fixed colours below
THEMES = [
    ("ink", "Ink & amber", None, None, "#E5484D"),
    ("teal", "Deep teal", 174, 0.65, "#FF7A7A"),
    ("forest", "Forest", 135, 0.45, "#E5484D"),
    ("ocean", "Ocean", 214, 0.75, "#FF8A6B"),
    ("berry", "Berry", 332, 0.55, "#FFD27A"),
    ("graphite", "Graphite", 220, 0.10, "#E5484D"),
]


def luminance(rgb):
    def lin(c):
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (lin(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def tone(hue, sat, t):
    """Colour with the given hue/saturation whose CIE L* is t (same idea as toneColor in Palette.kt)."""
    if t <= 0:
        return "#000000"
    if t >= 100:
        return "#FFFFFF"
    target = ((t + 16) / 116) ** 3 if t > 8 else t / 903.3
    lo, hi = 0.0, 1.0
    for _ in range(30):
        mid = (lo + hi) / 2
        r, g, b = colorsys.hls_to_rgb(hue / 360, mid, sat)
        if luminance((r, g, b)) < target:
            lo = mid
        else:
            hi = mid
    r, g, b = colorsys.hls_to_rgb(hue / 360, (lo + hi) / 2, sat)
    return "#%02X%02X%02X" % (round(r * 255), round(g * 255), round(b * 255))


def colors(theme):
    tid, _label, hue, sat, accent = theme
    if tid == "ink":
        return {"bg": "#0F0F10", "fg": "#E0A85A", "fg2": "#A8691A", "accent": accent}
    return {"bg": tone(hue, 0.06, 6), "fg": tone(hue, sat, 80), "fg2": tone(hue, sat, 50), "accent": accent}


STITCH_Y = [41.5, 49.5, 57.5, 65.5]


def stitches(color, stroke="2.2"):
    out = []
    for y in STITCH_Y:
        out.append(f'<path android:pathData="M50.6 {y} L57.4 {y + 4}" android:strokeColor="{color}" android:strokeWidth="{stroke}" android:strokeLineCap="round"/>')
        out.append(f'<path android:pathData="M57.4 {y} L50.6 {y + 4}" android:strokeColor="{color}" android:strokeWidth="{stroke}" android:strokeLineCap="round"/>')
    return "\n    ".join(out)


def foreground_xml(c):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="{c["fg"]}" android:pathData="M54 37 C46 32 34 31 26 34 V72 C34 69 46 70 54 75 Z"/>
    <path android:fillColor="{c["fg2"]}" android:pathData="M54 37 C62 32 74 31 82 34 V72 C74 69 62 70 54 75 Z"/>
    <path android:pathData="M54 37 V75" android:strokeColor="{c["bg"]}" android:strokeWidth="2.2"/>
    {stitches(c["accent"])}
</vector>
'''


MONO_XML = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Themed (monochrome) icon: two pages with a gap, bridged by the stitches. Only the alpha matters. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#000000" android:pathData="M52.4 37 C45 32 34 31 26 34 V72 C34 69 45 70 52.4 75 Z"/>
    <path android:fillColor="#000000" android:pathData="M55.6 37 C63 32 74 31 82 34 V72 C74 69 63 70 55.6 75 Z"/>
    {stitches("#000000", "2.6")}
</vector>
'''


def adaptive_xml(tid):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_bg_{tid}"/>
    <foreground android:drawable="@drawable/ic_fg_{tid}"/>
    <monochrome android:drawable="@drawable/ic_mono"/>
</adaptive-icon>
'''


def svg_art(c):
    st = []
    for y in STITCH_Y:
        st.append(f'<path d="M50.6 {y} L57.4 {y + 4}"/><path d="M57.4 {y} L50.6 {y + 4}"/>')
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">
<rect width="108" height="108" fill="{c["bg"]}"/>
<path d="M54 37 C46 32 34 31 26 34 V72 C34 69 46 70 54 75 Z" fill="{c["fg"]}"/>
<path d="M54 37 C62 32 74 31 82 34 V72 C74 69 62 70 54 75 Z" fill="{c["fg2"]}"/>
<path d="M54 37 V75" stroke="{c["bg"]}" stroke-width="2.2"/>
<g stroke="{c["accent"]}" stroke-width="2.2" stroke-linecap="round" fill="none">{"".join(st)}</g>
</svg>
'''


def main():
    (RES / "values").mkdir(parents=True, exist_ok=True)
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    (RES / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
    SVG.mkdir(parents=True, exist_ok=True)
    color_lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for t in THEMES:
        c = colors(t)
        tid = t[0]
        color_lines.append(f'    <color name="ic_bg_{tid}">{c["bg"]}</color>')
        (RES / f"drawable/ic_fg_{tid}.xml").write_text(foreground_xml(c), encoding="utf-8")
        (RES / f"mipmap-anydpi-v26/ic_launcher_{tid}.xml").write_text(adaptive_xml(tid), encoding="utf-8")
        (SVG / f"icon_{tid}.svg").write_text(svg_art(c), encoding="utf-8")
        print(tid, c)
    color_lines.append("</resources>")
    (RES / "values/ic_colors.xml").write_text("\n".join(color_lines) + "\n", encoding="utf-8")
    (RES / "drawable/ic_mono.xml").write_text(MONO_XML, encoding="utf-8")


if __name__ == "__main__":
    main()
