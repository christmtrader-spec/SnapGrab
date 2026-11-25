#!/usr/bin/env bash
# Build & sign the SnapGrab APK without Gradle: aapt2 -> javac -> d8 -> zip -> zipalign -> sign.
#
# Requirements:
#   - Android SDK with build-tools (34+) and platform android-35  (ANDROID_HOME or ANDROID_SDK_ROOT)
#   - JDK 17+ (JAVA_HOME must point to the JDK)
#   - zip, base64, python3 (only if binary assets need restoring from tools/bin/)
#
# Usage:  ./build_apk.sh          (writes SnapGrab.apk into the repo root)
set -euo pipefail

PROJ="$(cd "$(dirname "$0")" && pwd)"
OUT="$PROJ/build"
KS="$PROJ/snapgrab.keystore"
KS_ALIAS="snapgrab"
KS_PASS="snapgrab2026"

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:=$HOME/Android/Sdk}}"
SDK="$ANDROID_HOME"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK (17+) with javac}"

# --- restore binary assets (icons / keystore) if they are not materialized yet ---
if [ ! -f "$PROJ/res/mipmap-mdpi/ic_launcher.png" ] || [ ! -f "$KS" ]; then
  echo "[0/8] restoring binary assets from tools/bin/ ..."
  bash "$PROJ/tools/restore_binaries.sh"
fi

BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)"
PLATFORM="$(ls "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1 || true)"
[ -n "$BT" ] || { echo "ERROR: no build-tools under $SDK (run: sdkmanager \"build-tools;34\")"; exit 1; }
[ -n "$PLATFORM" ] || { echo "ERROR: no android.jar under $SDK (run: sdkmanager \"platforms;android-35\")"; exit 1; }
echo "Using build-tools: $BT"
echo "Using platform:    $PLATFORM"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/obj" "$OUT/dex"

echo "[1/8] aapt2 compile resources..."
"$BT/aapt2" compile --dir "$PROJ/res" -o "$OUT/res.zip"

echo "[2/8] aapt2 link (manifest + resources + R.java)..."
"$BT/aapt2" link -o "$OUT/base.apk" \
  -I "$PLATFORM" \
  --manifest "$PROJ/AndroidManifest.xml" \
  --java "$OUT/gen" \
  -R "$OUT/res.zip" \
  --auto-add-overlay \
  --min-sdk-version 23 \
  --target-sdk-version 33

echo "[3/8] javac..."
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 1.8 -target 1.8 -nowarn \
  -bootclasspath "$PLATFORM" \
  -d "$OUT/obj" \
  "$OUT/gen/com/snapgrab/app/R.java" \
  $(find "$PROJ/java" -name '*.java')

echo "[4/8] d8 dex..."
"$BT/d8" --release --lib "$PLATFORM" --min-api 23 \
  --output "$OUT/dex" \
  $(find "$OUT/obj" -name '*.class')

echo "[5/8] add classes.dex to apk..."
(cd "$OUT/dex" && zip -q "$OUT/base.apk" classes.dex)

echo "[6/8] zipalign..."
"$BT/zipalign" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "[7/8] sign v1 (jarsigner)..."
"$JAVA_HOME/bin/jarsigner" -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
  -signedjar "$OUT/signed-v1.apk" "$OUT/aligned.apk" "$KS_ALIAS"

echo "[8/8] sign v2+v3 (apksigner)..."
"$BT/apksigner" sign --ks "$KS" --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$PROJ/SnapGrab.apk" "$OUT/signed-v1.apk"

"$BT/apksigner" verify --print-certs "$PROJ/SnapGrab.apk" || true
echo "BUILD_OK: $PROJ/SnapGrab.apk"
ls -la "$PROJ/SnapGrab.apk"
