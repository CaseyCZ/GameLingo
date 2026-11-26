import {ZoneScheduler,sameSignature} from "./zone-scheduler.mjs";
function runTests(ZoneScheduler,sameSignature){
 const assert=(v,m)=>{if(!v)throw Error(m)};
 const eq=(a,b,m)=>assert(JSON.stringify(a)===JSON.stringify(b),m+": "+JSON.stringify(a));
 const throws=f=>{let failed=false;try{f()}catch{failed=true}assert(failed,"expected rejection")};
 const z=(id,priority=0)=>({id,priority,rect:[0,.65,1,1]});
 const s=()=>new ZoneScheduler([z("a",2),z("b")]);
 const sig=(a=0,b=0)=>({a:[a,a],b:[b,b]});
 const names=[];function t(name,f){f();names.push(name)}
 t("limits and geometry",()=>{throws(()=>new ZoneScheduler([]));throws(()=>new ZoneScheduler(Array.from({length:11},(_,i)=>z(""+i))));throws(()=>new ZoneScheduler([z("a"),z("a")]));throws(()=>new ZoneScheduler([{id:"x",rect:[0,.9,1,.8]}]))});
 t("one active reader and independent zones",()=>{const q=s();q.offer(1,0,sig());const a=q.take(0);eq(a.zone,"a","priority");eq(q.take(1),null,"parallel");q.finish(a.id,10,true);const b=q.take(10);eq(b.zone,"b","second zone");q.finish(b.id,20,true);q.offer(2,120,sig(0,100));eq(q.take(120).zone,"b","only changed zone")});
 t("latest frame replaces pending",()=>{const q=s();q.offer(1,0,sig());const a=q.take(0);for(let i=2;i<=50;i++)q.offer(i,i,sig(50,0));eq(q.finish(a.id,60,true).superseded,true,"changed during OCR");eq(q.take(60).frame,50,"latest frame")});
 t("compare last successful read not skipped",()=>{const q=new ZoneScheduler([z("a")]);q.offer(1,0,{a:[0,0]});const a=q.take(0);q.finish(a.id,10,true);q.offer(2,110,{a:[1,1]});eq(q.take(110),null,"small change");q.offer(3,210,{a:[2,2]});eq(q.take(210),null,"small change");q.offer(4,310,{a:[3,3]});assert(q.take(310),"cumulative change")});
 t("periodic refresh even unchanged",()=>{const q=new ZoneScheduler([z("a")]);q.offer(1,0,{a:[0]});const a=q.take(0);q.finish(a.id,10,true);q.offer(2,1009,{a:[0]});eq(q.take(1009),null,"early");q.offer(3,1010,{a:[0]});assert(q.take(1010),"refresh")});
 t("failure backoff and successful baseline only",()=>{const q=new ZoneScheduler([z("a")]);q.offer(1,0,{a:[0]});const a=q.take(0);eq(q.finish(a.id,10,false).reason,"read-failed","failed");q.offer(2,259,{a:[0]});eq(q.take(259),null,"backoff");q.offer(3,260,{a:[0]});assert(q.take(260),"retry")});
 t("reset does not release active reader",()=>{const q=s();q.offer(1,0,sig());const a=q.take(0);q.reset(5);q.offer(1,6,sig());eq(q.take(6),null,"reader still owned");eq(q.finish(a.id,7,true).reason,"old-session","reject");assert(q.take(7),"new session")});
 t("expired results and unknown callbacks",()=>{const q=s();q.offer(1,0,sig());const a=q.take(0);eq(q.finish(a.id+1,1,true).reason,"unknown-job","wrong id");eq(q.take(2),null,"still active");eq(q.finish(a.id,1501,true).reason,"expired","old");eq(q.take(1501),null,"old capture");q.offer(2,1502,sig());assert(q.take(1502),"fresh frame")});
 t("fair scheduling all ten zones",()=>{const zones=Array.from({length:10},(_,i)=>z("z"+i,10-i));const q=new ZoneScheduler(zones);const served=[];for(let i=0;i<10;i++){q.offer(i,i*100,Object.fromEntries(zones.map(z=>[z.id,[i*20]])));const a=q.take(i*100);served.push(a.zone);q.finish(a.id,i*100+1,true)}eq(new Set(served).size,10,"starvation")});
 t("unknown fingerprint fails open",()=>{const q=new ZoneScheduler([z("a")]);q.offer(1,0,{});const a=q.take(0);q.finish(a.id,1,true);q.offer(2,100,{});assert(q.take(100),"unknown must read")});
 t("signature copies and input validation",()=>{const q=new ZoneScheduler([z("a")]);const a=[0];q.offer(1,0,{a});a[0]=100;const job=q.take(0);q.finish(job.id,1,true);q.offer(2,100,{a:[0]});eq(q.take(100),null,"copied");throws(()=>q.offer(2,101,{a:[0]}));throws(()=>q.offer(3,102,{a:[256]}));throws(()=>q.take(99));});
 t("local changes and gradual changes",()=>{assert(!sameSignature([0,0,0],[0,25,0]),"local change");assert(sameSignature([0,0],[2,2]),"noise");assert(!sameSignature(null,null),"unknown")});
 return names;
}
console.log("ZONE_SCHEDULER_TESTS",runTests(ZoneScheduler,sameSignature));
