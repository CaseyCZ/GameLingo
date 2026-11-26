"""Offline evaluation of native OCR only; no external API or ground truth in mode selection."""
import json,sys,statistics,re
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent/"ocr-lab"))
from score_combination import load,groups,iou
def norm(text):return " ".join(re.findall(r"[a-z0-9]+",text.lower()))
def distance(a,b):
    prev=list(range(len(b)+1))
    for i,x in enumerate(a,1):
        row=[i]
        for j,y in enumerate(b,1):row.append(min(row[-1]+1,prev[j]+1,prev[j-1]+(x!=y)))
        prev=row
    return prev[-1]
def actual(row):
    lines=sorted(row["lines"],key=lambda l:(l["box"][1],l["box"][0]))
    return norm(" ".join(l["text"] for l in lines))
def assess(row):
    exp=norm(row["expected"]);got=actual(row)
    edits=distance(exp,got)
    geometry=None
    if row["truth"]:
        geometry=max([iou(t["box"],g["box"]) for t in row["truth"] for g in groups(row) if norm(t["text"])==norm(g["text"])]+[0])
    return dict(exact=exp==got,edits=edits,chars=len(exp),geometry=geometry,actual=got,ocr_ms=row["ocr_ms"])
def summarize(rows,engines):
    out={}
    for engine in engines:
        cases={}
        for r in rows:
            if r["engine"]==engine:cases.setdefault(r["id"],{})[r["mode"]]=r
        pairs=[m for m in cases.values() if set(m)=={"raw","outline"} and not any("error" in v for v in m.values())]
        detail=[]
        for pair in sorted(pairs,key=lambda m:m["raw"]["id"]):
            r,o=pair["raw"],pair["outline"]
            raw,outline=assess(r),assess(o)
            # Raw-first fallback ONLY if no alphabetic words were detected.
            # Compute counterfactual policy from paired OCR measurements.
            retry=not re.search(r"[a-z]{2}",raw["actual"])
            fallback=dict(outline if retry else raw)
            fallback["ocr_ms"]=raw["ocr_ms"]+(outline["ocr_ms"] if retry else 0)
            detail.append(dict(id=r["id"],dataset=r["dataset"],scene=r["scene"],expected=r["expected"],
                raw=raw,outline=outline,fallback=fallback,retry=retry,prep_host_ms=r["prep_host_ms"]))
        strata={}
        for name in ["all","synthetic","public"]+sorted({d["scene"] for d in detail}):
            subset=[d for d in detail if name=="all" or name in (d["dataset"],d["scene"])]
            if not subset:continue
            metrics={}
            for mode in ["raw","outline","fallback"]:
                ms=[d[mode]["ocr_ms"] for d in subset];geom=[d[mode]["geometry"] for d in subset if d[mode]["geometry"] is not None]
                metrics[mode]=dict(exact=sum(d[mode]["exact"] for d in subset),
                    cer=sum(d[mode]["edits"] for d in subset)/max(1,sum(d[mode]["chars"] for d in subset)),
                    median_ocr_ms=statistics.median(ms),total_ocr_ms=sum(ms),
                    geometry_at_50=sum(v>=.5 for v in geom) if geom else None,
                    host_filter_ms=sum(d["prep_host_ms"] for d in subset if mode=="outline" or (mode=="fallback" and d["retry"])))
            strata[name]=dict(n=len(subset),metrics=metrics,
                filter_better=sum(d["outline"]["edits"]<d["raw"]["edits"] for d in subset),
                raw_better=sum(d["raw"]["edits"]<d["outline"]["edits"] for d in subset),
                tied=sum(d["raw"]["edits"]==d["outline"]["edits"] for d in subset),
                raw_first_retries=sum(d["retry"] for d in subset))
        out[engine]=dict(received=sum(len(v) for v in cases.values()),expected=48,complete_pairs=len(pairs),
            errors=[dict(id=r["id"],mode=r["mode"],error=r["error"]) for r in rows if r["engine"]==engine and "error" in r],
            strata=strata,detail=detail)
    return out
if __name__=="__main__":
    rows=load(sys.argv[1])
    result=summarize(rows,sys.argv[3:])
    Path(sys.argv[2]).write_text(json.dumps(result,indent=2,ensure_ascii=False),encoding="utf8")
    for e,r in result.items():
        print("FILTER_STUDY",e,json.dumps({k:v for k,v in r.items() if k!="detail"}))
    if not result or any(r["received"]!=48 or r["complete_pairs"]!=24 or r["errors"] for r in result.values()):
        raise SystemExit("Incomplete native OCR results")
