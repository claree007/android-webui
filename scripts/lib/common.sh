#!/usr/bin/env bash
# Shared settings and helpers. Source this from other scripts; do not execute.
# Every value can be overridden through the environment.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Docker image that contains JDK + Android SDK (see docker/Dockerfile).
IMAGE="${ANDROID_WEBUI_IMAGE:-android-webui-build:1}"
# Named volume that persists the Gradle cache between runs.
GRADLE_VOLUME="${ANDROID_WEBUI_GRADLE_VOLUME:-android-webui-gradle-cache}"
DOCKER_MEMORY="${DOCKER_MEMORY:-8g}"
GRADLE_XMX="${GRADLE_XMX:-4g}"
GRADLE_WORKERS="${GRADLE_WORKERS:-2}"

# Gradle distribution is downloaded on the host (scripts/fetch-gradle.sh) and mounted
# read-only into containers, because downloads inside Docker proved slow/unreliable.
GRADLE_VERSION="${GRADLE_VERSION:-8.14.7}"
GRADLE_DIST_DIR="${GRADLE_DIST_DIR:-$REPO_ROOT/.cache/gradle-dist}"

APP_ID="com.androidwebui.android"
DEBUG_APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$REPO_ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

log() { printf '\033[1;34m[%s]\033[0m %s\n' "$(basename "$0")" "$*" >&2; }
die() { printf '\033[1;31m[%s] ERROR:\033[0m %s\n' "$(basename "$0")" "$*" >&2; exit 1; }

require_docker() {
  command -v docker >/dev/null 2>&1 || die "docker not found"
  docker info >/dev/null 2>&1 || die "docker daemon is not running"
}

require_image() {
  require_docker
  docker image inspect "$IMAGE" >/dev/null 2>&1 \
    || die "image '$IMAGE' not found. Run ./scripts/setup-build-image.sh first."
}

# Run ./gradlew inside the build container with the given arguments.
run_gradle() {
  require_image
  docker run --rm -m "$DOCKER_MEMORY" \
    -v "$REPO_ROOT:/app" \
    -v "$GRADLE_VOLUME:/root/.gradle" \
    -v "$GRADLE_DIST_DIR:/gradle-dist:ro" \
    -e GRADLE_VERSION="$GRADLE_VERSION" \
    -e GRADLE_XMX="$GRADLE_XMX" \
    -e GRADLE_WORKERS="$GRADLE_WORKERS" \
    "$IMAGE" bash /app/scripts/container/run-gradle.sh "$@"
}

# Pick an adb binary on the host (device access is never done from Docker).
find_adb() {
  if command -v adb >/dev/null 2>&1; then command -v adb
  elif [ -x "${ANDROID_HOME:-}/platform-tools/adb" ]; then echo "$ANDROID_HOME/platform-tools/adb"
  elif [ -x "$HOME/android-sdk/platform-tools/adb" ]; then echo "$HOME/android-sdk/platform-tools/adb"
  else return 1; fi
}

# Print the serial of the target device, honouring $ANDROID_SERIAL.
# Exit codes: 0 ok, 3 no device (NOT RUN), 4 several devices and no serial given.
select_device() {
  local adb="$1"
  if [ -n "${ANDROID_SERIAL:-}" ]; then echo "$ANDROID_SERIAL"; return 0; fi
  local devices
  devices="$("$adb" devices | awk 'NR>1 && $2=="device" {print $1}')"
  local n; n="$(printf '%s\n' "$devices" | grep -c . || true)"
  if [ "$n" -eq 0 ]; then return 3; fi
  if [ "$n" -gt 1 ]; then return 4; fi
  echo "$devices"
}
