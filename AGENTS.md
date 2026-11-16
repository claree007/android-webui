# AGENTS.md — Android native Open WebUI–style app

Read this first in every session. It records decisions and environment facts so they do not
have to be rediscovered. Keep it up to date: when a decision changes or a new pitfall is found,
edit this file in the same commit.

## 1. What this project is

A native Android app (Kotlin + Jetpack Compose) that behaves like Open WebUI for a single local
user. No WebView, no Open WebUI backend, no login. Chat goes to user-configured model endpoints
(Ollama, OpenAI-compatible, ...). Everything else (storage, RAG, search orchestration, tools) runs
on the device.

- **Source of truth for scope and phases:** [`new_implementation_plan.md`](new_implementation_plan.md).
  Follow its phase order, its "mandatory rules for each agent task" (section 6) and its definition of done.
- [`Implementation_plan.md`](Implementation_plan.md) is an older plan. It is **superseded; do not use it**.
- Work from scratch. **Do not reuse code from any earlier attempt** (`old-*` folders or files in the
  parent workspace).
- Commit as you go: small commits on `main`, one per vertical slice. Never push unless asked.

## 2. Decisions already made (do not re-ask)

| Topic | Decision |
|---|---|
| Application ID / package | `com.androidwebui.android` |
| License | Apache-2.0 (add `LICENSE` at repo root) |
| Branding | Do not use the "Open WebUI" name or logo in the app, code identifiers, or assets |
| Upstream reference | Pin by **commit SHA only**: `9bd84258d09eefe7bf975878fb0e31a5dadfe0f8`. **Never write an upstream version number or tag anywhere** (docs, code, UI, commit messages) |
| Upstream checkout | `../open-webui/` (sibling of this repo, read-only reference). Do not copy its code or assets |
| Screenshots | All of `screenshots/` are in scope; see section 6 |
| Visual approach | **Adaptation**, not a pixel copy: keep the look, use Android-native interactions |
| Themes | Light, dark and follow-system, switchable in settings. Only dark references exist; derive the light palette |
| Python runtime / plugins | **Deferred.** Skip Chaquopy, the Phase 2 Python spikes, and Phases 10–11 until asked |
| Early scope | Phase 0 (concise docs), Phase 1 (Docker build + foundation), thin slice of Phase 3 (native shell with fake data), then Phase 4 (chat) |
| Docs depth | Concise. Fill `docs/feature-parity.csv` rows only for phases actually built |
| minSdk / ABIs | minSdk 26; `arm64-v8a` + `x86_64` |
| Real endpoint | The owner has an Ollama endpoint. Enter it on the device. **Never commit URLs or keys** |

## 3. Everything must be scripted

The owner wants all setup captured as scripts so it can be committed and reused. If you do a setup
step by hand, add or update a script for it in the same change.

| Script | Purpose |
|---|---|
| `scripts/setup-build-image.sh` | Builds the Docker image from `docker/Dockerfile` |
| `scripts/bootstrap-gradle-wrapper.sh [ver]` | Generates the Gradle wrapper inside the container, pinning the checksum |
| `scripts/lib/common.sh` | Shared settings and helpers (sourced, never executed) |
| `scripts/container/run-gradle.sh` | Runs inside the container; applies memory options and the aarch64 `aapt2` override |
| `scripts/build-debug.sh`, `test-unit.sh`, `lint.sh` | Planned (Phase 1): Docker-based checks |
| `scripts/test-connected.sh`, `install-debug.sh` | Planned (Phase 1): use host `adb`; must print NOT RUN when no device is attached |

Rules for scripts: `set -euo pipefail`, source `scripts/lib/common.sh`, no hard-coded user paths,
overridable through environment variables, clear failure messages, executable bit set.

## 4. Environment facts (verified on the owner's machine)

- Host: macOS, **arm64**. Docker runs Linux **aarch64** containers.
- Our image: `android-webui-build:1`, built from `docker/Dockerfile` (Ubuntu 24.04, JDK 17,
  Android platform 36, build-tools 36.0.0, cmdline-tools). Rebuild with `./scripts/setup-build-image.sh`.
- Google's `aapt2` is x86_64 only. The Dockerfile wraps it with `qemu-x86_64-static`, and
  `run-gradle.sh` passes `-Pandroid.aapt2FromMavenOverride=...` on aarch64. Expect slow resource
  steps. If an `aapt2` error appears, check this first.
- Gradle cache lives in the named volume `android-webui-gradle-cache`.
- Container memory: `-m 8g`, `GRADLE_OPTS=-Xmx4g -XX:MaxMetaspaceSize=1g`, `--max-workers=2`, `--no-daemon`.
  The Docker VM has about 8 GB total, so do not raise these blindly.
- Host `adb` is at `~/android-sdk/platform-tools/adb`. A device may or may not be attached
  (`adb devices -l`). If none is attached, report connected tests as **not run**, never as passed.
- The owner's older image `android-dev-env` and volume `open-webui-gradle-cache` exist but belong to
  an earlier attempt. Do not depend on them.
- Long commands (image build, first Gradle build) exceed the 30 s tool timeout. Run them in the
  background with a log file, then poll the log. Do not run several heavy Docker jobs at once.
  Before starting a build, check `docker ps` for leftover containers from interrupted runs.

## 5. Toolchain pins (looked up from the registries on 2026-11-16)

Verify before use. Anything marked "stable" excludes alpha/beta/RC.

