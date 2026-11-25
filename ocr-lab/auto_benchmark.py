"""Deterministic generated subtitle scenes with ground truth."""
import argparse,json,os,random,time
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
from core import normalize,percentile
from engines import make_engine,preprocess,local_translator
from experiments import FILTERS
CUES=["Wait!","No.","I can't go.","I can go.","Meet me at 7:30.","Go to sector 3.",
      "We have only one chance.","The door is still open.","Please turn off the light.",
      "We need to find the others.","",""]
def font(size):
    for p in ("C:/Windows/Fonts/arial.ttf","/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if Path(p).exists():return ImageFont.truetype(p,size)
    return ImageFont.load_default(size=size)
def fixtures():
    rng=random.Random(284)
    result=[]
    for scene in ("boxed","busy","bright","small"):
        for cue in CUES:
            im=Image.new("RGB",(768,150),(40,65,80))
            d=ImageDraw.Draw(im)
            if scene!="boxed":
                for _ in range(45):
                    x,y=rng.randrange(768),rng.randrange(150)
                    d.rectangle((x,y,x+rng.randrange(8,95),y+rng.randrange(5,38)),fill=tuple(rng.randrange(65,256) for _ in range(3)))
                if scene=="bright":d.rectangle((0,80,768,145),fill=(225,232,240))
                d.text((40,15),"HUD 42",font=font(12),fill="white")
            else:d.rectangle((20,75,748,145),fill=(10,10,10))
            if cue:d.text((384,111),cue,font=font(18 if scene=="small" else 30),
                          fill="white",anchor="mm",stroke_width=2,stroke_fill="black")
            result.append((scene,cue,im))
    return result
def distance(a,b):
    prev=list(range(len(b)+1))
    for i,x in enumerate(a,1):
        cur=[i]
        for j,y in enumerate(b,1):
            cur.append(min(prev[j]+1,cur[-1]+1,prev[j-1]+(x!=y)))
        prev=cur
    return prev[-1]
def main():
    p=argparse.ArgumentParser()
    p.add_argument("--engine",choices=["tesseract","rapid","windows"],required=True)
    p.add_argument("--output",type=Path,required=True)
    p.add_argument("--translate",action="store_true")
    a=p.parse_args()
    a.output.mkdir(parents=True,exist_ok=True)
    scenes=fixtures()
    report=dict(engine=a.engine,fixtures=len(scenes),rows=[],errors=[])
    try:
        engine=make_engine(a.engine)
        t=time.perf_counter()
        smoke=engine.read(scenes[6][2],6)
        report["smoke"]=dict(expected=CUES[6],actual=smoke,ms=round((time.perf_counter()-t)*1000,1))
        print("SMOKE",json.dumps(report["smoke"]),flush=True)
        for mode in FILTERS:
            trace=[]
            for scene,cue,im in scenes:
                try:
                    start=time.perf_counter()
                    raw=engine.read(preprocess(im,mode,100),6)
                    ms=(time.perf_counter()-start)*1000
                    expected,actual=normalize(cue).casefold(),normalize(raw).casefold()
                    trace.append(dict(scene=scene,expected=cue,actual=raw,ms=round(ms,1),
                                  errors=distance(expected,actual),chars=len(expected),exact=expected==actual))
                except Exception as exc:
                    report["errors"].append(dict(filter=mode,scene=scene,error=repr(exc)))
                    break
            if len(trace)!=len(scenes):continue
            positives=[x for x in trace if x["expected"]]
            blanks=[x for x in trace if not x["expected"]]
            row=dict(filter=mode,exact=sum(x["exact"] for x in positives),total=len(positives),
                     missed=sum(not x["actual"].strip() for x in positives),
                     false_blank=sum(bool(x["actual"].strip()) for x in blanks),blanks=len(blanks),
                     cer=round(sum(x["errors"] for x in positives)/sum(x["chars"] for x in positives),4),
                     mean_ms=round(sum(x["ms"] for x in trace)/len(trace),1),
                     p95_ms=round(percentile([x["ms"] for x in trace],.95),1))
            report["rows"].append(row)
            (a.output/f"{mode}.json").write_text(json.dumps(trace,ensure_ascii=False,indent=2),encoding="utf8")
            print("BENCH",json.dumps(row),flush=True)
        if hasattr(engine,"close"):engine.close()
    except Exception as exc:
        report["errors"].append(dict(stage="init/smoke",error=repr(exc)))
        print("ENGINE_ERROR",repr(exc),flush=True)
    if a.translate:
        try:
            start=time.perf_counter()
            translate=local_translator()
            translate("Please wait here.")
            warm=(time.perf_counter()-start)*1000
            translated=[]
            for cue in CUES:
                if not cue:continue
                start=time.perf_counter()
                text=translate(cue)
                translated.append(dict(source=cue,target=text,ms=round((time.perf_counter()-start)*1000,1)))
            report["translation"]=dict(warmup_ms=round(warm,1),results=translated,
                p95_ms=round(percentile([x["ms"] for x in translated],.95),1))
            print("TRANSLATION",json.dumps(report["translation"],ensure_ascii=True),flush=True)
        except Exception as exc:report["errors"].append(dict(stage="translation",error=repr(exc)))
    (a.output/"report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf8")
    summary=os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary,"a",encoding="utf8") as out:
            out.write(f"## {a.engine}\n\n| Filter | Exact | CER | False positives | P95 ms |\n|---|---:|---:|---:|---:|\n")
            for row in report["rows"]:
                out.write(f"| {row['filter']} | {row['exact']}/{row['total']} | {row['cer']} | {row['false_blank']}/{row['blanks']} | {row['p95_ms']} |\n")
            for err in report["errors"]:out.write("\nError: "+str(err)+"\n")
    if report["errors"] or len(report["rows"])!=len(FILTERS):raise SystemExit(1)
if __name__=="__main__":main()
