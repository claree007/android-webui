# AGENTS.md

Rules for AI coding agents working in this repository. Everything a human developer needs (setup, commands, troubleshooting) is in [`DEVELOPMENT.md`](DEVELOPMENT.md); the overview is in [`README.md`](README.md). This file only covers how agents should work.

## Source of truth

1. [`new_implementation_plan.md`](new_implementation_plan.md) is authoritative for product scope, architecture, decisions (D1-D22) and phases. Its section 1a decisions override anything else.
2. [`docs/architecture.md`](docs/architecture.md) records what the code currently contains.
3. `screenshots/` holds the visual references.
4. `../webui.db` is the Open WebUI web app's schema reference. Read it only to copy DDL. Never copy its data, and never commit or ship it.
5. If a task conflicts with the plan, stop and say so. Do not silently pick a side.

## Before you start a task

- Find the phase and its dependencies in the plan. Do not start a phase whose dependencies are unmet.
- Work on one bounded vertical slice. Use the task template and completion report format in plan section 6.
- Check `git status` first. Keep unrelated working-tree changes out of your edits.

## Build and test

Use `./scripts/dev` (see [`DEVELOPMENT.md`](DEVELOPMENT.md)); do not install Android tooling on the host or rely on Android Studio.

- Run the narrowest check that covers your change while iterating, and `test`, `build` and `lint` before reporting done.
- Never hide output. Run `./scripts/dev` commands in the foreground so the output is visible live. Do not start them in the background, do not pipe build, test or lint output through `tail`, `head` or `grep`, and do not redirect it to a file or `/dev/null`. Scripts must not suppress output either. Show the full output and report results from it.
- Device access goes through the host `adb`, never through Docker. If no device is available, report device checks as **not run**; never mark them passed.
- Any new or changed script, command or setup step must be documented in `DEVELOPMENT.md` in the same change (D21).

## Conventions

- Kotlin, Jetpack Compose, minimal dependencies. Hilt, KSP, Room and DataStore are excluded by the plan (D4, D6, D7). Do not add them.
- Before adding a dependency, write its one-line justification in `docs/architecture.md` (D8).
- Match the style of the surrounding code. Do not reformat files you are not otherwise changing.
- Do not add comments that restate the code. Comment only non-obvious reasons.
- Write code independently. Never copy Open WebUI web app code, prompts or assets. Record the provenance of anything copied in `docs/provenance.md` (D20; create it when first needed).

## Safety rules

- Never log, print or commit secrets: API keys, tokens, signing keys, keystores (`*.jks`, `*.keystore`), or `local.properties`.
- Do not send user content to any external service as a fallback. Network use must follow the plan's configuration and disclosure rules.
- Tests must not call real providers. Use fakes and a mock HTTP server. Real-service tests run only with credentials the user supplies explicitly.
- Do not run destructive commands (`git reset --hard`, `git clean`, force-push, deleting volumes or images) unless asked.
- Do not commit generated output: `build/`, `.gradle/`, `.kotlin/`, `.cache/`, APKs.

## Definition of done

Follow the plan's Definition of done, and the checklist below.

- The relevant Docker checks ran and passed, and you report the exact commands and results.
- No new lint warnings and no skipped or disabled tests.
- Docs updated: `DEVELOPMENT.md` when scripts or setup changed, and `docs/` when behavior or a decision changed.
- The repository still builds.

If you could not verify something, say what was not run and why. Do not claim a result you did not observe.
