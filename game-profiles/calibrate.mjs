// Offline discovery only; not a runtime subtitle filter. No image or dialogue text required.
export function calibrate(frames){
 const median=a=>{const s=[...a].sort((x,y)=>x-y);return s[Math.floor(s.length/2)]};
 const geom=b=>[(b[0]+b[2])/2,(b[1]+b[3])/2,b[3]-b[1]];
 if(!Array.isArray(frames)||frames.length<12||frames.some((f,i)=>!Number.isInteger(f.frame)||(i&&f.frame!==frames[i-1].frame+1)||!Array.isArray(f.lines)))throw Error("need >=12 contiguous frames");
 const tracks=[];
 for(const f of frames){
  const used=new Set();
  for(const l of f.lines){
   const b=l.box;if(typeof l.token!=="string"||!l.token||!Array.isArray(b)||b.length!==4||b.some(v=>!Number.isFinite(v)||v<0||v>1)||b[2]<=b[0]||b[3]<=b[1])continue;
   const g=geom(b);let best=null,dist=Infinity;
   for(const t of tracks){
    if(used.has(t))continue;
    const a=t.anchor,ratio=g[2]/a[2];
    if(Math.abs(g[0]-a[0])>.12||Math.abs(g[1]-a[1])>.035||ratio<.5||ratio>2)continue;
    const d=Math.abs(g[0]-a[0])/.12+Math.abs(g[1]-a[1])/.035+Math.abs(Math.log(ratio));
    if(d<dist){dist=d;best=t}
   }
   if(!best){best={anchor:g,items:[]};tracks.push(best)}
   best.items.push({frame:f.frame,token:l.token,box:b});used.add(best)
  }
 }
 const candidates=[],persistent=[],uncertain=[];
 for(const t of tracks){
  const counts=new Map();let repeatedPairs=0;
  for(let i=0;i<t.items.length;i++){const v=t.items[i];counts.set(v.token,(counts.get(v.token)||0)+1);if(i&&v.frame===t.items[i-1].frame+1&&v.token===t.items[i-1].token)repeatedPairs++}
  const n=t.items.length,coverage=n/frames.length,dominance=Math.max(...counts.values())/n;
  const recurring=[...counts.values()].filter(v=>v>=2).length;
  const stablePosition=t.items.every(v=>{const g=geom(v.box);return Math.abs(g[0]-t.anchor[0])<=.015&&Math.abs(g[1]-t.anchor[1])<=.012});
  const boxes=t.items.map(v=>v.box),union=[Math.min(...boxes.map(b=>b[0])),Math.min(...boxes.map(b=>b[1])),Math.max(...boxes.map(b=>b[2])),Math.max(...boxes.map(b=>b[3]))];
  const rect=union.map((v,i)=>Number(Math.max(0,Math.min(1,v+(i<2?-1:1)*(i%2?.018:.025))).toFixed(4)));
  const record={rect,observations:n,coverage:Number(coverage.toFixed(4)),distinct:counts.size,recurring,repeatedPairs,centerY:Number(median(boxes.map(b=>geom(b)[1])).toFixed(4))};
  if(coverage>=.9&&dominance>=.95&&stablePosition)persistent.push(record);
  else if(coverage>=.2&&recurring>=3&&repeatedPairs>=3)candidates.push(record);
  else uncertain.push(record);
 }
 candidates.sort((a,b)=>b.repeatedPairs-a.repeatedPairs||b.observations-a.observations);
 return {status:"experimental",coordinateSystem:"encoded-video-normalized-ltrb",frames:frames.length,candidates:candidates.slice(0,10),persistent,uncertain,overflow:Math.max(0,candidates.length-10)};
}
