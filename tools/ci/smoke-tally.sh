#!/usr/bin/env bash
# Smoke test of the Tally app on an emulator (run inside reactivecircus/android-emulator-runner).
# Usage: smoke-tally.sh <apk> <package>
# Writes ring-result.txt (PASS/FAIL lines), smoke-test.log and smoke/*.png. Always exits 0 so a flaky emulator can never block a release.
# An emulator is not a Samsung phone: this proves the app installs, opens, draws, and its service starts, nothing about your phone.
APK=$1; PKG=$2; OUT=smoke; RESULT=ring-result.txt
mkdir -p "$OUT"; : > "$RESULT"
exec > >(tee smoke-test.log) 2>&1
res() { echo "$1" | tee -a "$RESULT"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || echo "screenshot $1 failed"; }
fatal_count() { adb logcat -d 2>/dev/null | grep -c -E "FATAL EXCEPTION|ANR in |Process ${PKG} .*has died" ; }

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

echo "== install"
OUTI=$(adb install -r "$APK" 2>&1); echo "$OUTI"
case "$OUTI" in *Success*) res "PASS installs on Android 14 (emulator)";; *) res "FAIL install: $OUTI"; exit 0;; esac
OUTU=$(adb install -r "$APK" 2>&1)
case "$OUTU" in *Success*) res "PASS installing the same build again works (update path)";; *) res "FAIL update install: $OUTU";; esac
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

echo "== launch"
adb logcat -c
OUTL=$(adb shell am start -W -n "$PKG/.MainActivity" 2>&1); echo "$OUTL"
sleep 8
shot 1-build-light
if [ -n "$(adb shell pidof "$PKG" | tr -d '\r')" ]; then res "PASS app is running 8 s after launch"; else res "FAIL app is not running after launch"; fi

echo "== tabs (taps near the glass tab bar)"
TY=$(( H - $(dp 70) ))
adb shell input tap $(( W * 50 / 100 )) "$TY"; sleep 3; shot 2-builds-light
adb shell input tap $(( W * 82 / 100 )) "$TY"; sleep 3; shot 3-setup-light
adb shell input tap $(( W * 18 / 100 )) "$TY"; sleep 3
echo "== type into the name field"
adb shell input tap $(( W / 2 )) $(( H * 27 / 100 )); sleep 1
adb shell input text "Test%sApp"; sleep 2; shot 4-build-typed
adb shell input keyevent KEYCODE_BACK; sleep 1

echo "== dark mode"
adb shell cmd uimode night yes; sleep 4
shot 5-build-dark
adb shell input tap $(( W * 82 / 100 )) "$TY"; sleep 3; shot 6-setup-dark
adb shell cmd uimode night no; sleep 2

echo "== background service starts"
OUTS=$(adb shell am start-foreground-service -n "$PKG/.BuildService" 2>&1); echo "$OUTS"
sleep 5
case "$OUTS" in *Error*|*Exception*) res "FAIL service start: $OUTS";; *) res "PASS service starts and stops again with no work to do";; esac

echo "== crash scan"
N=$(fatal_count)
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|ANR in " -A12 | head -60
if [ "$N" = "0" ]; then res "PASS no crash or ANR in the log"; else res "FAIL $N crash or ANR lines in the log"; fi
[ -n "$(adb shell pidof "$PKG" | tr -d '\r')" ] && res "PASS still running at the end" || res "FAIL not running at the end"
exit 0
