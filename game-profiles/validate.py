import json, math
from pathlib import Path
ROOT=Path(__file__).resolve().parent

def validate(p):
    if p["schemaVersion"]!=1 or p["id"]!="gta-vi":raise ValueError("unsupported profile")
    if set(p["platforms"])!={"xbox-series","ps5"}:raise ValueError("shared console profile required")
    if p["coordinateSystem"]!="normalized-ltrb-video-content-top-left":raise ValueError("coordinate system")
    zones=p["zones"]
    if p["maxZones"]!=10 or not 1<=len(zones)<=10:raise ValueError("zone count")
    if len({z["id"] for z in zones})!=len(zones):raise ValueError("duplicate zone")
    for z in zones:
        r=z["rect"]
        if len(r)!=4 or not all(type(v) in (float,int) and math.isfinite(v) and 0<=v<=1 for v in r) or not(r[0]<r[2] and r[1]<r[3]):raise ValueError("invalid rect")
    if p["ocr"]["preprocessing"]!="none" or p["ocr"]["manualOutputZones"] or p["ocr"]["overlay"]!="detected-text":raise ValueError("automatic defaults")
    if p["releaseReady"] and (not all(p["validation"][key] is True for key in ("xbox","ps5","referenceAnnotated")) or p["status"]!="validated" or p["pendingZones"] or any(z["status"]!="validated" for z in zones)):raise ValueError("unverified release")
    return p

if __name__=="__main__":
    p=validate(json.loads((ROOT/"gta-vi.json").read_text()))
    catalog=json.loads((ROOT/"catalog.json").read_text())
    assert catalog["profiles"]==[dict(id=p["id"],file="gta-vi.json",status=p["status"],releaseReady=p["releaseReady"])]
    print("PROFILE_VALID",p["id"],p["status"])
