"""Score iOS simulator output; fail on incomplete or duplicate rows."""
import json,re,sys,os
from collections import defaultdict
from pathlib import Path
ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT.parent/"ocr-lab"))
from core import normalize,percentile
from auto_benchmark import distance
path=ROOT/"results"
rows=[]
for line in (path/"xcodebuild.log").read_text(errors="replace").splitlines():
    if line.startswith("IOS_OCR "):
        rows.append(json.loads(line[len("IOS_OCR "):]))
groups=defaultdict(list)
for row in rows:groups[row["engine"],row["config"]].append(row)
report=[]
for (engine,config),items in sorted(groups.items()):
    if len(items)!=24 or len({r["id"] for r in items})!=24:continue
    pairs=[(normalize(r["expected"]).casefold(),normalize(r["actual"]).casefold()) for r in items]
    latency=[r["ocr_ms"]+r["resize_ms"] for r in items]
    result=dict(engine=engine,config=config,exact=sum(a==b for a,b in pairs),total=len(items),
        cer=round(sum(distance(a,b) for a,b in pairs)/sum(len(a) for a,b in pairs),4),
        missed=sum(not b for a,b in pairs),mean_ms=round(sum(latency)/len(latency),1),
        p95_ms=round(percentile(latency,.95),1))
    report.append(result)
    print("IOS_BENCH",json.dumps(result))
(path/"rows.json").write_text(json.dumps(rows,indent=2),encoding="utf8")
(path/"summary.json").write_text(json.dumps(report,indent=2),encoding="utf8")
if os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(os.environ["GITHUB_STEP_SUMMARY"],"a") as out:
        out.write("## iOS simulator OCR comparison\n\n| Engine | Config | Exact | CER | Missed | Mean ms | P95 ms |\n|---|---|---:|---:|---:|---:|---:|\n")
        for r in report:out.write(f"| {r['engine']} | {r['config']} | {r['exact']}/{r['total']} | {r['cer']} | {r['missed']} | {r['mean_ms']} | {r['p95_ms']} |\n")
        out.write("\n24 identical public crops. Host preprocessing excluded; resize included. Simulator timings are not physical iPhone timings.\n")
if len(rows)!=624 or len(report)!=26:raise SystemExit(f"Incomplete: {len(rows)}/624 calls, {len(report)}/26 configs")
