"""Interactive Windows screen OCR laboratory. All capture is local and opt-in."""
import ctypes
import csv
import queue
import threading
import time
from collections import deque
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
import tkinter as tk
from tkinter import ttk,messagebox,filedialog
from PIL import Image,ImageTk
import mss
from core import Dialogue,percentile
from engines import make_engine,preprocess,local_translator
from translation_timing import Ticket,eligible,timings,run_job

@dataclass(frozen=True)
class Config:
    engine:str
    filter:str
    scale:int
    interval:int
    psm:int
    rolling:bool
    translate:bool

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
        self.metrics=dict(submitted=0,completed=0,shown=0,stale=0,replaced=0,errors=0)
        self.ui_times=deque(maxlen=1000)
        self.trans_times=deque(maxlen=1000)
        self.thread=threading.Thread(target=self.run,daemon=True)
        self.thread.start()

    def emit(self,data):
        try:self.messages.get_nowait()
        except queue.Empty:pass
        self.messages.put_nowait(data)

    def record(self,row):
        keys=("elapsed_ms","engine","filter","scale","interval_ms","psm","capture_prep_ms","ocr_ms",
              "event","source","delta","translation","generation","translation_queue_ms","translation_ms",
              "frame_to_result_ms","frame_to_ui_ms","cold_translation")
        with self.lock:self.rows.append({k:row.get(k,"") for k in keys})

    def note(self,key):
        with self.lock:self.metrics[key]+=1

    def accept_ui(self,result,now):
        with self.lock:
            if not eligible(result,self.epoch,self.displayed,now,self.config.translate):
                return False
            self.displayed=result.ticket.generation
            self.metrics["shown"]+=1
            if not result.cold:self.ui_times.append((now-result.ticket.frame_at)*1000)
        self.record(dict(event="ui_displayed",source=result.ticket.source,
                         translation=result.text,**timings(result,now)))
        return True

    def run(self):
        engines={}
        tracker=Dialogue()
        times=deque(maxlen=1000)
        counts={"new":0,"repeat":0,"blank":0,"clear":0}
        translation=""
        translation_error=""
        last_config=None
        generation=-1
        future=None
        pending=None
        result_for_ui=None
        translate_fn=None
        pool=ThreadPoolExecutor(max_workers=1,thread_name_prefix="local-translation")
        start=time.perf_counter()
        try:
            with mss.mss() as sct:
                while not self.stop.is_set():
                    cfg=self.config
                    if cfg!=last_config:
                        # Do not combine statistics across different settings.
                        tracker=Dialogue()
                        times.clear()
                        counts=dict.fromkeys(counts,0)
                        generation+=1
                        with self.lock:self.epoch+=1
                        if pending is not None:self.note("replaced")
                        pending=result_for_ui=None
                        translation=translation_error=""
                        last_config=cfg
                    if cfg.engine not in engines:
                        self.emit({"status":"Načítám "+cfg.engine+"..."})
                        engines[cfg.engine]=make_engine(cfg.engine)
                    tick=time.perf_counter()
                    image=grab(sct,self.region)
                    prep=preprocess(image,cfg.filter,cfg.scale)
                    captured=time.perf_counter()
                    raw=engines[cfg.engine].read(prep,cfg.psm)
                    end=time.perf_counter()
                    if self.stop.is_set():break
                    event=tracker.accept(raw,cfg.rolling)
                    counts[event.kind]+=1
                    if event.kind in ("new","clear"):
                        generation+=1
                        if pending is not None:self.note("replaced")
                        pending=None
                        if event.kind=="clear":
                            with self.lock:self.epoch+=1
                            result_for_ui=None
                            translation=""
                        elif cfg.translate:
                            pending=Ticket(generation,self.epoch,event.text,tick,end)
                    ocr=(end-captured)*1000
                    times.append(ocr)
                    if future is not None and future.done():
                        try:
                            result=future.result()
                            self.note("completed")
                            with self.lock:
                                if not result.cold:self.trans_times.append((result.finished_at-result.started_at)*1000)
                                valid=eligible(result,self.epoch,self.displayed,time.perf_counter(),cfg.translate)
                            self.record(dict(event="translation_completed" if valid else "translation_stale",
                                             source=result.ticket.source,translation=result.text,**timings(result)))
                            if valid:
                                result_for_ui=result
                                translation=result.text
                            else:self.note("stale")
                        except Exception as exc:
                            self.note("errors")
                            translation_error=f"{type(exc).__name__}: {exc}"
                        future=None
                    if cfg.translate and pending is not None and future is None and not translation_error:
                        ticket,pending=pending,None
                        def work(ticket):
                            nonlocal translate_fn
                            cold=translate_fn is None
                            def translate(source):
                                nonlocal translate_fn
                                if translate_fn is None:translate_fn=local_translator()
                                return translate_fn(source)
                            return run_job(ticket,translate,cold)
                        self.note("submitted")
                        future=pool.submit(work,ticket)
                    if result_for_ui is not None and time.perf_counter()-result_for_ui.ticket.frame_at>3:
                        result_for_ui=None
                        translation=""
                    if not cfg.translate:translation=""
                    row={"elapsed_ms":round((tick-start)*1000,1),"engine":cfg.engine,"filter":cfg.filter,
                         "scale":cfg.scale,"interval_ms":cfg.interval,"psm":cfg.psm,
                         "capture_prep_ms":round((captured-tick)*1000,1),"ocr_ms":round(ocr,1),
                         "event":event.kind,"source":event.text,"delta":event.delta,"translation":translation}
                    self.record(row)
                    self.emit({"status":f"OCR {ocr:.0f} ms | průměr {sum(times)/len(times):.0f} | P95 {percentile(times,.95):.0f} ms",
                               "counts":dict(counts),"source":event.text,"translation":translation,
                               "warning":translation_error,"enabled":cfg.translate,"result":result_for_ui})
                    self.stop.wait(max(0,cfg.interval/1000-(end-tick)))
        except Exception as exc:
            self.emit({"error":f"{type(exc).__name__}: {exc}"})
        finally:
            pool.shutdown(wait=False,cancel_futures=True)

