#!/usr/bin/env bash
# Downloads the Gradle distribution on the HOST (resumable, checksum-verified) into
# $GRADLE_DIST_DIR so containers never have to download it. The directory is
# mounted read-only at /gradle-dist by scripts/lib/common.sh:run_gradle.
# Usage: ./scripts/fetch-gradle.sh [gradle-version]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

version="${1:-$GRADLE_VERSION}"
mkdir -p "$GRADLE_DIST_DIR"
zip="$GRADLE_DIST_DIR/gradle-$version-bin.zip"
base="https://services.gradle.org/distributions/gradle-$version-bin.zip"

expected="$(curl -fsSL "$base.sha256")"
[ -n "$expected" ] || die "could not fetch checksum for $version"

verify() { [ -f "$zip" ] && [ "$(shasum -a 256 "$zip" | awk '{print $1}')" = "$expected" ]; }

if verify; then log "Already downloaded and verified: $zip"; exit 0; fi

log "Downloading Gradle $version to $zip (resumable; re-run if interrupted)"
# -C - resumes a partial file; --retry covers flaky connections.
curl -fL --retry 10 --retry-delay 3 --retry-all-errors -C - -o "$zip" "$base"

verify || { rm -f "$zip"; die "checksum mismatch for $zip; file removed, re-run to download again"; }
log "Verified sha256 $expected"
