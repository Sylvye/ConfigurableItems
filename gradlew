#!/usr/bin/env sh
set -e
GRADLE_VERSION=8.14.3
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_DIR="$BASE_DIR/.gradle/wrapper-local"
GRADLE_HOME="$WRAPPER_DIR/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$WRAPPER_DIR"
  ZIP="$WRAPPER_DIR/gradle-$GRADLE_VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"
    else
      wget -q "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$ZIP"
    fi
  fi
  unzip -q -o "$ZIP" -d "$WRAPPER_DIR"
fi

exec "$GRADLE_BIN" "$@"
