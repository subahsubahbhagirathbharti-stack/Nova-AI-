#!/data/data/com.termux/files/usr/bin/bash
# Nova AI - Termux build script. Usage: bash build.sh
set -e
cd "$(dirname "$0")"

echo "==> Installing tools (first run only)"
pkg install -y openjdk-17 gradle aapt2 unzip wget >/dev/null 2>&1 || true

# Android SDK location (needs platforms/android-34 and build-tools/34.0.0)
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
if [ ! -d "$ANDROID_HOME/platforms/android-34" ]; then
  echo "!! Android SDK not found at $ANDROID_HOME (need platforms/android-34 + build-tools 34.0.0)."
  echo "   Install it (e.g. via a Termux android-sdk setup script or sdkmanager), then re-run."
  echo "   Or set a different path:  ANDROID_HOME=/path/to/sdk bash build.sh"
  exit 1
fi
echo "sdk.dir=$ANDROID_HOME" > local.properties

echo "==> Building APK"
gradle assembleDebug --no-daemon \
  -Pandroid.aapt2FromMavenOverride="$PREFIX/bin/aapt2"

APK=app/build/outputs/apk/debug/app-debug.apk
if [ -f "$APK" ]; then
  mkdir -p /sdcard/Download 2>/dev/null && cp "$APK" /sdcard/Download/NovaAI.apk 2>/dev/null \
    && echo "==> Copied to /sdcard/Download/NovaAI.apk (run termux-setup-storage if this failed)"
  echo "==> DONE: $APK"
else
  echo "!! Build failed - copy the error text and send it to Claude."
fi
