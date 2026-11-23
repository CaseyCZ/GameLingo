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
    """Vector-style gamepad in a subtitle speech bubble, cyan-to-violet."""
    scale = n / 1024
    img = Image.new("RGB", (n, n), NAVY)
    d = ImageDraw.Draw(img)
    def p(v):
        return tuple(round(x * scale) for x in v)

    # Subtle CaseyCZ glow on a navy surface. Crisp icon is not a copy of
    # GlossaSub branding; it uses GameLingo's own bubble + controller geometry.
    for y in range(n):
        mix = y / max(1, n-1)
        col = (int(11 + mix * 3), int(22 - mix * 7), int(44 + mix * 9))
        d.line([(0, y), (n, y)], fill=col)

    mask = Image.new("L", (n, n), 0)
    md = ImageDraw.Draw(mask)
    width = round(38*scale)
    md.rounded_rectangle(p((140, 180, 882, 800)),
                         radius=round(180 * scale), outline=255, width=width)
    # Bubble tail: continuing the left/lower contour.
    md.line([p((274, 783)), p((229, 906)), p((394, 788))],
            fill=255, width=width, joint="curve")

    gradient = Image.new("RGB", (n,n), CYAN)
    gd = ImageDraw.Draw(gradient)
    a,b=(0,194,249),(177,73,254)
    for x in range(n):
        t=x / max(1,n-1)
        color=tuple(int(a[i]*(1-t)+b[i]*t) for i in range(3))
        gd.line([(x,0),(x,n)], fill=color)
    img.paste(gradient,(0,0),mask)

    d = ImageDraw.Draw(img)
    # Compact, readable controller at launcher icon sizes.
    d.rounded_rectangle(p((265, 405, 755, 650)),
                        radius=round(115*scale),
                        outline=WHITE, width=round(33*scale))
    d.rounded_rectangle(p((354, 451, 387, 566)),
                        radius=round(8*scale),fill=CYAN)
    d.rounded_rectangle(p((313, 492, 428, 525)),
                        radius=round(8*scale),fill=CYAN)
    d.ellipse(p((567,458,626,517)),fill=(159,89,246))
    d.ellipse(p((646,516,705,575)),fill=(159,89,246))
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
    d.text((310,123),"Game",font=font(49),fill=WHITE)
    d.text((463,123),"Lingo",font=font(49),fill=(163,96,250))
    d.text((313,194),"Hraj. Rozuměj.",font=font(24),fill=CYAN)
    banner.save(images/"gamelingo_banner.png",optimize=True)
    print("Wrote GameLingo Android app icons and TV banner")
if __name__=="__main__":
    main()
