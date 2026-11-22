# Architecture

Product scope and decisions (D1–D22) live in [`../new_implementation_plan.md`](../new_implementation_plan.md). This file records what the code currently contains.

## Phase 0 documents

| File | Purpose |
|---|---|
| `feature-parity.csv` | Inventory of single-user features with disposition, phase and status |
| `screenshot-map.md` | Mapping of each reference screenshot to a screen and state, plus missing references |
| `behavior-specs.md` | Behavior specs derived from the Open WebUI web app (no code copied) |
| `provenance.md` | Pinned upstream revision, license position and a log of anything taken |

## Layout

| Path | Purpose |
|---|---|
| `AppContainer` | Manual dependency wiring, created in `AndroidWebuiApp` (D6) |
| `core/database` | Plain SQLite on a `webui.db` schema compatible with the Open WebUI web app (D1, D4) |
| `core/prefs` | Device-only preferences in `SharedPreferences` (D7) |
| `core/security` | Keystore-backed `SecretStore`; the DB stores only `keystore:<id>` references (D5) |
| `ui` | Compose theme and navigation |

The schema is generated from the Open WebUI web app's reference database by `./scripts/dev schema` into `app/src/main/assets/webui_schema.sql` (DDL only, no data) and `app/src/test/resources/open-webui-schema-fingerprint.txt`. `SchemaTest` compares the database created by the app with that fingerprint.

## Dependencies (D8)

Each dependency needs a one-line justification here before it is added.

| Dependency | Why |
|---|---|
| `androidx.activity:activity-compose` | Hosts Compose in the activity and provides edge-to-edge support |
| `androidx.compose.ui:ui`, `material3` (BOM) | UI toolkit and Material 3 components |
| `androidx.navigation:navigation-compose` | Screen navigation (plan stack) |
| `kotlinx-coroutines-android` | Coroutines and `StateFlow` for state and background work |
| `junit` (test) | JVM unit tests |
| `org.xerial:sqlite-jdbc` (test) | Runs the SQLite schema and repository tests on the JVM; not shipped in the app |

Excluded by the plan: Hilt, KSP, Room, DataStore (D4, D6, D7).
