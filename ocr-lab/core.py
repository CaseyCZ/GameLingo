"""Pure timing and dialogue logic; no desktop or network dependencies."""
from dataclasses import dataclass
import math
import re
from difflib import SequenceMatcher

def normalize(text):
    return " ".join(text.split())

def similarity(a,b):
    return SequenceMatcher(None, normalize(a).casefold(), normalize(b).casefold(), autojunk=False).ratio()

def percentile(values, q):
    if not values:
        return 0.0
    data = sorted(values)
    return data[max(0, math.ceil(len(data)*q)-1)]

def rolling_delta(old, new):
    """Only trim a sufficiently long, exact suffix/prefix match."""
    a,b=normalize(old).split(),normalize(new).split()
    for n in range(min(len(a),len(b)),1,-1):
        if [w.casefold() for w in a[-n:]] == [w.casefold() for w in b[:n]]:
            return " ".join(b[n:])
    return normalize(new)

@dataclass(frozen=True)
class Event:
    kind: str
    generation: int
    text: str
    delta: str = ""

class Dialogue:
    def __init__(self):
        self.current=""
        self.generation=0
        self.blanks=0

    def accept(self, raw, rolling=False):
        text=normalize(raw)
        if not text:
            self.blanks+=1
            if self.blanks>=2 and self.current:
                self.current=""
                self.generation+=1
                return Event("clear",self.generation,"")
            return Event("blank",self.generation,self.current)
        self.blanks=0
        if text==self.current:
            return Event("repeat",self.generation,text)
        delta=rolling_delta(self.current,text) if rolling else text
        self.current=text
        self.generation+=1
        return Event("new",self.generation,text,delta)

# Deterministic replay: one reader, newest frame available when free.
def replay_indices(count, frame_ms, costs_ms):
    if count<0 or frame_ms<=0:
        raise ValueError("Invalid timeline")
    index=0
    ready=0.0
    for cost in costs_ms:
        if not math.isfinite(cost) or cost<0:
            raise ValueError("Invalid cost")
        index=max(index,int(ready//frame_ms))
        if index>=count:
            break
        start=max(ready,index*frame_ms)
        finish=start+cost
        yield index,start,finish
        index+=1
        ready=finish

def score_cues(expected, observed):
    """observed = (cue index at sampled frame, accepted source).
    Match against the sampled cue, not against an unrelated later cue.
    """
    hit=set()
    wrong=0
    duplicate=0
    for idx,text in observed:
        if not 0<=idx<len(expected) or similarity(expected[idx],text)<0.9:
            wrong+=1
        elif idx in hit:
            duplicate+=1
        else:
            hit.add(idx)
    return dict(total=len(expected),recognized=len(hit),missed=len(expected)-len(hit),
                wrong=wrong,duplicates=duplicate)
