"""SubLens MIT filter, upstream 5f3f3506a5e19630fac8d0c81ab9fec8e3617eee; see SubLens-LICENSE.txt.
https://github.com/bnsware/SubLens/blob/5f3f3506a5e19630fac8d0c81ab9fec8e3617eee/sublens/ocr.py
"""
import numpy as np
from PIL import Image, ImageFilter, ImageOps

def isolate_bright_text(img: Image.Image, scale: int = 1) -> Image.Image:
    """Keep bright, unsaturated pixels that sit next to a darker edge.

    Game subtitles are white(ish) with an outline, drop shadow or dim backing.
    Requiring a darker pixel nearby separates glyph strokes from large bright
    areas (sky, snow, lamps) that would otherwise turn into solid blobs.
    Output: black text on white, which every OCR engine likes.
    """
    a = np.asarray(img.convert("RGB"), dtype=np.int32)
    mx = a.max(axis=2)
    mn = a.min(axis=2)
    lum = (a[..., 0] * 299 + a[..., 1] * 587 + a[..., 2] * 114) // 1000

    # brightness threshold adapts to the brightest text in the frame
    hi = float(np.percentile(lum, 99.5))
    thr = max(150.0, min(215.0, hi - 45))
    bright = (lum >= thr) & ((mx - mn) <= 70)

    # darker surroundings within a few pixels (outline, shadow, backing box)
    gray = Image.fromarray(lum.clip(0, 255).astype(np.uint8))
    # window must reach past a glyph stroke to its outline; strokes grow with upscaling
    k = 3 + 2 * max(1, scale)
    local_min = np.asarray(gray.filter(ImageFilter.MinFilter(k)), dtype=np.int32)
    edge = (lum - local_min) >= 70

    mask = bright & edge
    # close tiny holes inside glyphs
    m = Image.fromarray(np.where(mask, 255, 0).astype(np.uint8))
    m = m.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3))
    return ImageOps.invert(m).convert("RGB")

