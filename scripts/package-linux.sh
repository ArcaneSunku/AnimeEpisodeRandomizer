#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != "Linux" ]]; then
    echo "Linux packages must be built on Linux." >&2
    exit 1
fi

package_type="${1:-app-image}"
case "$package_type" in
    app-image|deb|rpm) ;;
    *) echo "Expected app-image, deb, or rpm." >&2; exit 1 ;;
esac

if [[ -n "${JAVA_HOME:-}" ]]; then
    jpackage_bin="$JAVA_HOME/bin/jpackage"
else
    jpackage_bin="$(command -v jpackage || true)"
fi

if [[ -z "$jpackage_bin" || ! -x "$jpackage_bin" ]]; then
    echo "Could not find jpackage. Set JAVA_HOME to Java 21 JDK." >&2
    exit 1
fi

jpackage_version="$("$jpackage_bin" --version)"

if [[ ! "$jpackage_version" =~ ^21([.]|[+]|$) ]]; then
    echo "Packaging requires JDK 21, but jpackage reports $jpackage_version." >&2
    echo "Set JAVA_HOME to your Java 21 JDK and try again." >&2
    exit 1
fi

echo "Packaging with JDK $jpackage_version"
./gradlew clean test installDist
mkdir -p build/native

"$jpackage_bin" \
    --type "$package_type" \
    --name AnimeEpisodeRandomizer \
    --app-version 1.0.0 \
    --description "Choose a random anime episode from your saved library" \
    --input build/install/AnimeEpisodeRandomizer/lib \
    --main-jar AnimeEpisodeRandomizer-1.0.0.jar \
    --main-class dev.atomixsoft.randomizer.App \
    --icon packaging/icon.png \
    --dest build/native \
    --add-modules java.desktop,java.net.http,jdk.crypto.ec,jdk.charsets,jdk.random

echo "Created $package_type output in build/native"
