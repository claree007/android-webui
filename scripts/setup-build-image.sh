#!/usr/bin/env bash
# Builds the Docker image used for all Gradle builds.
# Usage: ./scripts/setup-build-image.sh [--no-cache]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

require_docker
log "Building image $IMAGE (first build downloads the SDK; takes several minutes)"
docker build "$@" -t "$IMAGE" -f "$REPO_ROOT/docker/Dockerfile" "$REPO_ROOT/docker"
log "Done: $IMAGE"
