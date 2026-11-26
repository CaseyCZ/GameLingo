#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p results
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
adb logcat -v brief -s ComboOCR:I AndroidRuntime:E > results/combination-logcat.txt &
logger=$!
cleanup() {
  kill "$logger" 2>/dev/null || true
  adb exec-out run-as cz.caseycz.mlkitlab cat files/combination.jsonl > results/combination.jsonl || true
}
trap cleanup EXIT
set +e
timeout 600 adb shell am instrument -w -r -e class cz.caseycz.mlkitlab.CombinationTest cz.caseycz.mlkitlab.test/androidx.test.runner.AndroidJUnitRunner | tee results/combination-instrumentation.txt
status=${PIPESTATUS[0]}
set -e
cleanup
python ../ocr-lab/score_combination.py results/combination.jsonl results/combination-summary.json
test "$status" -eq 0
grep -F 'OK (1 test)' results/combination-instrumentation.txt
