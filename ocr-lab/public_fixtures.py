"""Pinned public reference data; no frames are republished or uploaded."""
import csv
import io
import random
import re
import urllib.request
from PIL import Image

BASE="https://raw.githubusercontent.com/GeekLinkDev/geeklink-ocr-benchmark/baa9ccbb7ffad36d56cc0ea115256a09bdf427f4/"
def get(path,limit):
    with urllib.request.urlopen(BASE+path,timeout=30) as r:
        data=r.read(limit+1)
    if len(data)>limit:raise ValueError("Reference asset too large")
    return data
def load_public():
    rows=list(csv.DictReader(io.StringIO(get("data/manifest.csv",2_000_000).decode("utf-8-sig"))))
    rng=random.Random(284)
    selected=[]
    for watermark in ("false","true"):
        eligible=[r for r in rows if r["lang"]=="en" and r["has_watermark"].lower()==watermark]
        eligible.sort(key=lambda x:x["id"])
        selected+=rng.sample(eligible,min(12,len(eligible)))
    if len(selected)<20:raise RuntimeError("Reference manifest changed")
    result=[]
    for row in selected:
        path=row["image"]
        if not re.fullmatch(r"data/images/[A-Za-z0-9_-]+\.jpg",path):raise ValueError("Unexpected image path")
        with Image.open(io.BytesIO(get(path,8_000_000))) as im:
            # Fixed bottom ROI for every engine; not a dataset ground-truth bounding box.
            img=im.convert("RGB").crop((0,int(im.height*.65),im.width,im.height))
            result.append(("public/"+row["id"],row["ground_truth"],img))
    return result
