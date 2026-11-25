"""Measured jobs and explicit stale-result policy."""
from dataclasses import dataclass
import time

@dataclass(frozen=True)
class Ticket:
    generation: int
    epoch: int
    source: str
    frame_at: float
    accepted_at: float

@dataclass(frozen=True)
class Result:
    ticket: Ticket
    started_at: float
    finished_at: float
    text: str
    cold: bool

def eligible(result, epoch, displayed, now, enabled=True):
    return (enabled and bool(result.text.strip()) and result.ticket.epoch==epoch
            and result.ticket.generation>displayed and now-result.ticket.frame_at<=3.0)

def timings(result, ui_at=None):
    t=result.ticket
    return dict(generation=t.generation,translation_queue_ms=round((result.started_at-t.accepted_at)*1000,1),
                translation_ms=round((result.finished_at-result.started_at)*1000,1),
                frame_to_result_ms=round((result.finished_at-t.frame_at)*1000,1),
                frame_to_ui_ms="" if ui_at is None else round((ui_at-t.frame_at)*1000,1),
                cold_translation=int(result.cold))

def run_job(ticket, translate, cold, clock=time.perf_counter):
    start=clock()
    text=translate(ticket.source)
    if not isinstance(text,str) or not text.strip():
        raise RuntimeError("Prázdný překlad")
    return Result(ticket,start,clock(),text,cold)
