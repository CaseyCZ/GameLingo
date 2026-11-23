#!/usr/bin/env python3
"""Original GameLingo icon/banner. Deterministic art; no upstream logos."""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

NAVY = (7, 11, 20)
CARD = (17, 24, 39)
CYAN = (56, 189, 248)
BLUE = (2, 132, 199)
WHITE = (248, 250, 252)

def font(size):
    for name in (
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf",
    ):
        if Path(name).exists():
            return ImageFont.truetype(name, size)
    return ImageFont.load_default()

def make_icon(n=1024):
    img = Image.new("RGB", (n, n), NAVY)
    px = img.load()
    for y in range(n):
        for x in range(n):
            d = min(1, ((x-n*.20)**2 + (y-n*.15)**2)**.5/(n*.9))
            px[x,y] = (
                int(NAVY[0]*(1-d)+13*d),
                int(NAVY[1]*(1-d)+26*d),
                int(NAVY[2]*(1-d)+49*d)
            )
    d = ImageDraw.Draw(img)
    a = n/1024
    def box(v): return tuple(round(c*a) for c in v)
    # Chat frame (subtitles) combined with an original gamepad pictogram.
    d.rounded_rectangle(box((150,185,874,810)),radius=round(170*a),fill=(15,43,69),
                        outline=CYAN,width=round(28*a))
    d.polygon([box((265,758))[0:2],box((258,905))[0:2],box((445,793))[0:2]],fill=(15,43,69))
    d.line([box((275,778))[0:2],box((268,867))[0:2],box((373,807))[0:2]],fill=CYAN,width=round(21*a),joint="curve")
    # White gamepad body
    d.rounded_rectangle(box((278,365,749,639)),radius=round(124*a),fill=WHITE)
    d.rounded_rectangle(box((363,430,413,565)),radius=round(14*a),fill=BLUE)
    d.rounded_rectangle(box((320,470,455,523)),radius=round(14*a),fill=BLUE)
    d.ellipse(box((562,442,622,502)),fill=BLUE)
    d.ellipse(box((636,500,695,560)),fill=BLUE)
    return img

def main():
    if len(sys.argv) != 3 or sys.argv[1] not in ("ios", "android"):
        raise SystemExit("Usage: create_brand_assets.py ios <AppIcon.png> | android <upstream>")
    if sys.argv[1] == "ios":
        out=Path(sys.argv[2]);out.parent.mkdir(parents=True,exist_ok=True)
        make_icon().save(out,optimize=True)
        print("Wrote GameLingo icon",out)
        return
    root=Path(sys.argv[2])
    images=root/"app/src/main/res/drawable-nodpi"
    for name in ("gamelingo_ikona.png","logo.png"):
        make_icon().save(images/name,optimize=True)
    banner=Image.new("RGB",(640,360),NAVY)
    d=ImageDraw.Draw(banner)
    banner.paste(make_icon(280),(18,40))
    d.text((310,123),"GameLingo",font=font(50),fill=WHITE)
    d.text((313,194),"Hraj. Rozumej.",font=font(24),fill=CYAN)
    banner.save(images/"gamelingo_banner.png",optimize=True)
    print("Wrote GameLingo Android app icons and TV banner")
if __name__=="__main__":
    main()
