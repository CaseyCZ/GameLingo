"""Score actual ML Kit output, preserving per-frame JSONL for audit."""
import json,sys,os
from collections import defaultdict
from pathlib import Path
ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT.parent/"ocr-lab"))
from core import normalize,percentile
from auto_benchmark import distance
def main():
    path=ROOT/"results/results.jsonl"
    rows=[json.loads(x) for x in path.read_text(encoding="utf8").splitlines() if x.strip()]
    groups=defaultdict(list)
    for row in rows:groups[row["dataset"],row["config"]].append(row)
    output=[]
    for (dataset,config),items in sorted(groups.items()):
        total=48 if dataset=="synthetic" else 24
        if len(items)!=total or len({r["id"] for r in items})!=total:continue
        for cleanup in ("actual","geometry","confidence"):
            positives=[r for r in items if r["expected"]]
            blanks=[r for r in items if not r["expected"]]
            pairs=[(normalize(r["expected"]).casefold(),normalize(r[cleanup]).casefold()) for r in positives]
            latency=[r["ocr_ms"]+r["resize_ms"]+r["probe_ms"] for r in items]
            result=dict(dataset=dataset,config=config,cleanup=cleanup,
                exact=sum(a==b for a,b in pairs),total=len(pairs),
                cer=round(sum(distance(a,b) for a,b in pairs)/sum(len(a) for a,b in pairs),4),
                missed=sum(not b for a,b in pairs),
                false_blank=sum(bool(normalize(r[cleanup])) for r in blanks),blanks=len(blanks),
                mean_ms=round(sum(latency)/len(latency),1),p50_ms=round(percentile(latency,.5),1),
                p95_ms=round(percentile(latency,.95),1))
            output.append(result)
            print("MLKIT",json.dumps(result))
    (path.parent/"summary.json").write_text(json.dumps(output,indent=2),encoding="utf8")
    summary=os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary,"a",encoding="utf8") as out:
            out.write("## ML Kit 16.0.1 — Android emulator\n\n")
            for ds in ("public","synthetic"):
                out.write(f"### {ds}\n\n| Config | Cleanup | Exact | CER | Missed | False positives | Mean ms | P95 ms |\n|---|---|---:|---:|---:|---:|---:|---:|\n")
                for r in sorted((x for x in output if x["dataset"]==ds),key=lambda x:(x["cer"],x["false_blank"],x["p95_ms"])):
                    out.write(f"| {r['config']} | {r['cleanup']} | {r['exact']}/{r['total']} | {r['cer']} | {r['missed']} | {r['false_blank']}/{r['blanks']} | {r['mean_ms']} | {r['p95_ms']} |\n")
            out.write("\nFilter preprocessing was performed on host, not included in emulator timings. Auto includes probe cost. Geometry/confidence postprocessing time not included. No physical TV or iOS measurements.\n")
    expected=72*(9+8+5)
    if len(rows)!=expected or len(output)!=2*22*3:raise SystemExit(f"Incomplete: {len(rows)}/{expected} OCR calls, {len(output)}/132 summary rows")
if __name__=="__main__":main()
