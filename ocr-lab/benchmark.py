"""Repeatable synthetic OCR benchmark with known source and timeline."""
import argparse
import json
from pathlib import Path
import time
from PIL import Image, ImageDraw, ImageFont
from core import Dialogue, normalize, percentile, score_cues
from engines import make_engine, preprocess

CUES=[
    "I can see the road.",
    "Wait here for me.",
    "We have only one chance.",
    "The door is still open.",
    "Please turn off the light.",
    "I will meet you outside.",
    "Do not leave without us.",
    "There is no time to waste.",
    "Are you ready to go?",
    "I know where we are.",
    "Look behind the house.",
    "Everything will be fine.",
]

def fixture(text, font, index):
    image=Image.new("RGB",(768,150),(32+index%5*8,45,62))
    draw=ImageDraw.Draw(image)
    draw.rectangle((20,80,748,143),fill=(16,16,16))
    draw.text((384,110),text,font=font,fill="white",anchor="mm",stroke_width=1,stroke_fill="black")
    return image

def font_for_test():
    candidates=[Path("C:/Windows/Fonts/arial.ttf"),Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")]
    for p in candidates:
        if p.exists():
            return ImageFont.truetype(str(p),28)
    return ImageFont.load_default(size=28)

def run(engine_name,mode,scale,duration,interval,psm):
    engine=make_engine(engine_name)
    font=font_for_test()
    images=[fixture(t,font,i) for i,t in enumerate(CUES)]
    # Warm-up excluded; record separately.
    t=time.perf_counter()
    engine.read(preprocess(images[0],mode,scale),psm)
    warmup=(time.perf_counter()-t)*1000
    tracker=Dialogue()
    elapsed=0.0
    observed=[]
    times=[]
    late=0
    trace=[]
    while elapsed < duration*len(CUES):
        idx=int(elapsed//duration)
        t=time.perf_counter()
        result=engine.read(preprocess(images[idx],mode,scale),psm)
        cost=(time.perf_counter()-t)*1000
        event=tracker.accept(result)
        times.append(cost)
        if event.kind=="new":
            observed.append((idx,event.text))
            late+=int(elapsed+cost >= (idx+1)*duration)
        trace.append({"sample_ms":round(elapsed,1),"cue":idx+1,"result":normalize(result),
                      "cost_ms":round(cost,1),"event":event.kind})
        # Simulated live scheduling: no old frame backlog, one reader.
        elapsed+=max(cost,interval)
    return {"engine":engine_name,"filter":mode,"scale":scale,"cue_ms":duration,
            "interval_ms":interval,"warmup_ms":round(warmup,1),"samples":len(times),
            "mean_ms":round(sum(times)/len(times),1),"p95_ms":round(percentile(times,.95),1),
            "late_new_events":late,**score_cues(CUES,observed),"trace":trace}

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--engine",choices=["tesseract","rapid"],default="tesseract")
    p.add_argument("--filter",choices=["raw","gray","contrast","binary"],default="raw")
    p.add_argument("--scale",type=int,choices=[50,75,100,125,150],default=100)
    p.add_argument("--duration",type=int,choices=[500,1000,1500,2000,3000],default=1500)
    p.add_argument("--interval",type=int,choices=[100,200,250,500],default=250)
    p.add_argument("--psm",type=int,choices=[6,7,11],default=6)
    p.add_argument("--output",type=Path)
    args=p.parse_args()
    report=run(args.engine,args.filter,args.scale,args.duration,args.interval,args.psm)
    print(json.dumps({k:v for k,v in report.items() if k!="trace"},indent=2))
    if args.output:
        args.output.write_text(json.dumps(report,indent=2),encoding="utf-8")

if __name__=="__main__":
    main()