| Item | Version |
|---|---|
| Gradle | 8.14.7 (wrapper). 9.x exists but the AGP line below targets 8.x |
| Android Gradle Plugin | 8.13.2 (latest 8.x stable) |
| Kotlin | 2.4.21 |
| KSP | 2.3.14 (confirm it is compatible with the Kotlin version above; adjust if resolution fails) |
| Compose BOM | 2026.09.00 (resolves material3 1.4.0) |
| Navigation Compose | 2.10.2 |
| Room | 2.8.5 |
| DataStore | 1.3.0 |
| Hilt | 2.60.1, `hilt-navigation-compose` 1.4.0 |
| Activity Compose | 1.13.0 (latest stable) |
| kotlinx-serialization-json | 1.13.0 |
| kotlinx-coroutines | 1.11.0 |
| compileSdk / targetSdk | 36 (matches the image's installed platform) |

Keep all versions in `gradle/libs.versions.toml`. Enable Gradle dependency verification when practical.

## 6. Screenshots (`screenshots/`, 780x1688, dark theme, phone-sized)

Many files are byte-identical duplicates (found with `md5`) or have misleading names. The mapping
below comes from opening a representative subset and matching the rest by checksum and filename.
Files I did **not** open (for example most `admin-*`, `settings-interface-scroll*`,
`workspace-create-model-bottom`, `19-admin-users`, `20/21-` evaluations/functions, `27-playground-scrolled`)
are mapped by name only. Re-check them when you build the matching screen, and correct this table.

| Screen / state | Files |
|---|---|
| New chat, empty (greeting, composer, suggested prompts) | `01-new-chat*.png`, `02-sidebar-open.png`(base) |
| Sidebar: New Chat, Search, Models list, Folders, Chats grouped by Today/Yesterday/Previous 7 days, user row | `03-sidebar-open.png`, `03-sidebar.png` |
| User menu: Workspace, Calendar, Automations, Playground, Admin Panel, Settings, Sign Out (sign-out/admin excluded) | `04-user-menu-popover.png`, `user-menu-full.png` |
| Settings tabs, shown as a full-height sheet with a Back chip and horizontally scrolling tabs | General `05-`, Interface `06/07/08-`, Notifications `09-`, Connections `10-`, Personalisation `11-`, Audio `12-`, Data Controls `13-settings-data.png`; also Keyboard, Integrations, Usage, Archived chats (tab names visible only) |
| Chat thread: markdown, sources chip, action row (edit, copy, speak, info, continue, regenerate, branch), follow-up suggestions | `17-`, `29-`, `30-`, `chat-thread-*.png` |
| Chat controls panel (advanced parameters) | `31-chat-controls-panel.png`, `chat-controls-panel.png` |
| Model selector sheet | `32-model-selector-sheet.png`, `model-selector.png` |
| Attach picker | `33-attach-picker.png`, `attach-picker.png` (**the files are plain new-chat captures; the picker itself is not visible: record as a missing reference**) |
| Workspace tabs (Models, Knowledge, Prompts, Skills, Tools) with search, filter and Create | Models `06/14-`, Knowledge `07/15-`, Prompts `08/16-`, Tools `09/17-`, Skills `workspace-skills.png`; all empty states |
| Create/edit model form | `18-workspace-create-model.png`, `workspace-create-model*.png` |
| Playground (Chat / Completions / Images) | `16/26/27-playground*.png` |
| Notes list and editor | `13-notes.png`, `23-`, `24-`, `notes-*.png` (**several are plain new-chat captures: record as missing**) |
| Admin settings (general, auth, connections, ...) | `12/22-admin-settings*.png`, `admin-settings-*.png`: map to local settings; multi-user parts excluded |
| Admin evaluations, functions | `20/21-`, `admin-*.png` |
| **404 error captures (not UI references)** | `10-workspace-functions.png`, `14-channels.png`, `15-memories.png`, `25-channels-list.png`, `28-memories.png`, `channels*.png`, `memories-page.png`: these are identical "404: Not Found" pages |

Excluded as multi-user or server-only (flag in `docs/feature-parity.csv`, reason required):
Channels, Admin → Users, Sign Out, account registration/auth, SCIM/groups, Shared Chats and
Community Sharing (no server). Everything else in admin screens moves to local settings or
workspace management.

Design notes: near-black surfaces (about `#171717`/`#0d0d0d`), white filled primary buttons (Save,
voice), pill composer with `+`, tools button, model selector chip, mic and voice button; rounded
panels; muted grey secondary text; the dashed circle icon is temporary chat. Replace the logo with an
original mark. The update banner is not a feature.

## 7. Architecture rules (from the plan; short form)

- Modules are created gradually: `app`, `core/*`, `engine/*`, `feature/*`. Do not generate empty modules.
- UI never calls providers directly. Domain code must not depend on Compose.
- Every optional integration sits behind an interface and has a fake for tests.
- Capability states: Available, NeedsConfiguration, NeedsDownload, UnsupportedDevice,
  UnsupportedProvider, DisabledByUser, UnavailableWithReason.
- Conversations are a **message graph** from the start (branches, never destructive edits).
- No silent fallbacks. No external service enabled implicitly. Secrets only in Keystore-backed
  storage; never in logs, source or exports. Never "trust all certificates".

## 8. Verification discipline

Before reporting a task done: run the relevant Docker checks (`build-debug`, `test-unit`, `lint`)
and report the exact commands and results. Say explicitly what was **not run** and why (for
example, no device attached). Update `docs/feature-parity.csv` and any doc a change touches. Leave
the repository buildable and commit.

## 9. Status log (update at the end of each work session)

- 2026-11-16: Plan reviewed, questions answered, decisions recorded above. Committed plan and
  screenshots. Added `docker/Dockerfile` and the scripts in section 3. Image `android-webui-build:1`
  builds successfully. **Next:** generate the Gradle wrapper (the first attempt hung at the Gradle
  download inside the container; run it in the background with a log), then Phase 0 docs, then the
  project skeleton.
