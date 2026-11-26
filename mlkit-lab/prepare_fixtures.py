"""Reuse exactly the existing OCR Lab datasets and filters at 100%."""
import sys,json,time
from pathlib import Path
ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT.parent/"ocr-lab"))
from auto_benchmark import fixtures
from public_fixtures import load_public
from engines import preprocess
from experiments import FILTERS

def main():
    dest=ROOT/"app/src/androidTest/assets"
    dest.mkdir(parents=True,exist_ok=True)
    rows=[]
    for dataset,scenes in (("synthetic",fixtures()),("public",load_public())):
        for i,(scene,expected,im) in enumerate(scenes):
            ident=f"{dataset}_{i:03d}"
            times={}
            for mode in FILTERS:
                start=time.perf_counter()
                prepared=preprocess(im,mode,100)
                times[mode]=round((time.perf_counter()-start)*1000,3)
                prepared.save(dest/f"{ident}_{mode}.png")
            rows.append(dict(id=ident,dataset=dataset,scene=scene,expected=expected,prep_host_ms=times))
    assert len(rows)==72,len(rows)
    (dest/"manifest.json").write_text(json.dumps(rows,ensure_ascii=False),encoding="utf8")
    print(f"Prepared {len(rows)} reference frames and {len(FILTERS)} filters")
if __name__=="__main__":main()
