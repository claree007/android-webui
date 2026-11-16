#!/usr/bin/env bash
# One-time (or on upgrade): generates gradlew, gradlew.bat and gradle/wrapper/*
# inside the build container, pinning the distribution checksum.
# Usage: ./scripts/bootstrap-gradle-wrapper.sh [gradle-version]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

GRADLE_VERSION="${1:-8.14.7}"
require_image
log "Generating Gradle wrapper $GRADLE_VERSION"

docker run --rm -v "$REPO_ROOT:/app" -w /app -e GRADLE_VERSION="$GRADLE_VERSION" "$IMAGE" bash -euc '
  tmp=$(mktemp -d)
  curl -fsSL -o "$tmp/g.zip" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  curl -fsSL -o "$tmp/g.zip.sha256" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip.sha256"
  sum=$(cat "$tmp/g.zip.sha256")
  echo "$sum  $tmp/g.zip" | sha256sum -c -
  unzip -q "$tmp/g.zip" -d "$tmp"
  # settings.gradle.kts must exist for gradle to run; create a throwaway one if missing.
  [ -f settings.gradle.kts ] || { touch settings.gradle.kts; created=1; }
  "$tmp/gradle-${GRADLE_VERSION}/bin/gradle" wrapper \
    --gradle-version "$GRADLE_VERSION" --distribution-type bin \
    --gradle-distribution-sha256-sum "$sum" --no-daemon
  [ "${created:-0}" = 1 ] && rm settings.gradle.kts
  rm -rf "$tmp"
'
chmod +x "$REPO_ROOT/gradlew"
log "Wrapper written. Commit gradlew, gradlew.bat and gradle/wrapper/."
