#!/usr/bin/env bash
# Runs INSIDE the build container (invoked by scripts/lib/common.sh:run_gradle).
# Not intended to be run on the host.
set -euo pipefail

cd /app
git config --global --add safe.directory /app

export GRADLE_OPTS="-Xmx${GRADLE_XMX:-4g} -XX:MaxMetaspaceSize=1g"

extra=()
if [ "$(uname -m)" = "aarch64" ]; then
  # Google's aapt2 is x86_64 only; the image wraps it with qemu-user.
  extra+=("-Pandroid.aapt2FromMavenOverride=${ANDROID_HOME}/build-tools/${ANDROID_BUILD_TOOLS}/aapt2")
fi

exec ./gradlew --no-daemon "--max-workers=${GRADLE_WORKERS:-2}" "${extra[@]}" "$@"
