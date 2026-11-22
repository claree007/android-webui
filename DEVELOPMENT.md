# Development

Everything builds inside Docker. Do not install Android tooling on the host. Run all commands from the repository root.

## Prerequisites

- Docker, running.
- `curl` and `python3` on the host.
- Optional: host `adb` for device checks (never run through Docker).

## One-time setup

```bash
./scripts/dev setup   # builds the Docker image (JDK 17 + Android SDK 36) and downloads Gradle into .cache/gradle-dist
```

## Commands

Run `./scripts/dev help` for the list. Extra arguments after `build`, `test` and `lint` are passed to Gradle.

| Command | Purpose | Output |
|---|---|---|
| `./scripts/dev setup [--no-cache]` | Builds the build image from `docker/Dockerfile`, then downloads the Gradle version pinned in `gradle/wrapper/gradle-wrapper.properties` to the host so containers never download it. Safe to re-run; an already verified download is skipped | Docker image (see `IMAGE` in `scripts/lib/common.sh`), `.cache/gradle-dist/` |
| `./scripts/dev build [gradle args]` | Builds the debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| `./scripts/dev test [gradle args]` | Runs JVM unit tests, for example `--tests '*Schema*'` | `app/build/reports/tests/` |
| `./scripts/dev lint [gradle args]` | Runs Android lint | `app/build/reports/lint-results-debug.html` |
| `./scripts/dev schema [webui.db]` | Regenerates the schema asset and test fingerprint from the Open WebUI web app's reference DB (default `../webui.db`). Reads DDL and the alembic revision only. Needs host `python3` | `app/src/main/assets/webui_schema.sql`, `app/src/test/resources/open-webui-schema-fingerprint.txt` |

Environment overrides (defaults in `scripts/lib/common.sh`): `ANDROID_WEBUI_IMAGE`, `ANDROID_WEBUI_GRADLE_VOLUME`, `DOCKER_MEMORY` (default `8g`), `GRADLE_XMX` (default `4g`), `GRADLE_WORKERS` (default `2`), `GRADLE_VERSION`, `GRADLE_DIST_DIR`.

Gradle runs with `--no-daemon`, so each run takes a while. Use the narrowest check while iterating.

## Failure modes

- `image '…' not found`: run `./scripts/dev setup`.
- `/gradle-dist/… missing`: run `./scripts/dev setup`.
- `docker daemon is not running`: start Docker.
- Resource linking fails after moving resource folders: run `./scripts/dev build clean :app:assembleDebug`.
- On arm64 hosts aapt2 runs under qemu-user, so builds are slower. The scripts pass the needed Gradle option automatically.

## Regenerating the schema

When the Open WebUI web app's reference database changes, run `./scripts/dev schema`, then `./scripts/dev test`, and commit the two generated files. Never commit or ship the reference `webui.db` itself.

## Upgrading Gradle

Use the standard wrapper task, then re-run setup so the new distribution is downloaded:

```bash
./gradlew wrapper --gradle-version <version> --distribution-type bin --gradle-distribution-sha256-sum <sha256>
./scripts/dev setup
```

The checksum is published at `https://services.gradle.org/distributions/gradle-<version>-bin.zip.sha256`.

## Device checks

Not set up yet. Report device checks as **not run** until a documented host ADB procedure exists.
