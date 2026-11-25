"""Local OCR adapters. No image or text upload."""
import io
import os
from pathlib import Path
import shutil
import subprocess
from PIL import Image, ImageOps
from experiments import advanced_filter,Word,clean_words

def preprocess(image, mode, scale, threshold=190, invert=False, padding=False):
    if scale!=100:
        image=image.resize((max(1,image.width*scale//100),max(1,image.height*scale//100)),Image.Resampling.BILINEAR)
    if mode=="gray":
        image=ImageOps.grayscale(image)
    elif mode=="contrast":
        image=ImageOps.autocontrast(ImageOps.grayscale(image))
    elif mode=="binary":
        image=ImageOps.grayscale(image).point(lambda x:255 if x>=threshold else 0)
    elif mode!="raw":
        image=advanced_filter(image,mode,scale,threshold)
    if invert:image=ImageOps.invert(image.convert("RGB"))
    if padding:
        # Use the corner median to avoid creating an artificial contrasting border.
        gray=ImageOps.grayscale(image)
        v=sorted([gray.getpixel((x,y)) for x,y in [(0,0),(0,gray.height-1),(gray.width-1,0),(gray.width-1,gray.height-1)]])[2]
        image=ImageOps.expand(image.convert("RGB"),border=12,fill=(v,v,v))
    return image

class Tesseract:
    def __init__(self):
        paths=[shutil.which("tesseract"),str(Path(os.environ.get("ProgramFiles","C:/Program Files"))/"Tesseract-OCR/tesseract.exe")]
        self.exe=next((p for p in paths if p and Path(p).is_file()),None)
        if not self.exe:
            raise RuntimeError("Tesseract chybí. Spusť winget install --exact --id UB-Mannheim.TesseractOCR")
    def read(self,image,psm=6,geometry=False,confidence=0):
        data=io.BytesIO()
        image.save(data,format="PNG")
        args=[self.exe,"stdin","stdout","-l","eng","--psm",str(psm)]
        if geometry or confidence:args.append("tsv")
        result=subprocess.run(args,
            input=data.getvalue(),stdout=subprocess.PIPE,stderr=subprocess.PIPE,
            timeout=10,check=True,creationflags=getattr(subprocess,"CREATE_NO_WINDOW",0))
        text=result.stdout.decode("utf-8",errors="replace").strip()
        if not (geometry or confidence):return text
        import csv
        lines={}
        for row in csv.DictReader(io.StringIO(text),delimiter="\t",quoting=csv.QUOTE_NONE):
            if row.get("level")!="5" or not row.get("text","").strip():continue
            try:
                w=Word(row["text"],float(row["left"]),float(row["top"]),float(row["width"]),float(row["height"]),float(row["conf"]))
            except (ValueError,KeyError):continue
            key=tuple(row[k] for k in ("page_num","block_num","par_num","line_num"))
            lines.setdefault(key,[]).append(w)
        return clean_words(list(lines.values()),geometry,confidence)

class Rapid:
    def __init__(self):
        from rapidocr_onnxruntime import RapidOCR
        self.engine=RapidOCR()
    def read(self,image,psm=6,geometry=False,confidence=0):
        import numpy as np
        # RapidOCR expects OpenCV BGR, not PIL RGB.
        pixels=np.asarray(image.convert("RGB"))[:,:,::-1].copy()
        result,_=self.engine(pixels)
        if not result:
            return ""
        # Group segments by line-height rather than tiny baseline differences.
        entries=sorted(result,key=lambda e:min(p[1] for p in e[0]))
        rows=[]
        for box,text,score in entries:
            y=sum(p[1] for p in box)/4
            x=min(p[0] for p in box)
            height=max(p[1] for p in box)-min(p[1] for p in box)
            word=Word(text,x,min(p[1] for p in box),max(p[0] for p in box)-x,height,score*100)
            if rows and abs(y-rows[-1][0])<=max(4,height*.5):
                rows[-1][1].append(word)
            else:
                rows.append((y,[word]))
        return clean_words([sorted(items,key=lambda w:w.x) for _,items in rows],geometry,confidence)

class WindowsOCR:
    """Public Windows OCR API. Runs on the same thread that owns the engine."""
    def __init__(self):
        import asyncio
        from winrt.windows.media.ocr import OcrEngine
        from winrt.windows.globalization import Language
        tag=next((l.language_tag for l in OcrEngine.available_recognizer_languages
                  if l.language_tag.lower().split("-")[0]=="en"),None)
        if tag is None:
            raise RuntimeError("Windows OCR: nainstaluj anglický jazyk a OCR v Nastavení Windows.")
        self.engine=OcrEngine.try_create_from_language(Language(tag))
        if self.engine is None:raise RuntimeError("Windows OCR EN není dostupné.")
        self.loop=asyncio.new_event_loop()
    def read(self,image,psm=6,geometry=False,confidence=0):
        from winrt.windows.media.ocr import OcrEngine
        from winrt.windows.graphics.imaging import SoftwareBitmap,BitmapPixelFormat,BitmapAlphaMode
        from winrt.windows.storage.streams import DataWriter
        if max(image.size)>OcrEngine.max_image_dimension:
            image=image.copy()
            image.thumbnail((OcrEngine.max_image_dimension,)*2)
        rgba=image.convert("RGBA")
        writer=DataWriter()
        try:
            writer.write_bytes(rgba.tobytes())
            bitmap=SoftwareBitmap.create_copy_from_buffer(writer.detach_buffer(),BitmapPixelFormat.RGBA8,
                      rgba.width,rgba.height,BitmapAlphaMode.STRAIGHT)
            try:
                async def read_async():
                    return await self.engine.recognize_async(bitmap)
                result=self.loop.run_until_complete(read_async())
                lines=[[Word(w.text,w.bounding_rect.x,w.bounding_rect.y,w.bounding_rect.width,w.bounding_rect.height)
                        for w in line.words] for line in result.lines]
                return clean_words(lines,geometry,0)
            finally:bitmap.close()
        finally:writer.close()
    def close(self):self.loop.close()

def make_engine(name):
    if name=="tesseract":
        return Tesseract()
    if name=="rapid":
        return Rapid()
    if name=="windows":
        return WindowsOCR()
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
