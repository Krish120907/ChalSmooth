#!/usr/bin/env bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

echo "=================================================="
echo "    Building ChalSmooth Android APK (Kotlin)      "
echo "=================================================="

# 1. Setup Java 17
if [ -d "/home/anjali/.android-toolchain/jdk-17.0.20.1+1" ]; then
    export JAVA_HOME="/home/anjali/.android-toolchain/jdk-17.0.20.1+1"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# 2. Setup Android SDK
export ANDROID_HOME="/home/anjali/.android-toolchain/android-sdk"
export ANDROID_SDK_ROOT="/home/anjali/.android-toolchain/android-sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "Using Java: $(which java || echo 'default')"
java -version 2>&1 | head -n 2 || true
echo "Android SDK: $ANDROID_HOME"

./gradlew assembleDebug --stacktrace

echo ""
echo "=================================================="
echo "✅ BUILD SUCCESSFUL!"
echo "APK Location: $DIR/app/build/outputs/apk/debug/app-debug.apk"
echo "=================================================="
