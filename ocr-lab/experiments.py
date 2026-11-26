"""Local experimental filters and bounded subtitle stabilization."""
import re
from dataclasses import dataclass
from statistics import median
import numpy as np
from PIL import Image, ImageOps, ImageFilter
from core import normalize, similarity
from third_party.sublens_filter import isolate_bright_text

FILTERS=("raw","gray","contrast","binary","white","outline","adaptive","darkbox","sharpen")
def advanced_filter(image, mode, scale=100, threshold=190):
    gray=ImageOps.grayscale(image)
    if mode=="white":
        a=np.asarray(image.convert("RGB"),dtype=np.int16)
        mask=(a.min(axis=2)>=threshold)&((a.max(axis=2)-a.min(axis=2))<=55)
        return Image.fromarray(np.where(mask,0,255).astype("uint8"))
    if mode=="outline":
        return isolate_bright_text(image,max(1,round(scale/100)))
    if mode=="adaptive":
        # Local background estimate, not a fixed global brightness.
        a=np.asarray(gray,dtype=np.int16)
        bg=np.asarray(gray.filter(ImageFilter.BoxBlur(7)),dtype=np.int16)
        mask=(a>=max(100,threshold-45))&((a-bg)>=22)
        return Image.fromarray(np.where(mask,0,255).astype("uint8"))
    if mode=="darkbox":
        a=np.asarray(gray,dtype=np.int16)
        dark=np.asarray(gray.filter(ImageFilter.BoxBlur(5)),dtype=np.int16)<120
        mask=(a>=max(110,threshold-45))&dark
        return Image.fromarray(np.where(mask,0,255).astype("uint8"))
    if mode=="sharpen":
        return ImageOps.autocontrast(gray).filter(ImageFilter.UnsharpMask(radius=1.5,percent=160,threshold=3))
    raise ValueError(mode)

@dataclass(frozen=True)
class Word:
    text:str
    x:float
    y:float
    w:float
    h:float
    confidence:float=100

def clean_words(lines, geometry=False, confidence=0):
    # Geometry and confidence are independent switches. Never require a dictionary;
    # preserve short words, numbers, and names.
    lines=[[w for w in line if w.confidence>=confidence and w.text.strip()] for line in lines]
    heights=[w.h for line in lines for w in line if any(c.isalpha() for c in w.text) and w.h>0]
    ref=median(heights) if heights else 0
    if geometry and ref:
        lines=[[w for w in line if .55*ref<=w.h<=1.9*ref] for line in lines]
    return "\n".join(" ".join(w.text for w in line) for line in lines if line)

def clean_text(raw):
    # Conservative removal of standalone OCR artifacts, not arbitrary words.
    lines=[]
    for line in raw.splitlines():
        words=[w for w in line.split() if not re.fullmatch(r"[|_=~*\\/]{1,}",w)]
        text=" ".join(words)
        if text and not re.fullmatch(r"[\W_]+",text):
            lines.append(text)
    return "\n".join(lines)

def equivalent(a,b):
    a,b=normalize(a),normalize(b)
    if a.casefold()==b.casefold():return True
    # A fuzzy match is evidence, not identity for short or meaning-changing words.
    if min(len(a),len(b))<20:return False
    aw,bw=a.casefold().split(),b.casefold().split()
    if len(aw)!=len(bw):return False
    changed=[(x,y) for x,y in zip(aw,bw) if x!=y]
    if len(changed)!=1:return False
    x,y=changed[0]
    if min(len(x),len(y))<5 or x.isdigit() or y.isdigit():return False
    return similarity(a,b)>=.96 and similarity(x,y)>=.8

class Adaptive:
    """Hold only until a second similar observation or the deadline."""
    def __init__(self):
        self.reset()
    def reset(self):
        self.candidate=""
        self.since=None
        self.accepted=""
    def accept(self,raw,now,mode="adaptive",wait_ms=350):
        text=normalize(raw)
        if not text:
            self.candidate=""
            self.since=None
            return "",now
        if text==self.accepted:
            self.candidate=""
            self.since=None
            return text,now
        if self.since is None:
            self.since=now
        first=self.since
        similar=self.candidate and equivalent(self.candidate,text)
        if mode=="rolling":
            # Wait briefly for growing text; punctuation can finish a cue early.
            ready=(similar or text.endswith((".","!","?","…")))
        else:
            ready=similar
        if ready or (now-first)*1000>=wait_ms:
            self.accepted=text
            self.candidate=""
            self.since=None
            return text,first
        self.candidate=text
        return None,first

class ImageGate:
    def __init__(self):
        self.last=None
        self.last_at=0
    def allow(self,image,now,enabled=True,refresh_ms=700):
        if not enabled:return True
        # Exact identity on the processed ROI, not approximate similarity that
        # could accidentally skip a changed word. Periodic full OCR is mandatory.
        key=(image.mode,image.size,image.tobytes())
        changed=key!=self.last
        if changed or (now-self.last_at)*1000>=refresh_ms:
            self.last=key
            self.last_at=now
            return True
        return False
