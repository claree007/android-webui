# Android WebUI

A native Android app (Kotlin, Jetpack Compose) inspired by Open WebUI. It is not a WebView wrapper and needs no Open WebUI backend. Chats and settings are stored locally, and models are reached through endpoints you configure. Product scope, decisions and the phase breakdown are in [`new_implementation_plan.md`](new_implementation_plan.md).

## Quick start

Requires Docker (running), `curl` and `python3`. No Android tooling is needed on the host.

```bash
./scripts/dev setup   # one-time: build the Docker image, download Gradle
./scripts/dev test    # JVM unit tests
./scripts/dev build   # debug APK -> app/build/outputs/apk/debug/app-debug.apk
```

All commands, environment variables and troubleshooting: [`DEVELOPMENT.md`](DEVELOPMENT.md).

## Repository layout

| Path | Purpose |
|---|---|
| `app/` | The Android app module (`src/main`, `src/test`) |
| `scripts/dev` | Single entry point: `setup`, `build`, `test`, `lint`, `schema` |
| `scripts/lib`, `scripts/container` | Shared shell helpers and the script that runs inside the build container |
| `docker/` | Build-image definition (JDK 17 + Android SDK) |
| `docs/architecture.md` | Code layout and dependency justifications |
| `new_implementation_plan.md` | Product scope, decisions D1-D22, implementation phases |
| `screenshots/` | Visual references for the UI |
| `AGENTS.md` | Rules for AI coding agents |

## Documentation

- [`DEVELOPMENT.md`](DEVELOPMENT.md): build, test, lint, troubleshooting
- [`docs/architecture.md`](docs/architecture.md): what the code contains today
- [`new_implementation_plan.md`](new_implementation_plan.md): what is being built and why
- [`AGENTS.md`](AGENTS.md): conventions, safety rules and definition of done
