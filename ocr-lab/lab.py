"""Interactive Windows screen OCR laboratory. All capture is local and opt-in."""
import ctypes
import csv
import queue
import threading
import time
from collections import deque
from dataclasses import dataclass
import tkinter as tk
from tkinter import ttk,messagebox,filedialog
from PIL import Image,ImageTk
import mss
from core import Dialogue,percentile
from engines import make_engine,preprocess,local_translator
from translation_timing import Ticket,eligible,timings,run_job
from stabilizer import Stabilizer
from latest_slot import LatestSlot
from experiments import FILTERS,Adaptive,ImageGate,clean_text
import json

@dataclass(frozen=True)
class Config:
    engine:str
    filter:str
    scale:int
    interval:int
    psm:int
    rolling:bool
    translate:bool
    stabilize:bool=False
    mode:str='off'
    wait_ms:int=350
    threshold:int=190
    invert:bool=False
    padding:bool=False
    geometry:bool=False
    confidence:int=0
    clean:bool=False
    gate:bool=False
    hold:int=3000

def dpi_aware():
    try:
        ctypes.windll.user32.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4))
    except (AttributeError,OSError):
        pass

def primary(sct):
    return next((m for m in sct.monitors[1:] if m["left"]==0 and m["top"]==0),sct.monitors[1])

def grab(sct,region):
    shot=sct.grab(region)
    return Image.frombytes("RGB",shot.size,shot.rgb)

