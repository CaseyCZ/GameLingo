import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {calibrate} from "./calibrate.mjs";
const read=name=>JSON.parse(readFileSync(new URL(name,import.meta.url),"utf8"));
const {samples}=read("./fixtures/gta-v-holdout-raw.json");
const {results:expected}=read("./holdout-report.json");
for(const [engine,frames] of Object.entries(samples)){
 const actual=calibrate(frames);
 assert.deepEqual(actual,expected[engine],engine+" replay changed; review holdout before updating");
 assert.equal(actual.frames,30);
 console.log("HOLDOUT",engine,JSON.stringify(actual));
}
