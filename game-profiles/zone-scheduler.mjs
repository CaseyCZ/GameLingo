// Experimental, single-worker scheduler. No native OCR or runtime integration.
export function sameSignature(a,b){
 if(!a||!b||a.length!==b.length)return false;
 let sum=0,changed=0;
 for(let i=0;i<a.length;i++){const d=Math.abs(a[i]-b[i]);if(d>24)return false;sum+=d;if(d>8)changed++}
 return sum<=a.length*2&&changed<=Math.max(1,Math.floor(a.length/500));
}
export class ZoneScheduler{
 constructor(zones,{minimumGapMs=100,maximumSkipMs=1000,retryMs=250,maximumResultAgeMs=1500}={}){
  if(!Array.isArray(zones)||zones.length<1||zones.length>10)throw Error("zone count");
  if([minimumGapMs,maximumSkipMs,retryMs,maximumResultAgeMs].some(x=>!Number.isFinite(x)||x<=0)||maximumSkipMs<minimumGapMs)throw Error("timing");
  if(new Set(zones.map(z=>z.id)).size!==zones.length)throw Error("duplicate zone");
  this.zones=zones.map((z,i)=>{const r=z.rect;if(typeof z.id!=="string"||!z.id||!Array.isArray(r)||r.length!==4||r.some(v=>!Number.isFinite(v)||v<0||v>1)||r[0]>=r[2]||r[1]>=r[3])throw Error("invalid zone");return {id:z.id,rect:[...r],priority:Number.isFinite(z.priority)?z.priority:0,index:i,baseline:null,readAt:null,lastAttempt:null,failedAt:null}});
  this.cfg={minimumGapMs,maximumSkipMs,retryMs,maximumResultAgeMs};this.epoch=0;this.serial=0;this.clock=0;this.latest=null;this.active=null;this.lastFrame=-1;
 }
 time(now){if(!Number.isFinite(now)||now<this.clock)throw Error("nonmonotonic clock");this.clock=now}
 reset(now){this.time(now);this.epoch++;this.latest=null;this.lastFrame=-1;for(const z of this.zones){z.baseline=null;z.readAt=null;z.lastAttempt=null;z.failedAt=null}}
 offer(frame,now,signatures){
  this.time(now);if(!Number.isSafeInteger(frame)||frame<=this.lastFrame)throw Error("out of order frame");
  const copied=new Map();
  for(const z of this.zones){const s=signatures?.[z.id];if(s==null){copied.set(z.id,null);continue}if((!Array.isArray(s)&&!ArrayBuffer.isView(s))||s.length<1||s.length>12288||Array.from(s).some(v=>!Number.isInteger(v)||v<0||v>255))throw Error("invalid fingerprint");copied.set(z.id,Array.from(s))}
  this.latest={frame,at:now,signatures:copied};this.lastFrame=frame;
 }
 take(now){
  this.time(now);if(this.active||!this.latest||now-this.latest.at>this.cfg.maximumResultAgeMs)return null;
  const f=this.latest;
  const eligible=this.zones.filter(z=>{
   if(z.failedAt!==null&&now-z.failedAt<this.cfg.retryMs)return false;
   if(z.lastAttempt!==null&&now-z.lastAttempt<this.cfg.minimumGapMs)return false;
   return z.readAt===null||now-z.readAt>=this.cfg.maximumSkipMs||!sameSignature(z.baseline,f.signatures.get(z.id));
  });
  eligible.sort((a,b)=>(a.lastAttempt??-Infinity)-(b.lastAttempt??-Infinity)||b.priority-a.priority||a.index-b.index);
  const z=eligible[0];if(!z)return null;
  z.lastAttempt=now;
  const job={id:++this.serial,epoch:this.epoch,zone:z.id,rect:[...z.rect],frame:f.frame,capturedAt:f.at,startedAt:now};
  this.active={job,signature:f.signatures.get(z.id)};return {...job,rect:[...job.rect]};
 }
 finish(id,now,success){
  this.time(now);if(!this.active||this.active.job.id!==id)return {accepted:false,reason:"unknown-job"};
  const {job,signature}=this.active;this.active=null;
  if(job.epoch!==this.epoch)return {accepted:false,reason:"old-session"};
  const z=this.zones.find(x=>x.id===job.zone);
  if(success!==true){z.failedAt=now;return {accepted:false,reason:"read-failed"}}
  z.failedAt=null;
  if(now-job.capturedAt>this.cfg.maximumResultAgeMs)return {accepted:false,reason:"expired"};
  z.baseline=signature;z.readAt=now;
  const superseded=!!this.latest&&this.latest.frame!==job.frame&&!sameSignature(signature,this.latest.signatures.get(z.id));
  return {accepted:true,zone:job.zone,frame:job.frame,superseded};
 }
}
