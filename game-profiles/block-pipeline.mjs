// Portable laboratory reference, not a production transport or native renderer.
function iou(a,b){
 const n=Math.max(0,Math.min(a[2],b[2])-Math.max(a[0],b[0]))*Math.max(0,Math.min(a[3],b[3])-Math.max(a[1],b[1]));
 const area=r=>(r[2]-r[0])*(r[3]-r[1]);return n/(area(a)+area(b)-n);
}

function validRect(b){return Array.isArray(b)&&b.length===4&&b.every(x=>Number.isFinite(x)&&x>=0&&x<=1)&&b[0]<b[2]&&b[1]<b[3]}

function clean(s){return typeof s==="string"?s.normalize("NFC").trim().replace(/\s+/gu," "):""}

class BlockTracker{
 constructor(zoneIds,{clearMs=500,staleMs=2500,confirmReads=2,maxBlocks=64}={}){
  if(!Array.isArray(zoneIds)||zoneIds.length<1||zoneIds.length>10||new Set(zoneIds).size!==zoneIds.length||zoneIds.some(z=>typeof z!=="string"||!z))throw Error("zones");
  if(!Number.isFinite(clearMs)||clearMs<=0||!Number.isFinite(staleMs)||staleMs<clearMs||!Number.isInteger(confirmReads)||confirmReads<1||confirmReads>5||!Number.isInteger(maxBlocks)||maxBlocks<1||maxBlocks>64)throw Error("configuration");
  this.zones=new Map(zoneIds.map(id=>[id,{lastFrame:-1,tracks:[]}]));
  this.clearMs=clearMs;this.staleMs=staleMs;this.confirmReads=confirmReads;this.maxBlocks=maxBlocks;
  this.now=0;this.epoch=0;this.serial=0;this.sequence=0;this.requestSerial=0;
 }
 clock(now){if(!Number.isFinite(now)||now<this.now)throw Error("nonmonotonic time");this.now=now}
 reset(now){this.clock(now);this.epoch++;for(const z of this.zones.values()){z.lastFrame=-1;z.tracks=[]}}
 sweep(now){this.clock(now);for(const z of this.zones.values())z.tracks=z.tracks.filter(t=>now-t.seenAt<this.staleMs&&(t.missingAt===null||now-t.missingAt<this.clearMs))}
 observe(zone,frame,now,blocks){
  this.sweep(now);const z=this.zones.get(zone);
  if(!z||!Number.isSafeInteger(frame)||frame<=z.lastFrame||!Array.isArray(blocks)||blocks.length>this.maxBlocks)return false;
  z.lastFrame=frame;
  const candidates=[];
  for(const b of blocks){const text=clean(b?.text);if(!text||text.length>512||!validRect(b?.rect))continue;const rect=[...b.rect];if(candidates.some(c=>c.text===text&&iou(c.rect,rect)>=.5))continue;candidates.push({text,rect})}
  const used=new Set();
  for(const b of candidates){
   let t=null,best=.3;
   for(const old of z.tracks){if(used.has(old))continue;const overlap=iou(old.rect,b.rect);if(overlap>best){best=overlap;t=old}}
   if(!t){if(z.tracks.length>=this.maxBlocks)continue;t={id:++this.serial,version:0,rect:b.rect,text:b.text,count:0,seenAt:now,missingAt:null,translation:null,pending:false,requestId:null,retryAt:0,confirmed:false};z.tracks.push(t)}
   used.add(t);
   if(t.text!==b.text||t.missingAt!==null){t.version++;t.text=b.text;t.count=0;t.translation=null;t.pending=false;t.requestId=null;t.retryAt=0;t.confirmed=false}
   t.rect=b.rect;t.seenAt=now;t.missingAt=null;t.count++;if(t.count>=this.confirmReads)t.confirmed=true;
  }
  for(const t of z.tracks){if(!used.has(t)&&t.missingAt===null){t.missingAt=now;t.version++;t.pending=false;t.requestId=null}}
  return true;
 }
 current(){
  const all=[];for(const [zone,z] of this.zones)for(const t of z.tracks)if(t.confirmed&&t.missingAt===null)all.push({zone,t});
  all.sort((a,b)=>Number(!!b.t.translation)-Number(!!a.t.translation)||Number(b.t.pending)-Number(a.t.pending)||a.t.id-b.t.id);
  const out=[];for(const a of all)if(!out.some(b=>a.t.text===b.t.text&&iou(a.t.rect,b.t.rect)>=.5))out.push(a);
  return out.slice(0,this.maxBlocks);
 }
 requests(now){this.sweep(now);const requests=[];for(const {zone,t} of this.current()){if(t.translation===null&&!t.pending&&now>=t.retryAt){t.pending=true;t.requestId=++this.requestSerial;requests.push({epoch:this.epoch,zone,id:t.id,version:t.version,requestId:t.requestId,text:t.text})}}return requests}
 complete(token,translated,now){
  this.sweep(now);const t=this.zones.get(token?.zone)?.tracks.find(t=>t.id===token.id);
  if(!t||token.epoch!==this.epoch||token.version!==t.version||token.text!==t.text||!t.pending||token.requestId!==t.requestId||!t.confirmed||t.missingAt!==null)return false;
  t.pending=false;t.requestId=null;const text=clean(translated);if(!text||text.length>2048){t.retryAt=now+500;return false;}t.translation=text;return true;
 }
 snapshot(now){
  this.sweep(now);
  // A missing block is not emitted; no stale result can resurrect it. Other zones remain independent.
  return {epoch:this.epoch,sequence:++this.sequence,blocks:this.current().filter(({t})=>t.translation!==null).map(({zone,t})=>({id:t.id,zone,text:t.translation,rect:[...t.rect]}))};
 }
}