class Session:
    def __init__(self,region,config):
        self.region=dict(region)
        self.config=config
        self.stop=threading.Event()
        self.messages=queue.Queue(maxsize=1)
        self.rows=deque(maxlen=10000)
        self.lock=threading.Lock()
        self.epoch=0
        self.displayed=-1
        self.metrics=dict(submitted=0,completed=0,shown=0,stale=0,replaced=0,errors=0,held=0)
        self.ui_times=deque(maxlen=1000)
        self.trans_times=deque(maxlen=1000)
        self.pending=LatestSlot()
        self.result=None
        self.error=""
        self.ready=threading.Event()
        self.warmup_ms=None
        self.worker=threading.Thread(target=self.translate_loop,daemon=True)
        self.thread=threading.Thread(target=self.run,daemon=True)
        self.worker.start()
        self.thread.start()

    def emit(self,data):
        try:self.messages.get_nowait()
        except queue.Empty:pass
        self.messages.put_nowait(data)

    def record(self,row):
        keys=("elapsed_ms","engine","filter","scale","interval_ms","psm","capture_prep_ms","ocr_ms",
              "event","source","delta","translation","generation","translation_queue_ms","translation_ms",
              "frame_to_result_ms","frame_to_ui_ms","cold_translation","warmup_ms","stabilize","raw_source","settings","segment","first_seen_to_ui_ms","error")
        with self.lock:self.rows.append({k:row.get(k,"") for k in keys})

    def note(self,key):
        with self.lock:self.metrics[key]+=1

    def invalidate(self):
        with self.lock:
            self.epoch+=1
            if self.result is not None and self.result.ticket.generation>self.displayed:
                self.metrics["stale"]+=1
            self.result=None
        if self.ready.is_set() and self.pending.clear():self.note("replaced")

    def take_ui(self,now):
        with self.lock:
            result=self.result
            if result is None:return None
            if result.ticket.generation<=self.displayed:return None
            if not eligible(result,self.epoch,self.displayed,now,self.config.translate):
                self.metrics["stale"]+=1
                self.result=None
                return None
            self.displayed=result.ticket.generation
            self.metrics["shown"]+=1
            self.ui_times.append((now-result.ticket.frame_at)*1000)
        self.record(dict(event="ui_displayed",source=result.ticket.source,
                         translation=result.text,settings=result.ticket.settings,segment=result.ticket.segment,**timings(result,now)))
        return result.text

    def translate_loop(self):
        translator=None
        try:
            while not self.stop.is_set():
                ticket=self.pending.take()
                if ticket is None:break
                if ticket=="warmup":
                    if translator is None:
                        t=time.perf_counter()
                        translator=local_translator()
                        translator("Please wait here.")
                        with self.lock:self.warmup_ms=(time.perf_counter()-t)*1000
                        self.record(dict(event="model_warmup",warmup_ms=round(self.warmup_ms,1)))
                    self.ready.set()
                    continue
                if self.stop.is_set():break
                with self.lock:
                    valid=ticket.epoch==self.epoch and self.config.translate
                if not valid:
                    self.note("stale")
                    continue
                self.note("submitted")
                result=run_job(ticket,translator,False)
                self.note("completed")
                with self.lock:
                    self.trans_times.append((result.finished_at-result.started_at)*1000)
                    valid=not self.stop.is_set() and eligible(result,self.epoch,self.displayed,time.perf_counter(),self.config.translate)
                    if valid:
                        if self.result is not None and self.result.ticket.generation>self.displayed:
                            self.metrics["stale"]+=1
                        self.result=result
                    else:self.metrics["stale"]+=1
                self.record(dict(event="translation_completed" if valid else "translation_stale",
                                 source=ticket.source,translation=result.text,settings=ticket.settings,segment=ticket.segment,**timings(result)))
        except Exception as exc:
            self.note("errors")
            with self.lock:self.error=f"{type(exc).__name__}: {exc}"
            self.ready.set()

    def run(self):
        engines={}
        tracker=Dialogue()
        stabilizer=Stabilizer()
        adaptive=Adaptive()
        gate=ImageGate()
        segment=0
        times=deque(maxlen=1000)
        counts={"new":0,"repeat":0,"blank":0,"clear":0,"held":0,"skipped":0}
        last_config=None
        generation=0
        start=time.perf_counter()
        try:
            with mss.mss() as sct:
                while not self.stop.is_set():
                    with self.lock:
                        cfg=self.config
                    if cfg!=last_config:
                        tracker=Dialogue()
                        stabilizer=Stabilizer()
                        adaptive=Adaptive()
                        gate=ImageGate()
                        segment+=1
                        settings=json.dumps(cfg.__dict__,sort_keys=True)
                        self.record(dict(event="settings",settings=settings,segment=segment))
                        times.clear()
                        counts=dict.fromkeys(counts,0)
                        self.invalidate()
                        last_config=cfg
                    if cfg.translate and not self.ready.is_set():
                        self.emit({"status":"Připravuji lokální překladač – snímání ještě neběží."})
                        self.pending.put("warmup")
                        while not self.stop.is_set() and cfg==self.config and not self.ready.wait(.05):pass
                        if self.stop.is_set():break
                        if cfg!=self.config:continue
                    if cfg.engine not in engines:
                        self.emit({"status":"Načítám "+cfg.engine+"..."})
                        try:engines[cfg.engine]=make_engine(cfg.engine)
                        except Exception as exc:
                            self.record(dict(event="engine_error",engine=cfg.engine,settings=settings,segment=segment,error=repr(exc)))
                            self.emit({"status":f"OCR {cfg.engine} nedostupné: {exc}. Vyber jiný engine."})
                            self.stop.wait(.5)
                            continue
                    with self.lock:
                        if cfg!=self.config:continue
                        capture_epoch=self.epoch
                    tick=time.perf_counter()
                    image=grab(sct,self.region)
                    prep=preprocess(image,cfg.filter,cfg.scale,cfg.threshold,cfg.invert,cfg.padding)
                    captured=time.perf_counter()
                    if not gate.allow(prep,tick,cfg.gate):
                        counts["skipped"]+=1
                        self.record(dict(event="image_skipped",settings=settings,segment=segment,
                                         elapsed_ms=round((tick-start)*1000,1)))
                        self.stop.wait(max(0,cfg.interval/1000-(time.perf_counter()-tick)))
                        continue
                    try:
                        raw=engines[cfg.engine].read(prep,cfg.psm,cfg.geometry,cfg.confidence)
                    except Exception as exc:
                        self.record(dict(event="ocr_error",engine=cfg.engine,settings=settings,segment=segment,error=repr(exc)))
                        self.emit({"status":f"OCR chyba: {exc}. Změň nastavení nebo engine."})
                        self.stop.wait(.5)
                        continue
                    end=time.perf_counter()
                    if self.stop.is_set():break
                    with self.lock:
                        if cfg!=self.config or capture_epoch!=self.epoch:continue
                    original=clean_text(raw) if cfg.clean else raw
                    first_seen=tick
                    if cfg.mode in ("adaptive","rolling"):
                        filtered,first_seen=adaptive.accept(original,tick,cfg.mode,cfg.wait_ms)
                    elif cfg.mode=="exact" or cfg.stabilize:
                        filtered=stabilizer.accept(original)
                    else:filtered=original
                    if filtered is None:
                        kind="held"
                        source=tracker.current
                        delta=""
                        self.note("held")
                    else:
                        event=tracker.accept(filtered,cfg.rolling)
                        kind,source,delta=event.kind,event.text,event.delta
                        if kind=="clear":
                            self.invalidate()
                            stabilizer.reset()
                            adaptive.reset()
                        elif kind=="new":
                            generation+=1
                            if cfg.translate and not self.error:
                                ticket=Ticket(generation,capture_epoch,source,tick,end,settings,segment,first_seen)
                                if self.pending.put(ticket):self.note("replaced")
                    counts[kind]+=1
                    ocr=(end-captured)*1000
                    times.append(ocr)
                    self.record({"elapsed_ms":round((tick-start)*1000,1),"engine":cfg.engine,"filter":cfg.filter,
                         "scale":cfg.scale,"interval_ms":cfg.interval,"psm":cfg.psm,
                         "capture_prep_ms":round((captured-tick)*1000,1),"ocr_ms":round(ocr,1),
                         "event":kind,"source":source,"delta":delta,"raw_source":raw,"stabilize":cfg.mode,"settings":settings,"segment":segment})
                    self.emit({"status":f"OCR {ocr:.0f} ms | průměr {sum(times)/len(times):.0f} | P95 {percentile(times,.95):.0f} ms",
                               "counts":dict(counts),"source":source,"enabled":cfg.translate})
                    self.stop.wait(max(0,cfg.interval/1000-(end-tick)))
        except Exception as exc:
            self.emit({"error":f"{type(exc).__name__}: {exc}"})
        finally:
            self.pending.close()
            for engine in engines.values():
                if hasattr(engine,"close"):engine.close()

