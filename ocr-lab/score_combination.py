"""Score native lines from simulator logs. No external translation API.
Group geometry inspired by GameTranslator, independently implemented.
"""
import json, re, sys, statistics
from pathlib import Path
def norm(s):return " ".join(re.findall(r"[a-z0-9]+",s.lower()))
def iou(a,b):
    intersect=max(0,min(a[2],b[2])-max(a[0],b[0]))*max(0,min(a[3],b[3])-max(a[1],b[1]))
    area=lambda q:max(0,q[2]-q[0])*max(0,q[3]-q[1])
    return intersect/max(1,area(a)+area(b)-intersect)
def groups(row):
    x,y,_,_=row["rect"]
    lines=[(l["text"],[l["box"][0]+x,l["box"][1]+y,l["box"][2]+x,l["box"][3]+y]) for l in row["lines"] if re.search(r"[a-zA-Z]{2}",l["text"])]
    blocks=[]
    for text,box in sorted(lines,key=lambda a:(a[1][1],a[1][0])):
        def joins(group):
            p=group[-1][1];a=p[3]-p[1];b=box[3]-box[1]
            overlap=min(p[2],box[2])-max(p[0],box[0])
            return min(a,b)>0 and max(a,b)<=1.6*min(a,b) and 0<=box[1]-p[1] and box[1]-p[3]<.8*max(a,b) and overlap>=.5*min(p[2]-p[0],box[2]-box[0])
        match=next((g for g in reversed(blocks) if joins(g)),None)
        if match is None:blocks.append([(text,box)])
        else:match.append((text,box))
    return [dict(text=" ".join(t for t,b in g),box=[min(b[0] for t,b in g),min(b[1] for t,b in g),max(b[2] for t,b in g),max(b[3] for t,b in g)]) for g in blocks]
def dedup(groups):
    out=[]
    for g in groups:
        if not any(norm(g["text"])==norm(p["text"]) and iou(g["box"],p["box"])>=.5 for p in out):out.append(g)
    return out
def score(row,gs):
    return [max([iou(t["box"],g["box"]) for g in gs if norm(t["text"])==norm(g["text"])]+[0]) for t in row["truth"]]
def load(path):
    rows={}
    for line in Path(path).read_text(errors="replace").splitlines():
        try:
            row=json.loads(line.split("COMBO ",1)[1] if "COMBO " in line else line)
            if "engine" in row and "lines" in row:rows[(row["engine"],row["id"],row["mode"])]=row
        except (ValueError,IndexError):pass
    return list(rows.values())
def summarize(rows):
    results={}
    for engine in sorted({r["engine"] for r in rows}):
        allrows=[r for r in rows if r["engine"]==engine]
        cases={r["id"]:{} for r in allrows}
        for r in allrows:cases[r["id"]][r["mode"]]=r
        base=[m for m in cases.values() if "raw" in m and "outline" in m and m["raw"]["state"]=="base" and m["raw"]["zone"]<10]
        base.sort(key=lambda m:m["raw"]["zone"])
        metrics={}
        for mode in ["raw","outline","fallback"]:
            detail=[]
            for m in base:
                selected=m["outline"] if mode!="raw" else m["raw"]
                gs=groups(selected);elapsed=selected["ocr_ms"]+(selected["prep_host_ms"] if mode!="raw" else 0)
                if mode=="fallback" and (not gs or "error" in selected):
                    selected=m["raw"];gs=groups(selected);elapsed+=selected["ocr_ms"]
                detail.append(dict(zone=selected["zone"],matches=score(selected,gs),groups=gs,ms=elapsed))
            metrics[mode]={"exact_with_iou_50":sum(v>=.5 for d in detail for v in d["matches"]),"total_expected":sum(len(d["matches"]) for d in detail),"extra_groups":sum(len(d["groups"])-sum(v>=.5 for v in d["matches"]) for d in detail),"prefix_sum_ms":{str(n):sum(d["ms"] for d in detail[:n]) for n in [1,2,4,6,8,10] if len(detail)>=n},"detail":detail}
        # Sequence and overlap use actual OCR boxes. A fixture dictionary is not a live translator.
        def get(key):
            m=cases.get(key,{})
            return m.get("outline")
        a,b,c,o=[get(k) for k in ["combo_base_09","combo_moved_09","combo_empty_09","combo_base_10"]]
        sequence={}
        if all([a,b,c]):
            aa,bb,cc=[groups(r) for r in [a,b,c]]
            sequence={"base_matches":score(a,aa),"moved_matches":score(b,bb),"empty_groups":len(cc),"delta_x":bb[0]["box"][0]-aa[0]["box"][0] if aa and bb else None}
        original=get("combo_base_00")
        if original and o:
            both=groups(original)+groups(o)
            sequence["overlap_groups_before"]=len(both);sequence["overlap_groups_after"]=len(dedup(both))
        results[engine]={"received":len(allrows),"expected":26,"errors":[r.get("error") for r in allrows if "error" in r],"metrics":metrics,"sequence":sequence}
    return results
if __name__=="__main__":
    rows=load(sys.argv[1]);result=summarize(rows)
    Path(sys.argv[2]).write_text(json.dumps(result,indent=2),encoding="utf8")
    for e,r in result.items(): print("COMBO_SUMMARY",e,json.dumps({k:v for k,v in r.items() if k!="metrics"}),{k:{x:y for x,y in v.items() if x!="detail"} for k,v in r["metrics"].items()})
    if not result or any(r["received"]!=r["expected"] or r["errors"] for r in result.values()):raise SystemExit("Incomplete native OCR results")