function mapRect(rect,viewport){
 if(!validRect(rect)||!Array.isArray(viewport)||viewport.length!==4||viewport.some(x=>!Number.isFinite(x))||viewport[2]<=0||viewport[3]<=0)throw Error("geometry");
 const [x,y,w,h]=viewport;return [x+rect[0]*w,y+rect[1]*h,x+rect[2]*w,y+rect[3]*h];
}

function fitViewport(contentWidth,contentHeight,displayWidth,displayHeight){
 if([contentWidth,contentHeight,displayWidth,displayHeight].some(v=>!Number.isFinite(v)||v<=0))throw Error("dimensions");
 const s=Math.min(displayWidth/contentWidth,displayHeight/contentHeight),w=contentWidth*s,h=contentHeight*s;return [(displayWidth-w)/2,(displayHeight-h)/2,w,h];
}

class SnapshotReceiver{
 constructor(){this.session=null;this.sequence=-1;this.expiry=0;this.blocks=[]}
 begin(session){if(typeof session!=="string"||!session)throw Error("session");this.session=session;this.sequence=-1;this.expiry=0;this.blocks=[]}
 accept(session,snapshot,now,ttl=1200){
  if(session!==this.session||!Number.isFinite(now)||!Number.isFinite(ttl)||ttl<=0||ttl>3000||!Number.isSafeInteger(snapshot?.sequence)||snapshot.sequence<=this.sequence||!Array.isArray(snapshot.blocks)||snapshot.blocks.length>64)return false;
  const ids=new Set();for(const b of snapshot.blocks){if(!Number.isSafeInteger(b?.id)||ids.has(b.id)||!validRect(b.rect)||!clean(b.text)||b.text.length>2048)return false;ids.add(b.id)}
  this.sequence=snapshot.sequence;this.expiry=now+ttl;this.blocks=snapshot.blocks.map(b=>({id:b.id,text:b.text,rect:[...b.rect]}));return true;
 }
 visible(now,viewport){if(!Number.isFinite(now)||now>=this.expiry)return [];return this.blocks.map(b=>({id:b.id,text:b.text,rect:mapRect(b.rect,viewport)}))}
}
export {BlockTracker,SnapshotReceiver,mapRect,fitViewport};
