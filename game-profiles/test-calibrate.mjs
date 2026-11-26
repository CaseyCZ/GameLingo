import {readFileSync} from "node:fs";
import {calibrate} from "./calibrate.mjs";
const {samples}=JSON.parse(readFileSync(new URL("./fixtures/gta-vi-trailer-raw.json",import.meta.url),"utf8"));
function check(ok,msg){if(!ok)throw Error(msg)}
const checks=[];
function test(name,fn){fn();checks.push(name)}
test("reject insufficient frames",()=>{let failed=false;try{calibrate([])}catch{failed=true}check(failed,"short input accepted")});
test("reject gaps",()=>{let failed=false;try{calibrate(Array.from({length:12},(_,i)=>({frame:i*2,lines:[]})))}catch{failed=true}check(failed,"gap accepted")});
test("blank sequence",()=>check(calibrate(Array.from({length:12},(_,i)=>({frame:i,lines:[]}))).candidates.length===0,"blank candidate"));
test("static label and changing dialogue",()=>{const f=Array.from({length:15},(_,i)=>({frame:i,lines:[{token:"static",box:[.02,.9,.08,.93]},...(i<12?[{token:"d"+Math.floor(i/4),box:[.3,.9,.7,.93]}]:[])]}));const r=calibrate(f);check(r.persistent.length===1&&r.candidates.length===1,"track classification");check(r.candidates[0].rect[0]>.1,"static contamination")});
test("random one-off reads not dialogue",()=>check(calibrate(Array.from({length:15},(_,i)=>({frame:i,lines:[{token:"n"+i,box:[.3,.9,.7,.93]}]}))).candidates.length===0,"noise accepted"));
test("long single subtitle not candidate",()=>check(calibrate(Array.from({length:15},(_,i)=>({frame:i,lines:i<10?[{token:"long",box:[.3,.9,.7,.93]}]:[]}))).uncertain.length===1,"single dialogue should remain uncertain"));
test("invalid geometry ignored",()=>check(calibrate(Array.from({length:15},(_,i)=>({frame:i,lines:[{token:"d"+Math.floor(i/5),box:[-.1,.9,.7,.93]}]}))).candidates.length===0,"invalid geometry accepted"));
test("candidate cap 10",()=>{const f=Array.from({length:15},(_,i)=>({frame:i,lines:Array.from({length:11},(_,j)=>({token:"d"+j+"_"+Math.floor(i/5),box:[.3,.01+j*.085,.5,.035+j*.085]}))}));const r=calibrate(f);check(r.candidates.length===10&&r.overflow===1,"cap failed")});
for(const [engine,frames] of Object.entries(samples)){test("trailer "+engine,()=>{const r=calibrate(frames);check(r.persistent.length===2&&r.candidates.length===1,"unexpected trailer tracks");check(r.candidates[0].rect[0]>.1&&r.candidates[0].rect[2]>.5,"candidate not central")})}
console.log("CALIBRATION_TESTS",checks.length,checks);