class Lab:
    def __init__(self,root):
        self.root=root
        root.title("GameLingo OCR Lab • Windows")
        root.geometry("900x730")
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
        settings=ttk.Frame(top);settings.pack(fill="x")
        for i,(label,var,options) in enumerate([
            ("OCR engine",self.engine,["tesseract","rapid"]),
            ("Filtr",self.filter,["raw","gray","contrast","binary"]),
            ("Měřítko %",self.scale,["50","75","100","125","150"]),
            ("Interval ms",self.interval,["100","200","250","500","800","1000"]),
            ("Tesseract PSM",self.psm,["6","7","11"])]):
            row,col=divmod(i,3)
            ttk.Label(settings,text=label).grid(row=row*2,column=col,sticky="w",padx=3)
            ttk.Combobox(settings,textvariable=var,values=options,state="readonly",width=12).grid(row=row*2+1,column=col,padx=3,sticky="w")
        ttk.Checkbutton(top,text="Průběžné titulky – sledovat překryv slov (experiment)",variable=self.rolling).pack(anchor="w")
        ttk.Checkbutton(top,text="Offline překlad EN → CS (volitelný Argos)",variable=self.translate,command=self.toggle_overlay).pack(anchor="w")
        ttk.Checkbutton(top,text="Zobrazit plovoucí okno mimo snímanou oblast",variable=self.show_overlay,command=self.toggle_overlay).pack(anchor="w")
        controls=ttk.Frame(top);controls.pack(fill="x",pady=10)
        self.start_button=ttk.Button(controls,text="Spustit",command=self.start)
        self.start_button.pack(side="left")
        ttk.Button(controls,text="Zastavit",command=self.stop).pack(side="left",padx=5)
        ttk.Button(controls,text="Export měření CSV",command=self.export).pack(side="left",padx=5)
        ttk.Label(top,textvariable=self.status,wraplength=710).pack(anchor="w")
        ttk.Label(top,textvariable=self.counts,wraplength=710).pack(anchor="w")
        ttk.Label(top,textvariable=self.translation_stats,wraplength=710).pack(anchor="w")
        ttk.Label(top,text="Rozpoznaný text",font=("Segoe UI",11,"bold")).pack(anchor="w",pady=(12,0))
        ttk.Label(top,textvariable=self.source,wraplength=700,font=("Segoe UI",12)).pack(anchor="w")
        ttk.Label(top,text="Překlad",font=("Segoe UI",11,"bold")).pack(anchor="w",pady=(12,0))
        ttk.Label(top,textvariable=self.translation,wraplength=700,font=("Segoe UI",12)).pack(anchor="w")
        ttk.Label(top,textvariable=self.warning,wraplength=700,foreground="#a04000").pack(anchor="w")
        ttk.Label(top,text="Živé počty nejsou měření vynechaných vět; pro ně použij benchmark.py.",wraplength=700).pack(side="bottom",anchor="w")
        root.protocol("WM_DELETE_WINDOW",self.close)
        root.after(50,self.poll)

    def config(self):
        return Config(self.engine.get(),self.filter.get(),int(self.scale.get()),int(self.interval.get()),
                      int(self.psm.get()),self.rolling.get(),self.translate.get())

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
        tk.Label(window,textvariable=self.translation if self.translate.get() else self.source,
                 bg="#151923",fg="white",font=("Segoe UI",13),wraplength=width-20,
                 height=3).pack(fill="x")
        ttk.Button(window,text="Zastavit OCR",command=self.stop).pack()
        self.overlay=window

    def stop(self):
        if self.session:self.session.stop.set()
        if self.overlay:
            self.overlay.destroy();self.overlay=None
        self.status.set("Zastavuji…")

    def poll(self):
        if self.session:
            self.session.config=self.config()
            try:
                d=self.session.messages.get_nowait()
                if "error" in d:
                    self.warning.set(d["error"])
                    self.stop()
                else:
                    self.status.set(d["status"])
                    if "counts" in d:
                        c=d["counts"]
                        self.counts.set(f"Nové: {c['new']} | stejné: {c['repeat']} | prázdné: {c['blank']} | vymazání: {c['clear']}")
                        self.source.set(d["source"])
                        result=d.get("result")
                        if result is not None:
                            if self.session.accept_ui(result,time.perf_counter()):
                                self.translation.set(result.text)
                        else:
                            self.translation.set("Čekám na překlad…" if d["enabled"] and d["source"] else "")
                        with self.session.lock:
                            m=dict(self.session.metrics)
                            ui=list(self.session.ui_times)
                            tr=list(self.session.trans_times)
                        self.translation_stats.set(
                            f"Překlad P95 {percentile(tr,.95):.0f} ms | Snímek → UI P95 {percentile(ui,.95):.0f} ms (warm)\n"
                            f"Zahájeno {m['submitted']} | hotovo {m['completed']} | zobrazeno {m['shown']} | staré {m['stale']} | nahrazené {m['replaced']} | chyby {m['errors']}")
                        if d["warning"]:self.warning.set(d["warning"])
            except queue.Empty:pass
            if not self.session.thread.is_alive():
                self.pick.state(["!disabled"]);self.start_button.state(["!disabled"])
                self.status.set("Zastaveno. Výsledky můžeš exportovat.")
                if self.overlay:self.overlay.destroy();self.overlay=None
        self.root.after(50,self.poll)

    def export(self):
        if not self.session:return
        with self.session.lock:rows=list(self.session.rows)
        if not rows:return
        if not messagebox.askyesno("Export","CSV obsahuje rozpoznaný text i překlad. Uložit lokálně?"):return
        path=filedialog.asksaveasfilename(defaultextension=".csv",filetypes=[("CSV","*.csv")])
        if path:
            # Neutralize spreadsheet formulas in captured text.
            for row in rows:
                for k in ("source","delta","translation"):
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
