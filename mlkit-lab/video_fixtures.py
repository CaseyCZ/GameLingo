"""Pinned gameplay demo; native OCR receives the same bottom ROI in both modes."""
import hashlib,json,subprocess,sys,tempfile,time,os
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT/"ocr-lab"))
from third_party.sublens_filter import isolate_bright_text
URL="https://raw.githubusercontent.com/pigro141/livedub/5666537f002c75c1b4697634ad42e64ae58cb6f1/assets/vetrina/doppiaggio-gtav.mp4"
SHA="2ce09f6da04da6df51200e8375a74513f214a028ae57d6b1842e36dc9b19c501"
DEST=ROOT/"mlkit-lab/app/src/androidTest/assets"
def main():
    gta6=os.environ.get("OCR_VIDEO_SOURCE")=="gta-vi-trailer"
    url=URL.replace("doppiaggio-gtav.mp4","trailer-multilingua.mp4") if gta6 else URL
    DEST.mkdir(parents=True,exist_ok=True)
    rows=[]
    with tempfile.TemporaryDirectory() as tmp:
        d=Path(tmp);video=d/"input.mp4"
        subprocess.run(["curl","--fail","--location","--retry","2","--max-time","60","--max-filesize","10000000",url,"-o",str(video)],check=True)
        digest=hashlib.sha256(video.read_bytes()).hexdigest()
        if not gta6:assert digest==SHA,"Video checksum mismatch"
        print("VIDEO_SOURCE_SHA256",digest)
        subprocess.run(["ffmpeg","-hide_banner","-loglevel","error","-i",str(video),"-t","10" if gta6 else "15","-vf","fps=3" if gta6 else "fps=2","-an",str(d/"%04d.png")],check=True)
        frames=sorted(d.glob("*.png"));assert len(frames)==30,len(frames)
        for i,path in enumerate(frames):
            with Image.open(path) as source:
                im=source.convert("RGB")
            w,h=im.size
            # Fixed bottom 35%: excludes the top diagnostic band; subtitle containment still unverified.
            box=(0,int(h*.65),w,h)
            crop=im.crop(box)
            key=f"video_{i:02d}"
            crop.save(DEST/f"{key}_raw.png")
            start=time.perf_counter();filtered=isolate_bright_text(crop);ms=(time.perf_counter()-start)*1000
            filtered.save(DEST/f"{key}_outline.png")
            rows.append(dict(id=key,dataset="video",scene="gta_vi_trailer" if gta6 else "gtav_demo",frame=i,
                sample_interval_ms=1000/3 if gta6 else 500,rect=list(box),width=w,height=h,prep_host_ms=ms,truth=[]))
    (DEST/"combination.json").write_text(json.dumps(rows),encoding="utf8")
    print("Prepared",len(rows),"sequential gameplay ROIs")
if __name__=="__main__":main()
