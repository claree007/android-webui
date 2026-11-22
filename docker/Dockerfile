# Build environment for the Android app. Docker is a BUILD tool only; the app
# has no runtime dependency on it.
#
# Build with:   ./scripts/setup-build-image.sh
#
# Works on amd64 and arm64 hosts. On arm64, Google only ships x86_64 builds of
# aapt2, so it is wrapped with qemu-user (slower, but fully functional).
FROM ubuntu:24.04

ARG TARGETARCH
ARG CMDLINE_TOOLS_VERSION=11076708
ARG ANDROID_PLATFORM=36
ARG ANDROID_BUILD_TOOLS=36.0.0

ENV DEBIAN_FRONTEND=noninteractive \
    LC_ALL=C.UTF-8 \
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-${TARGETARCH} \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/root/.gradle \
    ANDROID_BUILD_TOOLS=${ANDROID_BUILD_TOOLS}

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      openjdk-17-jdk-headless git curl unzip zip ca-certificates \
 && if [ "$TARGETARCH" = "arm64" ]; then \
      apt-get install -y --no-install-recommends qemu-user-static libc6-amd64-cross libgcc-s1-amd64-cross libstdc++6-amd64-cross; \
    fi \
 && rm -rf /var/lib/apt/lists/*

# Android command-line tools, then the pinned SDK packages.
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
 && curl -fsSL -o /tmp/cmdline-tools.zip \
      https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip \
 && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools \
 && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
 && rm /tmp/cmdline-tools.zip

ENV PATH=${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}

RUN yes | sdkmanager --licenses > /dev/null \
 && sdkmanager "platform-tools" "platforms;android-${ANDROID_PLATFORM}" "build-tools;${ANDROID_BUILD_TOOLS}"

# arm64 hosts: run the x86_64 aapt2 through qemu. Projects must point AGP at it
# via -Pandroid.aapt2FromMavenOverride (done by scripts/container/run-gradle.sh).
RUN if [ "$TARGETARCH" = "arm64" ]; then \
      d=${ANDROID_HOME}/build-tools/${ANDROID_BUILD_TOOLS}; \
      mv $d/aapt2 $d/aapt2.real \
   && printf '#!/bin/sh\nexec qemu-x86_64-static -L /usr/x86_64-linux-gnu "$(dirname "$0")/aapt2.real" "$@"\n' > $d/aapt2 \
   && chmod +x $d/aapt2; \
    fi

WORKDIR /app
CMD ["bash"]
