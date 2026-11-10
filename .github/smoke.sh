#!/usr/bin/env bash
# エミュレータで APK を入れてホームとして起動し、落ちないことと主な画面の見た目を確かめる。
# 実際のウィジェット相当の中身（天気・予定・再生中の曲）や Pixel 7 での見た目は端末で確かめる。
set -euxo pipefail
PKG=io.github.tkdev106.launcher
OUT=smoke
mkdir -p "$OUT"

# 成果物はこの環境から取り出せないことがあるので、落ちたときの手がかりをジョブのログにも出す
report() {
  adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
  ls -la "$OUT"
  grep -nE "FATAL EXCEPTION|ANR in|has died|system_server|Watchdog|$PKG" "$OUT/logcat.txt" | tail -80 || true
  grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -120 || true
}
trap report EXIT

adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb shell cmd package set-home-activity "$PKG/.HomeActivity"
adb logcat -c
timeout 60 adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
sleep 6
adb exec-out screencap -p > "$OUT/home.png"

# 上へスワイプでアプリ一覧
adb shell input swipe 540 1700 540 700 250
sleep 3
adb exec-out screencap -p > "$OUT/drawer.png"
# 検索
adb shell input text "se"
sleep 2
adb exec-out screencap -p > "$OUT/search.png"
adb shell input keyevent KEYCODE_HOME
sleep 2
adb exec-out screencap -p > "$OUT/home-again.png"

# ドックの1列目（決済フォルダ）を押して窓を開く
W=$(adb shell wm size | sed -E 's/.* ([0-9]+)x([0-9]+).*/\1/')
H=$(adb shell wm size | sed -E 's/.* ([0-9]+)x([0-9]+).*/\2/')
adb shell input tap $((W / 10)) $((H - H * 9 / 100))
sleep 2
adb exec-out screencap -p > "$OUT/folder.png"
adb shell input keyevent KEYCODE_BACK
sleep 1

# 編集モード（2本指のジェスチャーは adb から送れないので直接開く）
timeout 30 adb root >/dev/null 2>&1 || true
timeout 60 adb wait-for-device || true
sleep 2
timeout 60 adb shell am start -n "$PKG/.EditActivity" || true
sleep 3
adb exec-out screencap -p > "$OUT/edit.png"
adb shell input keyevent KEYCODE_HOME

adb logcat -d > "$OUT/logcat.txt"
trap - EXIT
if grep -E "FATAL EXCEPTION|ANR in $PKG" "$OUT/logcat.txt"; then
  grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" || true
  echo "落ちた"
  exit 1
fi
adb shell pidof "$PKG" >/dev/null || { echo "ホームのプロセスが居ない"; exit 1; }
echo "落ちずに動いた"
