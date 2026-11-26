import {ZoneScheduler} from "./zone-scheduler.mjs";
import {BlockTracker} from "./block-pipeline.mjs";
const CATALOG={"schemaVersion":1,"profiles":[{"id":"gta-vi","file":"gta-vi.json","status":"experimental","releaseReady":false}]};
const PROFILES={"gta-vi":{"schemaVersion":1,"id":"gta-vi","title":"Grand Theft Auto VI","platforms":["xbox-series","ps5"],"status":"experimental","releaseReady":false,"coordinateSystem":"normalized-ltrb-video-content-top-left","maxZones":10,"ocr":{"mode":"automatic","preprocessing":"none","sourceLanguage":"auto","overlay":"detected-text","manualOutputZones":false},"zones":[{"id":"dialogue-discovery","purpose":"dialogue","rect":[0,0.65,1,1],"status":"unverified-search-area"}],"pendingZones":["phone","objectives","help"],"validation":{"xbox":false,"ps5":false,"referenceAnnotated":false},"sources":[{"kind":"official-gameplay-reference","url":"https://www.rockstargames.com/VI/an-extended-look","verifiedLayout":false},{"kind":"trailer-smoke-test","url":"https://github.com/pigro141/livedub/blob/5666537f002c75c1b4697634ad42e64ae58cb6f1/assets/vetrina/trailer-multilingua.mp4","verifiedLayout":false}]}};
function profileFor(game,platform,allowExperimental=false){
 if(!["xbox-series","ps5"].includes(platform))throw Error("unsupported console");
 const entry=CATALOG.profiles.find(p=>p.id===game);
 if(entry&&entry.status==="experimental"&&allowExperimental){
  const p=PROFILES[entry.id];
  if(!p||!p.platforms.includes(platform)||p.releaseReady!==false||p.zones.length<1||p.zones.length>10)throw Error("invalid profile");
  return {id:p.id,experimental:true,zones:p.zones.map((z,i)=>({id:z.id,rect:[...z.rect],priority:10-i})),pendingZones:[...p.pendingZones]};
 }
 return {id:"generic",experimental:true,zones:[{id:"dialogue-discovery",rect:[0,.65,1,1],priority:10}],pendingZones:["game-specific layout"]};
}
class AutomaticSession{
 constructor({game,platform,targetLanguage,sourceLanguage="auto",allowExperimental=false}){
  if(typeof targetLanguage!=="string"||!/^[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})?$/.test(targetLanguage))throw Error("target language");
  if(typeof sourceLanguage!=="string"||!sourceLanguage)throw Error("source language");
  this.profile=profileFor(game,platform,allowExperimental);
  this.targetLanguage=targetLanguage;this.sourceLanguage=sourceLanguage;
  this.scheduler=new ZoneScheduler(this.profile.zones);
  this.tracker=new BlockTracker(this.profile.zones.map(z=>z.id));
  this.job=null;this.running=true;
 }
 offer(frame,now,signatures){if(this.running)this.scheduler.offer(frame,now,signatures)}
 next(now){if(!this.running)return null;const job=this.scheduler.take(now);if(job)this.job=job;return job}
 finish(id,now,success,lines){
  const job=this.job;const result=this.scheduler.finish(id,now,success);
  if(job?.id===id)this.job=null;
  if(!this.running||!job||job.id!==id||!result.accepted||result.superseded)return false;
  if(!Array.isArray(lines)||lines.length>64)return false;
  const [l,t,r,b]=job.rect;
  const mapped=lines.flatMap(line=>{
   const q=line?.rect;
   if(!Array.isArray(q)||q.length!==4||q.some(v=>!Number.isFinite(v)||v<0||v>1)||q[0]>=q[2]||q[1]>=q[3])return [];
   return [{text:line.text,rect:[l+q[0]*(r-l),t+q[1]*(b-t),l+q[2]*(r-l),t+q[3]*(b-t)]}];
  });
  if(lines.length&&mapped.length===0)return false;
  return this.tracker.observe(job.zone,job.frame,now,mapped);
 }
 requests(now){return this.running?this.tracker.requests(now).map(r=>({...r,sourceLanguage:this.sourceLanguage,targetLanguage:this.targetLanguage})):[]}
 translated(token,text,now){return this.running&&token?.targetLanguage===this.targetLanguage&&token?.sourceLanguage===this.sourceLanguage&&this.tracker.complete(token,text,now)}
 snapshot(now){return this.tracker.snapshot(now)}
 stop(now){this.running=false;this.scheduler.reset(now);this.tracker.reset(now);return this.tracker.snapshot(now)}
}
export {profileFor,AutomaticSession};
