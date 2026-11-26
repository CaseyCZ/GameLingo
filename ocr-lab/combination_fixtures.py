"""Independent synthetic fixtures for native OCR, no Tesseract or network."""
import json, time, random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from third_party.sublens_filter import isolate_bright_text

ROOT=Path(__file__).resolve().parent.parent
DEST=ROOT/"mlkit-lab/app/src/androidTest/assets"
W,H=1280,720
def main():
    DEST.mkdir(parents=True,exist_ok=True)
    font=ImageFont.load_default(size=27)
    words=["Find the vehicle","Mission started","Take cover now","Open the gate","Meet me outside","Press the lever","Follow the road","Keep moving","Return to camp","We need to leave"]
    cs=["Najdi vozidlo","Mise zahájena","Kryj se","Otevři bránu","Sejdeme se venku","Stiskni páku","Drž se cesty","Pokračuj","Vrať se do tábora","Musíme odejít"]
    zones=[(20+c*420,20+r*166,20+c*420+400,20+r*166+145) for r in range(3) for c in range(3)]+[(440,520,840,690)]
    entries=[]
    for state in ["base","moved","empty"]:
        rng=random.Random(871)
        frame=Image.new("RGB",(W,H),(67,90,111))
        d=ImageDraw.Draw(frame)
        for i in range(850):
            x,y=rng.randrange(W),rng.randrange(H)
            d.rectangle((x,y,x+rng.randrange(10,110),y+rng.randrange(4,50)),fill=tuple(rng.randrange(35,230) for _ in range(3)))
        truth=[]
        for i,(x,y,x2,y2) in enumerate(zones):
            if state=="empty" and i==9:continue
            if state!="base" and i!=9:continue
            text=words[i]
            xy=(x+25+(48 if state=="moved" else 0),y+42)
            if i==9:
                text="We need to\nleave"
            color=(255,221,0) if i==2 else (255,255,255)
            d.multiline_text(xy,text,font=font,fill=color,stroke_width=2,stroke_fill=(0,0,0),spacing=4)
            box=d.multiline_textbbox(xy,text,font=font,spacing=4,stroke_width=2)
            truth.append(dict(zone=i,text=words[i],translation=cs[i],box=list(box)))
        # Deliberately outside configured regions.
        d.text((34,580),"GARAGE",font=font,fill="white",stroke_width=2,stroke_fill="black")
        chosen=list(enumerate(zones)) if state=="base" else [(9,zones[9])]
        if state=="base":chosen.append((10,(35,25,415,155))) # alternate run: replaces one zone, not an eleventh active zone
        for i,box in chosen:
            crop=frame.crop(box)
            key=f"combo_{state}_{i:02d}"
            crop.save(DEST/f"{key}_raw.png")
            start=time.perf_counter()
            filtered=isolate_bright_text(crop)
            ms=(time.perf_counter()-start)*1000
            filtered.save(DEST/f"{key}_outline.png")
            entries.append(dict(id=key,state=state,zone=i,rect=list(box),width=W,height=H,prep_host_ms=ms,truth=[x for x in truth if x["zone"]==(0 if i==10 else i)]))
    (DEST/"combination.json").write_text(json.dumps(entries,ensure_ascii=False),encoding="utf8")
    print("Prepared",len(entries),"ROI cases")
if __name__=="__main__":main()
