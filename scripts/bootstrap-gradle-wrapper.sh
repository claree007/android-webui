#!/usr/bin/env bash
# One-time (or on upgrade): generates gradlew, gradlew.bat and gradle/wrapper/*
# inside the build container, pinning the distribution checksum.
# Uses the zip downloaded by ./scripts/fetch-gradle.sh (no download in the container).
# Usage: ./scripts/bootstrap-gradle-wrapper.sh [gradle-version]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

GRADLE_VERSION="${1:-$GRADLE_VERSION}"
"$REPO_ROOT/scripts/fetch-gradle.sh" "$GRADLE_VERSION"
require_image
log "Generating Gradle wrapper $GRADLE_VERSION"

docker run --rm -v "$REPO_ROOT:/app" -v "$GRADLE_DIST_DIR:/gradle-dist:ro" -w /app \
  -e GRADLE_VERSION="$GRADLE_VERSION" "$IMAGE" bash -euc '
  tmp=$(mktemp -d)
  zip=/gradle-dist/gradle-${GRADLE_VERSION}-bin.zip
  sum=$(sha256sum "$zip" | cut -d" " -f1)
  unzip -q "$zip" -d "$tmp"
  # gradle needs a settings file to run; use a throwaway one if the project has none yet.
  [ -f settings.gradle.kts ] || { touch settings.gradle.kts; created=1; }
  "$tmp/gradle-${GRADLE_VERSION}/bin/gradle" wrapper \
    --gradle-version "$GRADLE_VERSION" --distribution-type bin \
    --gradle-distribution-sha256-sum "$sum" --no-daemon
  [ "${created:-0}" = 1 ] && rm settings.gradle.kts
  rm -rf "$tmp"
'
chmod +x "$REPO_ROOT/gradlew"
log "Wrapper written. Commit gradlew, gradlew.bat and gradle/wrapper/."
