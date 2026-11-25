#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p results
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
set +e
adb shell am instrument -w -r -e class cz.caseycz.mlkitlab.OcrBenchmarkTest cz.caseycz.mlkitlab.test/androidx.test.runner.AndroidJUnitRunner | tee results/instrumentation.txt
instrument_status=$?
adb exec-out run-as cz.caseycz.mlkitlab cat files/results.jsonl > results/results.jsonl
pull_status=$?
adb logcat -d -s MLKitLab:I AndroidRuntime:E > results/logcat.txt
set -e
if [ "$pull_status" -ne 0 ]; then exit "$pull_status"; fi
python summarize.py
test "$instrument_status" -eq 0
grep -F 'OK (1 test)' results/instrumentation.txt