class Lab:
    def __init__(self,root):
        self.root=root
        root.title("GameLingo OCR Lab • Windows")
        root.geometry("800x690")
        self.session=None
        self.region=None
        self.overlay=None
        self.status=tk.StringVar(value="Vyber oblast titulků. Nic se zatím nesnímá.")
        self.region_label=tk.StringVar(value="Oblast: nenastavena")
        self.source=tk.StringVar()
        self.translation=tk.StringVar()
        self.counts=tk.StringVar()
        self.translation_stats=tk.StringVar()
        self.warning=tk.StringVar()
        self.engine=tk.StringVar(value="tesseract")
        self.filter=tk.StringVar(value="binary")
        self.scale=tk.StringVar(value="100")
        self.interval=tk.StringVar(value="250")
        self.psm=tk.StringVar(value="6")
        self.rolling=tk.BooleanVar(value=False)
        self.stabilize=tk.BooleanVar(value=False)
        self.mode=tk.StringVar(value="off")
        self.wait_ms=tk.StringVar(value="350")
        self.threshold=tk.StringVar(value="190")
        self.invert=tk.BooleanVar(value=False)
        self.padding=tk.BooleanVar(value=False)
        self.geometry=tk.BooleanVar(value=False)
        self.confidence=tk.StringVar(value="0")
        self.clean=tk.BooleanVar(value=False)
        self.gate=tk.BooleanVar(value=False)
        self.hold=tk.StringVar(value="3000")
        self.font_size=tk.StringVar(value="16")
        self.preview_enabled=tk.BooleanVar(value=False)
        self.translate=tk.BooleanVar(value=False)
        self.show_overlay=tk.BooleanVar(value=True)
        top=ttk.Frame(root,padding=16)
        top.pack(fill="both",expand=True)
        ttk.Label(top,text="GameLingo OCR Lab",font=("Segoe UI",18,"bold")).pack(anchor="w")
        ttk.Label(top,text="Lokální pokusy • Windows • bez zásahu do TV").pack(anchor="w")
        area=ttk.Frame(top);area.pack(fill="x",pady=10)
        self.pick=ttk.Button(area,text="Vybrat oblast myší",command=self.select_region)
        self.pick.pack(side="left")
        ttk.Label(area,textvariable=self.region_label).pack(side="left",padx=10)
        tabs=ttk.Notebook(top)
        tabs.pack(fill="x",pady=4)
        ocr=ttk.Frame(tabs,padding=5)
        behavior=ttk.Frame(tabs,padding=5)
        display=ttk.Frame(tabs,padding=5)
        tabs.add(ocr,text="OCR a obraz")
        tabs.add(behavior,text="Text a stabilizace")
        tabs.add(display,text="Překlad a výstup")
        def choice(parent,i,label,var,options):
            row,col=divmod(i,3)
            ttk.Label(parent,text=label).grid(row=row*2,column=col,sticky="w",padx=3)
            ttk.Combobox(parent,textvariable=var,values=options,state="readonly",width=12).grid(row=row*2+1,column=col,padx=3,sticky="w")
        for i,(label,var,options) in enumerate([
            ("OCR engine",self.engine,["tesseract","rapid","windows"]),
            ("Filtr",self.filter,FILTERS),
            ("Měřítko %",self.scale,["50","75","100","125","150","200"]),
            ("Interval ms",self.interval,["100","150","200","250","350","500","800","1000"]),
            ("Tesseract PSM",self.psm,["3","6","7","11","13"]),
            ("Práh jasu",self.threshold,["130","150","170","190","210","230"])]):
            choice(ocr,i,label,var,options)
        ttk.Checkbutton(ocr,text="Invertovat",variable=self.invert).grid(row=4,column=0,sticky="w")
        ttk.Checkbutton(ocr,text="Přidat okraj",variable=self.padding).grid(row=4,column=1,sticky="w")
        ttk.Checkbutton(ocr,text="Přeskočit stejný obraz",variable=self.gate).grid(row=5,column=0,columnspan=3,sticky="w")
        choice(behavior,0,"Stabilizace",self.mode,["off","exact","adaptive","rolling"])
        choice(behavior,1,"Max. čekání ms",self.wait_ms,["150","250","350","500","750"])
        choice(behavior,2,"Důvěra % (Tess/Rapid)",self.confidence,["0","25","40","55","70","85"])
        ttk.Checkbutton(behavior,text="Velikost znaků (geometrie)",variable=self.geometry).grid(row=2,column=0,columnspan=3,sticky="w")
        ttk.Checkbutton(behavior,text="Odstranit samostatné rušivé symboly",variable=self.clean).grid(row=3,column=0,columnspan=3,sticky="w")
        ttk.Checkbutton(behavior,text="Sledovat překryv slov (diagnostika)",variable=self.rolling).grid(row=4,column=0,columnspan=3,sticky="w")
        choice(display,0,"Zobrazení ms",self.hold,["1000","2000","3000","5000","8000"])
        choice(display,1,"Velikost písma",self.font_size,["12","14","16","18","20","24"])
        ttk.Checkbutton(display,text="Offline překlad EN → CS (Argos)",variable=self.translate,command=self.toggle_overlay).grid(row=2,column=0,columnspan=3,sticky="w")
        ttk.Checkbutton(display,text="Plovoucí překlad mimo snímanou oblast",variable=self.show_overlay,command=self.toggle_overlay).grid(row=3,column=0,columnspan=3,sticky="w")
        presets=ttk.Frame(top);presets.pack(fill="x")
        ttk.Label(presets,text="Předvolby:").pack(side="left")
        for name in ("Původní","Hry","Video","YouTube"):
            ttk.Button(presets,text=name,command=lambda n=name:self.preset(n)).pack(side="left",padx=2)
        controls=ttk.Frame(top);controls.pack(fill="x",pady=10)
        self.start_button=ttk.Button(controls,text="Spustit",command=self.start)
        self.start_button.pack(side="left")
        ttk.Button(controls,text="Zastavit",command=self.stop).pack(side="left",padx=5)
        ttk.Button(controls,text="Export měření CSV",command=self.export).pack(side="left",padx=5)
        ttk.Label(top,textvariable=self.status,wraplength=710).pack(anchor="w")
        ttk.Label(top,textvariable=self.counts,wraplength=710).pack(anchor="w")
        ttk.Label(top,textvariable=self.translation_stats,wraplength=710).pack(anchor="w")
        ttk.Label(top,text="Rozpoznaný text",font=("Segoe UI",11,"bold")).pack(anchor="w",pady=(12,0))
        ttk.Label(top,textvariable=self.source,wraplength=720,font=("Segoe UI",11)).pack(anchor="w")
        ttk.Label(top,text="Překlad",font=("Segoe UI",11,"bold")).pack(anchor="w",pady=(12,0))
        ttk.Label(top,textvariable=self.translation,wraplength=720,font=("Segoe UI",11)).pack(anchor="w")
        ttk.Label(top,textvariable=self.warning,wraplength=700,foreground="#a04000").pack(anchor="w")
        ttk.Label(top,text="Živé počty nejsou měření vynechaných vět; pro ně použij benchmark.py.",wraplength=700).pack(side="bottom",anchor="w")
        root.protocol("WM_DELETE_WINDOW",self.close)
        root.after(25,self.poll)

    def config(self):
        return Config(self.engine.get(),self.filter.get(),int(self.scale.get()),int(self.interval.get()),
                      int(self.psm.get()),self.rolling.get(),self.translate.get(),False,self.mode.get(),int(self.wait_ms.get()),
                      int(self.threshold.get()),self.invert.get(),self.padding.get(),self.geometry.get(),
                      int(self.confidence.get()),self.clean.get(),self.gate.get(),3000)

    def preset(self,name):
        # Reset all experimental switches; presets are starting points, not winners.
        self.filter.set("binary");self.scale.set("100");self.interval.set("250");self.psm.set("6")
        self.threshold.set("190");self.mode.set("off");self.wait_ms.set("350")
        self.invert.set(False);self.padding.set(False);self.geometry.set(False)
        self.confidence.set("0");self.clean.set(False);self.gate.set(False);self.rolling.set(False)
        if name=="Hry":
            self.filter.set("outline");self.mode.set("adaptive");self.geometry.set(True);self.clean.set(True)
        elif name=="Video":
            self.filter.set("darkbox");self.mode.set("adaptive");self.clean.set(True)
        elif name=="YouTube":
            self.filter.set("outline");self.mode.set("rolling");self.rolling.set(True);self.clean.set(True)

    def select_region(self):
        if self.session and self.session.thread.is_alive():return
        self.root.withdraw()
        self.root.after(300,self.capture_selection)

    def capture_selection(self):
        try:
            with mss.mss() as sct:
                monitor=dict(primary(sct))
                image=grab(sct,monitor)
            w,h=image.size
            ratio=min(1,1100/w,650/h)
            sw,sh=max(1,int(w*ratio)),max(1,int(h*ratio))
            window=tk.Toplevel(self.root)
            window.title("Označ pouze anglické titulky • Escape = zrušit")
            canvas=tk.Canvas(window,width=sw,height=sh,highlightthickness=0,cursor="crosshair")
            canvas.pack()
            preview=ImageTk.PhotoImage(image.resize((sw,sh),Image.Resampling.LANCZOS))
            canvas.create_image(0,0,image=preview,anchor="nw")
            canvas.image=preview
            start=[0,0]
            rectangle=canvas.create_rectangle(0,0,0,0,outline="#00d8ff",width=3)
            def down(e):start[:]=[e.x,e.y]
            def move(e):canvas.coords(rectangle,*start,e.x,e.y)
            def finish():
                window.destroy()
                self.root.deiconify()
            def up(e):
                x1,x2=sorted((max(0,min(sw,start[0])),max(0,min(sw,e.x))))
                y1,y2=sorted((max(0,min(sh,start[1])),max(0,min(sh,e.y))))
                left,top=int(x1*w/sw),int(y1*h/sh)
                width,height=int(x2*w/sw)-left,int(y2*h/sh)-top
                if width<32 or height<32:
                    messagebox.showwarning("OCR Lab","Vyber oblast alespoň 32 × 32 px.",parent=window)
                    return
                self.region=dict(left=monitor["left"]+left,top=monitor["top"]+top,width=width,height=height)
                self.monitor=monitor
                self.region_label.set(f"Oblast: {width} × {height} px")
                finish()
            canvas.bind("<ButtonPress-1>",down)
            canvas.bind("<B1-Motion>",move)
            canvas.bind("<ButtonRelease-1>",up)
            window.bind("<Escape>",lambda e:finish())
            window.protocol("WM_DELETE_WINDOW",finish)
        except Exception as exc:
            self.root.deiconify()
            messagebox.showerror("OCR Lab",str(exc))

    def start(self):
        if self.session and self.session.thread.is_alive():return
        if self.region is None:
            messagebox.showinfo("OCR Lab","Nejdřív vyber oblast titulků.")
            return
        self.source.set("");self.translation.set("");self.warning.set("");self.counts.set("")
        self.status.set("Spouštím lokální OCR…")
        self.session=Session(self.region,self.config())
        self.pick.state(["disabled"]);self.start_button.state(["disabled"])
        self.toggle_overlay()

    def toggle_overlay(self):
        if self.overlay:
            self.overlay.destroy()
            self.overlay=None
        if not self.show_overlay.get() or not self.session or self.session.stop.is_set() or not self.session.thread.is_alive():return
        r,m=self.region,self.monitor
        width=min(900,m["width"]-20,max(320,r["width"]))
        height=115
        x=max(m["left"]+10,min(r["left"],m["left"]+m["width"]-width-10))
        above=r["top"]-height-12
        below=r["top"]+r["height"]+12
        y=above if above>=m["top"] else below
        if y+height>m["top"]+m["height"]:
            self.warning.set("Není místo mimo OCR oblast; plovoucí okno vypnuto.")
            return
        window=tk.Toplevel(self.root)
        window.overrideredirect(True)
        window.attributes("-topmost",True)
        window.geometry(f"{width}x{height}+{x}+{y}")
        window.configure(bg="#151923")
        self.overlay_label=tk.Label(window,textvariable=self.translation if self.translate.get() else self.source,
                 bg="#151923",fg="white",font=("Segoe UI",int(self.font_size.get())),wraplength=width-20,
                 padx=6,pady=5)
        self.overlay_label.pack(fill="x")
        ttk.Button(window,text="Zastavit OCR",command=self.stop).pack()
        self.overlay=window
        self.overlay_key=None

    def resize_overlay(self):
        if not self.overlay:return
        r,m=self.region,self.monitor
        text=self.translation.get() if self.translate.get() else self.source.get()
        key=(text,self.font_size.get())
        if key==self.overlay_key:return
        self.overlay_key=key
        width=min(1100,m["width"]-20,max(320,r["width"]))
        x=max(m["left"]+10,min(r["left"],m["left"]+m["width"]-width-10))
        above=r["top"]-m["top"]-12
        below=m["top"]+m["height"]-r["top"]-r["height"]-12
        available=max(above,below)
        self.overlay_label.configure(wraplength=width-20,font=("Segoe UI",int(self.font_size.get())))
        self.overlay.update_idletasks()
        height=self.overlay.winfo_reqheight()
        for font in range(int(self.font_size.get())-1,9,-1):
            if height<=available:break
            self.overlay_label.configure(font=("Segoe UI",font))
            self.overlay.update_idletasks()
            height=self.overlay.winfo_reqheight()
        if height>available:
            self.overlay.withdraw()
            self.warning.set("Překlad se nevejde mimo OCR oblast; je v hlavním okně.")
            return
        y=r["top"]-height-12 if above>=height else r["top"]+r["height"]+12
        self.overlay.geometry(f"{width}x{height}+{x}+{y}")
        self.overlay.deiconify()

    def stop(self):
        if self.session:
            self.session.stop.set()
            self.session.pending.close()
        if self.overlay:
            self.overlay.destroy();self.overlay=None
        self.status.set("Zastavuji…")

    def poll(self):
        if self.session:
            s=self.session
            cfg=self.config()
            if cfg!=s.config:
                with s.lock:s.config=cfg
                s.invalidate()
                self.translation.set("")
            try:
                d=s.messages.get_nowait()
                if "error" in d:
                    self.warning.set(d["error"])
                    self.stop()
                else:
                    self.status.set(d["status"])
                    if "counts" in d:
                        c=d["counts"]
                        self.counts.set(f"Nové: {c['new']} | stejné: {c['repeat']} | prázdné: {c['blank']} | čeká: {c['held']} | stejné obrazy: {c['skipped']}")
                        self.source.set(d["source"])
            except queue.Empty:pass
            # Independent result polling: no dependency on capture/OCR completion.
            if not s.stop.is_set():
                text=s.take_ui(time.perf_counter())
                if text is not None:self.translation.set(text)
                with s.lock:
                    m=dict(s.metrics)
                    ui=list(s.ui_times)
                    tr=list(s.trans_times)
                    error=s.error
                    warm=s.warmup_ms
                    result=s.result
                if not cfg.translate or result is None:
                    self.translation.set("")
                elif (time.perf_counter()-result.finished_at)*1000>int(self.hold.get()):
                    self.translation.set("")
                if error:self.warning.set(error)
                self.translation_stats.set(
                    f"Překlad P95 {percentile(tr,.95):.0f} ms | Snímek → UI P95 {percentile(ui,.95):.0f} ms | Příprava {warm or 0:.0f} ms\n"
                    f"Zahájeno {m['submitted']} | hotovo {m['completed']} | zobrazeno {m['shown']} | staré {m['stale']} | nahrazené {m['replaced']} | chyby {m['errors']}")
            self.resize_overlay()
            if not s.thread.is_alive():
                self.pick.state(["!disabled"]);self.start_button.state(["!disabled"])
                self.status.set("Zastaveno. Výsledky můžeš exportovat.")
                if self.overlay:self.overlay.destroy();self.overlay=None
        self.root.after(25,self.poll)

    def export(self):
        if not self.session:return
        with self.session.lock:rows=[dict(row) for row in self.session.rows]
        if not rows:return
        if not messagebox.askyesno("Export","CSV obsahuje rozpoznaný text i překlad. Uložit lokálně?"):return
        path=filedialog.asksaveasfilename(defaultextension=".csv",filetypes=[("CSV","*.csv")])
        if path:
            # Neutralize spreadsheet formulas in captured text.
            for row in rows:
                for k in ("source","delta","translation","raw_source","error"):
                    v=row[k]
                    if v.lstrip().startswith(("=","+","-","@")):row[k]="'"+v
            with open(path,"w",encoding="utf-8-sig",newline="") as f:
                writer=csv.DictWriter(f,fieldnames=list(rows[0]),delimiter=";")
                writer.writeheader();writer.writerows(rows)

    def close(self):
        self.stop()
        self.root.destroy()

if __name__=="__main__":
    dpi_aware()
    root=tk.Tk()
    Lab(root)
    root.mainloop()
