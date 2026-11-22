#!/usr/bin/env bash
# Runs INSIDE the build container (invoked by scripts/lib/common.sh:run_gradle).
# Not intended to be run on the host.
set -euo pipefail

cd /app
git config --global --add safe.directory /app

# Pre-seed the wrapper's cache from the host-downloaded zip mounted at /gradle-dist,
# so ./gradlew never has to download Gradle inside the container.
props=gradle/wrapper/gradle-wrapper.properties
if [ -f "$props" ]; then
  url="$(sed -n 's/^distributionUrl=//p' "$props" | sed 's/\\//g')"
  zip_name="$(basename "$url")"                       # gradle-X-bin.zip
  dist_name="${zip_name%.zip}"                        # gradle-X-bin
  hash="$(java /app/scripts/container/GradleWrapperHash.java "$url")"
  dest="$GRADLE_USER_HOME/wrapper/dists/$dist_name/$hash"
  if [ ! -f "$dest/$zip_name.ok" ]; then
    [ -f "/gradle-dist/$zip_name" ] || {
      echo "ERROR: /gradle-dist/$zip_name missing. Run ./scripts/dev setup on the host." >&2
      exit 1
    }
    echo "Seeding Gradle wrapper cache: $dest"
    rm -rf "$dest"; mkdir -p "$dest"
    unzip -q "/gradle-dist/$zip_name" -d "$dest"
    touch "$dest/$zip_name.ok"
  fi
fi

export GRADLE_OPTS="-Xmx${GRADLE_XMX:-2g} -XX:MaxMetaspaceSize=512m"

extra=()
if [ "$(uname -m)" = "aarch64" ]; then
  # Google's aapt2 is x86_64 only; the image wraps it with qemu-user.
  extra+=("-Pandroid.aapt2FromMavenOverride=${ANDROID_HOME}/build-tools/${ANDROID_BUILD_TOOLS}/aapt2")
fi

exec ./gradlew --no-daemon "--max-workers=${GRADLE_WORKERS:-2}" "${extra[@]}" "$@"
