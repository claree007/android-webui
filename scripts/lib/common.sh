#!/usr/bin/env bash
# Shared settings and helpers. Source this from other scripts; do not execute.
# Every value can be overridden through the environment.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Docker image that contains JDK + Android SDK (see docker/Dockerfile).
IMAGE="${ANDROID_WEBUI_IMAGE:-android-webui-build:2}"
# Named volume that persists the Gradle cache between runs.
GRADLE_VOLUME="${ANDROID_WEBUI_GRADLE_VOLUME:-android-webui-gradle-cache}"
DOCKER_MEMORY="${DOCKER_MEMORY:-8g}"
GRADLE_XMX="${GRADLE_XMX:-4g}"
GRADLE_WORKERS="${GRADLE_WORKERS:-2}"

# Gradle distribution is downloaded on the host (`scripts/dev setup`) and mounted
# read-only into containers, because downloads inside Docker proved slow/unreliable.
# The default version is the one pinned in the committed Gradle wrapper.
GRADLE_VERSION="${GRADLE_VERSION:-$(sed -n 's/.*gradle-\(.*\)-bin\.zip.*/\1/p' "$REPO_ROOT/gradle/wrapper/gradle-wrapper.properties")}"
GRADLE_DIST_DIR="${GRADLE_DIST_DIR:-$REPO_ROOT/.cache/gradle-dist}"

DEBUG_APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"

log() { printf '\033[1;34m[%s]\033[0m %s\n' "$(basename "$0")" "$*" >&2; }
die() { printf '\033[1;31m[%s] ERROR:\033[0m %s\n' "$(basename "$0")" "$*" >&2; exit 1; }

require_docker() {
  command -v docker >/dev/null 2>&1 || die "docker not found"
  docker info >/dev/null || die "docker daemon is not running"
}

require_image() {
  require_docker
  docker image inspect "$IMAGE" >/dev/null \
    || die "image '$IMAGE' not found. Run ./scripts/dev setup first."
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

