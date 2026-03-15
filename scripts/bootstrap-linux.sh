#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
CMDLINE_TOOLS_DIR="$ANDROID_SDK_ROOT/cmdline-tools/latest"
TOOLS_BIN="$CMDLINE_TOOLS_DIR/bin"
ZIP_PATH="/tmp/commandlinetools-linux.zip"
URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

ensure_jdk() {
  if command -v java >/dev/null 2>&1; then
    return
  fi
  if command -v apt-get >/dev/null 2>&1; then
    sudo apt-get update
    sudo apt-get install -y openjdk-21-jdk unzip curl
  elif command -v dnf >/dev/null 2>&1; then
    sudo dnf install -y java-21-openjdk-devel unzip curl
  elif command -v pacman >/dev/null 2>&1; then
    sudo pacman -Sy --noconfirm jdk21-openjdk unzip curl
  else
    echo "Install JDK 21 manually and re-run." >&2
    exit 1
  fi
}

ensure_cmdline_tools() {
  if [[ -x "$TOOLS_BIN/sdkmanager" ]]; then
    return
  fi
  mkdir -p "$ANDROID_SDK_ROOT"
  curl -L "$URL" -o "$ZIP_PATH"
  rm -rf /tmp/android-cmdline-tools
  unzip -q "$ZIP_PATH" -d /tmp/android-cmdline-tools
  mkdir -p "$(dirname "$CMDLINE_TOOLS_DIR")"
  rm -rf "$CMDLINE_TOOLS_DIR"
  mv /tmp/android-cmdline-tools/cmdline-tools "$CMDLINE_TOOLS_DIR"
}

ensure_jdk
ensure_cmdline_tools

export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$TOOLS_BIN:$ANDROID_SDK_ROOT/platform-tools:$PATH"

yes | sdkmanager --licenses >/dev/null || true
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "cmdline-tools;latest" "ndk;27.2.12479018" "cmake;3.31.1"

cd "$REPO_ROOT/android-app"
./gradlew :app:assembleDemoDebug
