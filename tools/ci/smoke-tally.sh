#!/usr/bin/env bash
# Smoke test of the Tally app on an emulator (run inside reactivecircus/android-emulator-runner).
# Usage: smoke-tally.sh <apk> <package>
# Writes ring-result.txt (PASS/FAIL lines), smoke-test.log, smoke-diag.txt and smoke/*.png. Always exits 0 so a flaky emulator can never block a release.
# An emulator is not a Samsung phone: this proves the app installs, opens, draws, and its background service works, nothing about your phone.
APK=$1; PKG=$2; OUT=smoke; RESULT=ring-result.txt; DIAG=smoke-diag.txt
mkdir -p "$OUT"; : > "$RESULT"; : > "$DIAG"
exec > >(tee smoke-test.log) 2>&1
res() { echo "$1" | tee -a "$RESULT"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || echo "screenshot $1 failed"; }
pid() { adb shell pidof "$PKG" 2>/dev/null | tr -d '\r' | awk '{print $1}'; }
diag() { { echo; echo "##### $1"; shift; "$@" 2>&1; } >> "$DIAG"; }
fatal_count() { adb logcat -d 2>/dev/null | grep -c -E "FATAL EXCEPTION|ANR in |Process ${PKG} .*has died"; }
# Stack of the app's main thread, from a SIGQUIT dump (needs the emulator's root).
stack() {
  local p; p=$(pid); [ -n "$p" ] || { echo "no process"; return; }
  adb shell "kill -3 $p" >/dev/null 2>&1; sleep 3
  adb shell "ls -t /data/anr 2>/dev/null | head -1" | tr -d '\r' | while read -r f; do
    [ -n "$f" ] && adb shell "grep -A25 'Cmd line: $PKG' /data/anr/$f | head -5; grep -A28 '\"main\" prio' /data/anr/$f | head -34"
  done
}

echo "== wait for the emulator (max 150 s)"
timeout 60 adb wait-for-device
for i in $(seq 1 30); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 5
done
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard 2>/dev/null || true
SIZE=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${SIZE%x*}; H=${SIZE#*x}
DEN=$(adb shell wm density | grep -o '[0-9]*' | tail -1)
[ -n "$W" ] && [ -n "$H" ] && [ -n "$DEN" ] || { res "FAIL emulator did not report a screen size"; exit 0; }
echo "screen ${W}x${H} at ${DEN} dpi"
dp() { echo $(( $1 * DEN / 160 )); }
adb root >/dev/null 2>&1; adb wait-for-device; ROOT=$(adb shell id -u | tr -d '\r'); echo "adb root uid: $ROOT"
# let the emulator settle so a cold, busy machine does not look like a slow app
sleep ${SETTLE:-20}
diag "cpu before install" adb shell top -b -n 1 -m 8

echo "== install"
OUTI=$(adb install -r "$APK" 2>&1); echo "$OUTI"
case "$OUTI" in *Success*) res "PASS installs on Android 14 (emulator)";; *) res "FAIL install: $OUTI"; exit 0;; esac
OUTU=$(adb install -r "$APK" 2>&1)
case "$OUTU" in *Success*) res "PASS installing the same build again works (update path)";; *) res "FAIL update install: $OUTU";; esac
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
sleep ${SETTLE2:-10}

echo "== launch"
adb logcat -c
OUTL=$(adb shell am start -W -n "$PKG/.MainActivity" 2>&1); echo "$OUTL"
sleep 3;  shot 1a-build-3s
sleep 7;  shot 1b-build-10s
diag "cpu at 10 s" adb shell top -b -n 1 -m 8
diag "focus at 10 s" adb shell "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"
diag "main thread at 10 s" stack
sleep 10; shot 1c-build-20s
if [ -n "$(pid)" ]; then res "PASS app is running 20 s after launch"; else res "FAIL app is not running 20 s after launch"; fi

echo "== tabs (taps near the glass tab bar)"
TY=$(( H - $(dp 70) ))
adb shell input tap $(( W * 50 / 100 )) "$TY"; sleep 4; shot 2-builds
adb shell input tap $(( W * 82 / 100 )) "$TY"; sleep 4; shot 3-setup
adb shell input tap $(( W * 18 / 100 )) "$TY"; sleep 4; shot 4-build-again
diag "main thread after tabs" stack
if [ -n "$(pid)" ]; then res "PASS app survived switching tabs"; else res "FAIL app died while switching tabs"; fi

echo "== dark mode"
adb shell cmd uimode night yes; sleep 5
shot 5-build-dark
adb shell input tap $(( W * 82 / 100 )) "$TY"; sleep 4; shot 6-setup-dark
adb shell cmd uimode night no; sleep 3

echo "== background service, driven by a seeded build (no token, so it must fail politely)"
adb shell am force-stop "$PKG"; sleep 1
UID_NAME=$(adb shell "ls -ld /data/data/$PKG" | tr -d '\r' | awk '{print $3}')
cat > seed.xml <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="builds">[{"id":"bseed00001","name":"Seeded","pkg":"com.killswitch.seeded","fit":"fill","created":1,"stage":"Preparing","detail":"","runId":0,"saved":"","error":"","resume":false}]</string>
</map>
XML
adb push seed.xml /data/local/tmp/seed.xml >/dev/null
adb shell "mkdir -p /data/data/$PKG/shared_prefs && cp /data/local/tmp/seed.xml /data/data/$PKG/shared_prefs/tally.xml && chown $UID_NAME:$UID_NAME /data/data/$PKG/shared_prefs/tally.xml && chmod 660 /data/data/$PKG/shared_prefs/tally.xml && restorecon /data/data/$PKG/shared_prefs/tally.xml"
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity" >/dev/null 2>&1
sleep 12
shot 7-builds-seeded
PREFS=$(adb shell "cat /data/data/$PKG/shared_prefs/tally.xml" 2>&1)
echo "$PREFS" | head -20
diag "prefs after the seeded build" echo "$PREFS"
case "$PREFS" in
  *'"stage":"Failed"'*'Add your GitHub token'*|*'Add your GitHub token'*'"stage":"Failed"'*) res "PASS background service ran the seeded build and failed it politely (no token)";;
  *) res "FAIL seeded build did not end as expected, see smoke-diag.txt";;
esac

echo "== crash scan"
adb logcat -d -v time | grep -E "FATAL EXCEPTION|AndroidRuntime|ANR in |$PKG.*(Exception|Error)" -A8 | head -80 >> "$DIAG"
N=$(fatal_count)
adb logcat -d | grep -E "FATAL EXCEPTION|ANR in " | head -10
if [ "$N" = "0" ]; then res "PASS no crash or ANR in the log"; else res "FAIL $N crash or ANR lines in the log"; fi
[ -n "$(pid)" ] && res "PASS still running at the end" || res "FAIL not running at the end"
adb logcat -d -v time | tail -300 > smoke-logcat.txt
exit 0
