#!/bin/sh
# GadgetDrive — Gradle wrapper
# Downloads Gradle 8.1.1 on first run, then delegates to it.
APP_HOME=$(cd "$(dirname "$0")" && pwd)
PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
GRADLE_URL=$(grep '^distributionUrl=' "$PROPS" | sed 's/distributionUrl=//' | sed 's/\\//g' | tr -d '\r')
GRADLE_BASENAME=$(basename "$GRADLE_URL")
GRADLE_VERSION=$(echo "$GRADLE_BASENAME" | sed 's/gradle-//' | sed 's/-bin\.zip//' | sed 's/-all\.zip//')
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST="$GRADLE_HOME/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
EXE="$DIST/gradle-${GRADLE_VERSION}/bin/gradle"
if [ ! -f "$EXE" ]; then
    mkdir -p "$DIST"
    ZIP="$DIST/$GRADLE_BASENAME"
    echo "Downloading Gradle $GRADLE_VERSION ..."
    if command -v curl >/dev/null 2>&1; then
        curl -fL --progress-bar "$GRADLE_URL" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then
        wget -q --show-progress "$GRADLE_URL" -O "$ZIP"
    else
        echo "ERROR: curl or wget required." && exit 1
    fi
    echo "Extracting Gradle $GRADLE_VERSION ..."
    unzip -q "$ZIP" -d "$DIST"
    chmod +x "$EXE"
fi
exec "$EXE" "$@"