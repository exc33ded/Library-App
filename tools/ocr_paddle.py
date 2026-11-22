import json
import os
import sys

import numpy as np
from PIL import Image, ImageOps
from paddleocr import PaddleOCR

MAX_EDGE = 1600
ROTATIONS = (0, 90, 270)
UPRIGHT_ENOUGH = 15  # letters; below this the photo may be sideways, so also try 90/270
DET_SIDE = 960  # detection runs on a 960px max side: ~3x faster than 1600 and enough for cover titles


def make_ocr():
    kwargs = dict(
        lang="en",
        use_doc_orientation_classify=False,
        use_doc_unwarping=False,
        use_textline_orientation=False,
        enable_mkldnn=False,  # Paddle 3.x on Windows crashes in the oneDNN kernel (ConvertPirAttribute2RuntimeAttribute)
    )
    try:
        ocr = PaddleOCR(ocr_version="PP-OCRv6", **kwargs)
        print("PaddleOCR engine: PP-OCRv6", file=sys.stderr)
    except Exception:
        ocr = PaddleOCR(**kwargs)
        print("PaddleOCR engine: default (PP-OCRv6 unavailable for lang=en)", file=sys.stderr)
    return ocr


def read_result(res):
    raw_json = getattr(res, "json", {})
    nested = raw_json.get("res", {}) if isinstance(raw_json, dict) else {}
    for source in (res, nested):
        try:
            return source["rec_texts"], source["rec_polys"]
        except (KeyError, TypeError, AttributeError):
            continue
    return [], []


def recognize(ocr, image):
    lines = []
    for res in ocr.predict(np.array(image), text_det_limit_side_len=DET_SIDE, text_det_limit_type="max"):
        texts, polys = read_result(res)
        for text, poly in zip(texts, polys):
            if not str(text).strip():
                continue
            xs = [point[0] for point in poly]
            ys = [point[1] for point in poly]
            height = int(round(min(max(xs) - min(xs), max(ys) - min(ys))))
            lines.append({"text": str(text), "height": height})
    return lines


def letter_count(lines):
    return sum(ch.isalpha() for line in lines for ch in line["text"])


def prepare(path):
    with Image.open(path) as img:
        img = ImageOps.exif_transpose(img).convert("RGB")
        longest = max(img.size)
        if longest > MAX_EDGE:
            scale = MAX_EDGE / longest
            img = img.resize(
                (round(img.width * scale), round(img.height * scale)),
                Image.Resampling.LANCZOS,
            )
        return img.copy()


def main():
    photos_dir, out_path = sys.argv[1], sys.argv[2]
    ocr = make_ocr()
    entries = []
    for name in sorted(n for n in os.listdir(photos_dir) if n.lower().endswith(".jpg")):
        print(f"OCR: {name}", file=sys.stderr)
        base = prepare(os.path.join(photos_dir, name))
        lines = recognize(ocr, base)
        if letter_count(lines) < UPRIGHT_ENOUGH:
            lines = max(
                [lines] + [recognize(ocr, base.rotate(deg, expand=True)) for deg in ROTATIONS[1:]],
                key=letter_count,
            )
        entries.append({"file": name, "lines": lines})
    with open(out_path, "w", encoding="utf-8") as out:
        json.dump(entries, out, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
