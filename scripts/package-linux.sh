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

command -v jpackage >/dev/null || { echo "A JDK with jpackage is required." >&2; exit 1; }
./gradlew clean test installDist
mkdir -p build/native

jpackage \
    --type "$package_type" \
    --name AnimeEpisodeRandomizer \
    --app-version 1.0.0 \
    --description "Choose a random anime episode from your saved library" \
    --input build/install/AnimeEpisodeRandomizer/lib \
    --main-jar AnimeEpisodeRandomizer-1.0.0.jar \
    --main-class dev.atomixsoft.randomizer.App \
    --icon packaging/icon.png \
    --dest build/native \
    --add-modules java.desktop,java.net.http,jdk.crypto.ec,jdk.charsets

echo "Created $package_type output in build/native"
