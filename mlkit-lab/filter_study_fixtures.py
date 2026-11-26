"""Paired native OCR filter study: fixed synthetic strata + pinned public film ROIs."""
import io,json,random,sys,time
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont,ImageFilter
ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT/"ocr-lab"))
from third_party.sublens_filter import isolate_bright_text
from public_fixtures import load_public
DEST=ROOT/"mlkit-lab/app/src/androidTest/assets"
def synthetic(kind,rep,text):
    rng=random.Random(4200+rep)
    w,h=640,160
    im=Image.new("RGB",(w,h),(32,39,46))
    d=ImageDraw.Draw(im)
    if kind=="bright":
        d.rectangle((0,0,w,h),fill=(223,224,218))
        for x in range(0,w,32):d.rectangle((x,0,x+15,h),fill=(184,196,207))
    elif kind=="busy":
        for _ in range(180):
            x,y=rng.randrange(w),rng.randrange(h)
            d.rectangle((x,y,x+rng.randrange(8,90),y+rng.randrange(5,42)),fill=tuple(rng.randrange(40,240) for _ in range(3)))
    elif kind=="low_contrast":
        d.rectangle((0,0,w,h),fill=(112,115,120))
    elif kind=="colored":
        d.rectangle((0,0,w,h),fill=(72,83,94))
    elif kind=="small_compressed":
        for _ in range(70):
            x,y=rng.randrange(w),rng.randrange(h)
            d.rectangle((x,y,x+rng.randrange(8,90),y+rng.randrange(5,42)),fill=tuple(rng.randrange(40,200) for _ in range(3)))
    font=ImageFont.load_default(size=15 if kind=="small_compressed" else 28)
    fill=(255,255,255)
    if kind=="colored":fill=(255,218,0) if rep==0 else (60,220,255)
    if kind=="low_contrast":fill=(166,169,173)
    stroke=2 if kind in ("bright","busy","colored") else (1 if kind=="small_compressed" else 0)
    xy=(28+rep*33,53)
    box=d.multiline_textbbox(xy,text,font=font,spacing=4,stroke_width=stroke)
    d.multiline_text(xy,text,font=font,spacing=4,fill=fill,stroke_width=stroke,stroke_fill=(15,15,15))
    if kind=="small_compressed":
        buf=io.BytesIO();im.save(buf,"JPEG",quality=28);buf.seek(0)
        with Image.open(buf) as compressed:im=compressed.convert("RGB")
    return im,list(box)
def main():
    DEST.mkdir(parents=True,exist_ok=True)
    rows=[]
    def add(key,kind,im,text,box=None):
        im.save(DEST/f"{key}_raw.png")
        start=time.perf_counter()
        out=isolate_bright_text(im)
        ms=(time.perf_counter()-start)*1000
        out.save(DEST/f"{key}_outline.png")
        rows.append(dict(id=key,dataset="synthetic" if box is not None else "public",scene=kind,expected=text,
            rect=[0,0,im.width,im.height],width=im.width,height=im.height,prep_host_ms=ms,
            truth=[dict(text=text,box=box)] if box is not None else []))
    kinds=["clean_dark","bright","busy","colored","low_contrast","small_compressed"]
    phrases=["Keep the engine running.","Go to the north gate!","Wait for my signal.","We should leave now.",
             "There is no time left.","I can hear them coming.","Check the map again.","Find a safe place.",
             "Do not make a sound.","We need your help.","Stay close to me.","Open the rear door."]
    for i,kind in enumerate(kinds):
        for rep in range(2):
            text=phrases[i*2+rep]
            im,box=synthetic(kind,rep,text)
            add(f"study_s_{i*2+rep:02d}",kind,im,text,box)
    # First six of each existing balanced watermark stratum (previously pinned sample).
    # Reused reference data is explicitly not an unseen validation set.
    public=load_public()
    assert len(public)==24,len(public)
    for i in list(range(6))+list(range(12,18)):
        scene,text,im=public[i]
        # Bound decoding/recognition cost equally in both modes, preserve aspect ratio.
        im.thumbnail((960,540),Image.Resampling.LANCZOS)
        add(f"study_p_{i:02d}","public_no_watermark" if i<12 else "public_watermark",im,text)
    assert len(rows)==24
    (DEST/"combination.json").write_text(json.dumps(rows,ensure_ascii=False),encoding="utf8")
    print("Prepared",len(rows),"paired study ROIs")
if __name__=="__main__":main()
