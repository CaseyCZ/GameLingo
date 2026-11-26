"""Unlabelled video evaluation: report OCR disagreements and sequence behaviour, not accuracy."""
import json,sys,statistics,re,unicodedata
from pathlib import Path
from difflib import SequenceMatcher
sys.path.insert(0,str(Path(__file__).resolve().parent.parent/"ocr-lab"))
from score_combination import load,groups,iou
def normalize(s):
    return " ".join(re.findall(r"\w+",unicodedata.normalize("NFKC",s).casefold(),flags=re.UNICODE))
def text(row):
    return normalize(" ".join(l["text"] for l in sorted(row["lines"],key=lambda l:(l["box"][1],l["box"][0]))))
def sequence(rows):
    prev=None;changes=0;repeats=0;empty=0;clear=0;stable=0;positions=[];ms=[]
    for row in rows:
        current=text(row);gs=groups(row)
        ms.append(row["ocr_ms"]);empty+=not bool(current)
        if prev is not None:
            old,oldgs=prev
            if old==current and current:
                repeats+=1
                if oldgs and gs:
                    positions.append(max(iou(a["box"],b["box"]) for a in oldgs for b in gs))
            elif old!=current:changes+=1
            if old and not current:clear+=1
            if old and current and SequenceMatcher(None,old,current,autojunk=False).ratio()>=.85:stable+=1
        prev=(current,gs)
    return dict(frames=len(rows),empty_frames=empty,text_changes=changes,exact_repeats=repeats,clear_candidates=clear,
        similar_adjacent_pairs=stable,median_repeat_box_iou=statistics.median(positions) if positions else None,
        median_ocr_ms=statistics.median(ms) if ms else None,total_ocr_ms=sum(ms))
def summarize(rows,engines):
    results={}
    for e in engines:
        rs=[r for r in rows if r["engine"]==e]
        modes={m:sorted([r for r in rs if r["mode"]==m and "error" not in r],key=lambda r:r["frame"]) for m in ("raw","outline")}
        raw={r["id"]:r for r in modes["raw"]};outline={r["id"]:r for r in modes["outline"]}
        pairs=[(raw[k],outline[k]) for k in sorted(raw.keys()&outline.keys())]
        # Sequence metrics are descriptive only: repeated OCR errors can appear stable.
        result=dict(received=len(rs),expected=60,errors=[r.get("error") for r in rs if "error" in r],
            paired=len(pairs),agreement=sum(text(a)==text(b) for a,b in pairs),
            raw_only_nonempty=sum(bool(text(a)) and not text(b) for a,b in pairs),
            outline_only_nonempty=sum(bool(text(b)) and not text(a) for a,b in pairs),
            both_nonempty_disagree=sum(bool(text(a)) and bool(text(b)) and text(a)!=text(b) for a,b in pairs),
            modes={m:sequence(v) for m,v in modes.items()},host_filter_ms=sum(r["prep_host_ms"] for r in modes["outline"]))
        results[e]=result
    return results
if __name__=="__main__":
    rows=load(sys.argv[1]);results=summarize(rows,sys.argv[3:])
    Path(sys.argv[2]).write_text(json.dumps(results,indent=2),encoding="utf8")
    for e,r in results.items():print("VIDEO_OCR",e,json.dumps(r))
    if not results or any(r["received"]!=60 or r["paired"]!=30 or r["errors"] for r in results.values()):raise SystemExit("Incomplete OCR results")
