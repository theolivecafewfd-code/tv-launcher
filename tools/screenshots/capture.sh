#!/bin/bash
# Runs inside the emulator job: installs the debug player, opens each screen against the pretend
# provider (tools/screenshots/server.py) and saves what the TV would show into shots/.
set -x
OUT=shots
mkdir -p "$OUT"
adb install -r player/build/outputs/apk/debug/player-debug.apk
# A Fire Stick has no status or navigation bars, so hide the emulator's.
adb shell settings put global policy_control immersive.full=*

shot() { sleep "${2:-0}"; adb exec-out screencap -p > "$OUT/$1.png"; }
key() { for k in "$@"; do adb shell input keyevent "$k"; sleep 0.7; done; }
open() {
  adb shell pm clear dev.wiptv.player
  adb shell am start -W -n dev.wiptv.player/.DemoSetupActivity \
    --es server http://10.0.2.2:8000 --es scenario "$1" --es section "$2"
}

# Films: several things on the go.
open many MOVIES
shot 01-movies-continue-watching 14
key DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT
shot 02-movies-continue-poster-fallback 4
key DPAD_DOWN DPAD_DOWN
shot 03-movies-new-this-week 5
key DPAD_RIGHT DPAD_RIGHT
shot 04-movies-moved-along 5
key DPAD_DOWN DPAD_RIGHT DPAD_RIGHT
shot 05-movies-no-backdrop 5
key DPAD_UP DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT
key DPAD_CENTER
shot 06-movie-details 6
key BACK
sleep 2

# Series: just one thing on the go.
open single SERIES
shot 10-series-single-resume 14
key DPAD_DOWN DPAD_DOWN
shot 11-series-row 5
key DPAD_DOWN
shot 12-series-poster-fallback 5
key DPAD_UP DPAD_CENTER
shot 13-series-details 7
key DPAD_DOWN DPAD_DOWN
shot 14-series-seasons 3
key DPAD_DOWN DPAD_DOWN
shot 15-series-episodes 3
key BACK
sleep 2

# Films with a single item, and Live TV for comparison.
open single MOVIES
shot 20-movies-single-resume 14
open many LIVE
shot 30-live-tv 16
adb logcat -d -t 400 > "$OUT/logcat.txt" 2>/dev/null || true
