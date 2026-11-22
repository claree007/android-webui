# Provenance

Records what was taken from outside this repository (D20). The app is Apache-2.0 and carries no Open WebUI name, logo or branding.

## Reference revision

| Item | Value |
|---|---|
| Upstream | https://github.com/open-webui/open-webui |
| Pinned commit | `9bd84258d09eefe7bf975878fb0e31a5dadfe0f8` (committed 2026-03-27), package version 0.8.12 |
| Local checkout | `../open-webui` (read only; never modified) |
| Reference database | `../webui.db`, a database created by an Open WebUI instance. Its alembic revision is recorded in `app/src/test/resources/open-webui-schema-fingerprint.txt` |

The reference screenshots in `screenshots/` show a newer web app version (they display a "v0.12.5 available" banner) than the pinned source. Where the two differ, the pinned source defines stored data formats and the screenshots define the visual design. Phase 0 did not check how far they differ.

## License of the upstream code

The upstream `LICENSE` is a BSD-3-Clause license with an added branding clause (clause 4: the "Open WebUI" name, logo and visual identifiers must not be removed or replaced, with exceptions in clause 5). Clause 6 says code added before the branding clause stays under BSD-3-Clause. The upstream `LICENSE_HISTORY` states that code created before commit `60d84a3aae9802339705826e9095e272e3c83623` ("chore: license \"branding\" clause") is under the older BSD-3-Clause terms. The pinned commit is after it, so everything in the pinned checkout is treated as under the branding license (D20).

Consequence for this project: no upstream source, prompt text or branded asset is copied. Behavior is described in `behavior-specs.md` and implemented independently.

## What has been taken

| Item | Source | License position | Where used |
|---|---|---|---|
| Database schema (DDL only: table, column, type and index definitions, alembic revision) | `../webui.db` | Schema structure needed for file compatibility (D1). **Open question:** the schema is derived from upstream models, so whether DDL is copyrightable under the upstream license has not been reviewed. It is the minimum needed for interoperability. Review before the first release | `app/src/main/assets/webui_schema.sql`, `app/src/test/resources/open-webui-schema-fingerprint.txt` |
| Setting key names (`ui.*`, `task.*`, `openai.*`, `ollama.*`) | Upstream config and settings code | Names of stored keys, needed for compatibility. No code copied | `behavior-specs.md`, later app code |

No data from `webui.db` is copied or committed. The file is never shipped.

## Not taken

- Source code, prompt templates, translations, icons, logos and fonts.
- Screenshots are used only as visual references in this repository. They show the upstream branding. Review whether they may stay in a public repository before the open-source release (Phase 13).

## Rule for new entries

Before copying any upstream asset, text or schema file, add a row above with its source, license position and destination, in the same change.
