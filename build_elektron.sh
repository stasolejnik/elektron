#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
JAVA_HOME_17="${JAVA_HOME_17:-/usr/lib/jvm/java-17-openjdk}"
echo "═══════════════════════════════════════════════════════════════════"
echo "  ATOMIK — build na Arch Linux"
echo "═══════════════════════════════════════════════════════════════════"
if [ ! -x "$JAVA_HOME_17/bin/java" ]; then
    echo "→ Brak Javy 17 — instaluję (sudo pacman)..."
    sudo pacman -S --noconfirm jdk17-openjdk
fi
export JAVA_HOME="$JAVA_HOME_17"
export PATH="$JAVA_HOME/bin:$PATH"
echo "✔ Java: $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
mkdir -p "$SDK_ROOT/cmdline-tools"
if [ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "→ Pobieram Android cmdline-tools do $SDK_ROOT..."
    TMP="$(mktemp -d)"
    curl -fsSL -o "$TMP/tools.zip" "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    (cd "$TMP" && (unzip -q tools.zip 2>/dev/null || jar xf tools.zip))
    rm -rf "$SDK_ROOT/cmdline-tools/latest"
    mv "$TMP/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
    rm -rf "$TMP"
fi
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"
echo "→ Akceptuję licencje SDK..."
yes | sdkmanager --licenses >/dev/null 2>&1 || true
echo "→ Instaluję pakiety SDK..."
sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null
echo "✔ SDK gotowy"
echo "sdk.dir=$SDK_ROOT" > local.properties
if [ ! -x ./gradlew ] || [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
    if command -v gradle &>/dev/null; then
        echo "→ Generuję Gradle Wrapper..."
        gradle wrapper --gradle-version 8.9 --distribution-type bin
    else
        echo "❌ Brak gradlew i brak systemowego gradle. Zainstaluj: sudo pacman -S gradle"
        exit 1
    fi
fi
echo ""
./gradlew :app:assembleDebug
APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
    echo ""
    echo "═══════════════════════════════════════════════════════════════════"
    echo "  ✅ BUILD OK"
    echo "     APK:     $(realpath "$APK")"
    echo "     Rozmiar: $(du -h "$APK" | cut -f1)"
    echo ""
    echo "  Instalacja przez USB:"
    echo "     $SDK_ROOT/platform-tools/adb install -r \"$(realpath "$APK")\""
    echo "═══════════════════════════════════════════════════════════════════"
else
    echo "❌ Nie znaleziono APK"
    exit 1
fi
