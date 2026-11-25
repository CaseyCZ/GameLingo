"""Local OCR adapters. No image or text upload."""
import io
import os
from pathlib import Path
import shutil
import subprocess
from PIL import Image, ImageOps

def preprocess(image, mode, scale):
    if scale!=100:
        image=image.resize((max(1,image.width*scale//100),max(1,image.height*scale//100)),Image.Resampling.BILINEAR)
    if mode=="gray":
        image=ImageOps.grayscale(image)
    elif mode=="contrast":
        image=ImageOps.autocontrast(ImageOps.grayscale(image))
    elif mode=="binary":
        image=ImageOps.grayscale(image).point(lambda x:255 if x>=190 else 0)
    return image

class Tesseract:
    def __init__(self):
        paths=[shutil.which("tesseract"),str(Path(os.environ.get("ProgramFiles","C:/Program Files"))/"Tesseract-OCR/tesseract.exe")]
        self.exe=next((p for p in paths if p and Path(p).is_file()),None)
        if not self.exe:
            raise RuntimeError("Tesseract chybí. Spusť winget install --exact --id UB-Mannheim.TesseractOCR")
    def read(self,image,psm=6):
        data=io.BytesIO()
        image.save(data,format="PNG")
        result=subprocess.run([self.exe,"stdin","stdout","-l","eng","--psm",str(psm)],
            input=data.getvalue(),stdout=subprocess.PIPE,stderr=subprocess.PIPE,
            timeout=10,check=True,creationflags=getattr(subprocess,"CREATE_NO_WINDOW",0))
        return result.stdout.decode("utf-8",errors="replace").strip()

class Rapid:
    def __init__(self):
        from rapidocr_onnxruntime import RapidOCR
        self.engine=RapidOCR()
    def read(self,image,psm=6):
        import numpy as np
        # RapidOCR expects OpenCV BGR, not PIL RGB.
        pixels=np.asarray(image.convert("RGB"))[:,:,::-1].copy()
        result,_=self.engine(pixels)
        if not result:
            return ""
        # Group segments by line-height rather than tiny baseline differences.
        entries=sorted(result,key=lambda e:min(p[1] for p in e[0]))
        rows=[]
        for box,text,_ in entries:
            y=sum(p[1] for p in box)/4
            x=min(p[0] for p in box)
            height=max(p[1] for p in box)-min(p[1] for p in box)
            if rows and abs(y-rows[-1][0])<=max(4,height*.5):
                rows[-1][1].append((x,text))
            else:
                rows.append((y,[(x,text)]))
        return "\n".join(" ".join(t for _,t in sorted(items)) for _,items in rows)

def make_engine(name):
    if name=="tesseract":
        return Tesseract()
    if name=="rapid":
        return Rapid()
    raise ValueError("Unknown OCR engine")

def local_translator():
    # Only an already installed English-to-Czech Argos model; no automatic download.
    import argostranslate.translate as argos
    langs=argos.get_installed_languages()
    en=next((x for x in langs if x.code=="en"),None)
    cs=next((x for x in langs if x.code=="cs"),None)
    if en is None or cs is None:
        raise RuntimeError("Chybí lokální model Argos EN -> CS (viz README).")
    translator=en.get_translation(cs)
    if translator is None:
        raise RuntimeError("Překladová cesta EN -> CS není nainstalována.")
    return translator.translate
