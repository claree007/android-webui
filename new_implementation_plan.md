# Android Native Open WebUI–Style App: Implementation Plan

## 1. Instructions to the implementation agent

Build an independently implemented, native Android application using Open WebUI as a behavioral reference and the supplied screenshots as visual references.

Do not build a WebView wrapper. Do not require an Open WebUI backend.

Work in small, independently verifiable increments. After each phase, the application must remain buildable and previously completed features must continue working.

### Required product behavior

| Area | Requirement |
|---|---|
| Platform | Android only; Kotlin and Jetpack Compose |
| Identity | One local profile named **User**; no app registration or login |
| Models | External inference through user-configured endpoints and credentials |
| Interface | Open WebUI–inspired mobile interface with Android-native interactions |
| Themes | Light, dark, and follow-system |
| Storage | Chats, configuration, documents, and app state stored locally |
| Background generation | Continue during ordinary app switching and screen locking, subject to Android restrictions |
| Notifications | Quiet system notification during generation, with Stop action; in-app progress indicator |
| Web search | Direct search-engine integrations; extraction and orchestration on-device |
| Documents and RAG | Local extraction, OCR, indexing, embeddings, and retrieval |
| Speech | Prefer verified on-device processing; external alternatives require explicit configuration |
| Image generation | Connect to configured image-generation services |
| Code execution | On-device, sandboxed execution |
| Plugins | Tested compatibility subset of existing Open WebUI Python Tools and Functions |
| Distribution | Open-source project with Docker-based builds |
| Testing | Automated tests plus documented physical-device checks |

No external service may be silently enabled as a fallback.

External model providers can receive chat messages, selected document excerpts, images, search results, and tool outputs when those are included in requests. Explain this clearly in configuration and attachment flows.

### Scope boundaries

Exclude account registration, multi-user administration, organization management, and server-only operational features.

Do **not** exclude a useful single-user feature merely because Open WebUI places it in an admin screen. Move its equivalent into local settings or workspace management.

Keep on-device chat-model inference as a future extension point, not a dependency of this project.

---

## 1a. Recorded decisions (plan-review session)

These override any conflicting text elsewhere in this plan. Do not re-ask.

| # | Topic | Decision |
|---|---|---|
| D1 | Database schema | The on-device database is **schema-compatible with Open WebUI's `webui.db`** at the pinned Open WebUI web app SHA: same tables, columns, types, indexes, and `alembic_version` row. Multi-user tables are created too, but only **one local user** (`User`) exists. A `webui.db` from the Open WebUI web app is never opened or swapped in as the active database; its content is brought in by **merge import** (D2). Two content differences from the Open WebUI web app are intentional: (a) API keys are stored as `keystore:<id>` references (D5), and (b) connections may carry `provider_type` (D17). A DB exported from this app therefore needs its keys re-entered, and its non-OpenAI/Ollama connections are not usable, when opened in stock Open WebUI. Exports include plaintext keys only through the D5 opt-in toggle. The reference file is `../webui.db`; the schema DDL is copied from it (never ship the data) |
| D2 | Merge import / export | **Import is a merge, not a swap.** The user picks a foreign `webui.db`; the app validates its schema (reporting unsupported content instead of silently dropping it), reads it **read-only**, and merges its settings and data into the active DB: settings, connections (D5, D16), chats and folders, models and presets, prompts, notes, memories, knowledge, tools, functions, skills and the rest of the single-user data. The source file is never modified. **IDs are preserved.** **Conflicts:** if an imported row has the same ID as an existing row, the existing row wins and the collision is listed in the import report; JSON-valued settings and config are merged key by key without overwriting existing keys, and imported connections are appended. **Removable:** every merge is recorded as an **import batch** (`app_import_batch`, with `app_import_item` rows listing each table and row ID, or settings key, it added; both `app_`-prefixed, see Foundational data model). The user can **remove a batch** later, which deletes exactly the rows and keys that batch added and nothing else. Rows the user created themselves are never touched. Removal asks for confirmation and states that later edits to imported items are removed with them. Before a merge changes the active DB, the app takes a restorable backup of it. **Export** writes a copy of the active DB (D5 for secrets) |
| D3 | Message graph | Follow the Open WebUI web app's format exactly. `chat.chat` JSON is the **complete source of truth, except for the single in-flight message of an active generation job**, whose latest content is in its `chat_message` row (D18): `history.messages` is a map `id → {id, parentId, childrenIds, role, content, models, timestamp, ...}` and `history.currentId` is the active leaf. Edits and regenerations add sibling children and never delete. The `chat_message` table is a **derived, rebuildable index** (`parent_id`, `output`, `sources`, `usage`, `done`, ...) whose row `id` is the composite `{chat_id}-{message_id}`, used for search, statistics and cheap streaming updates. In the reference `webui.db` it is incomplete (1495 rows vs 1530 messages in the JSON), so it is never used to rebuild the JSON (the one exception is the in-flight message of an interrupted job, copied in by message ID only, see D18). Write rules are in D18 |
| D4 | Database layer | **Plain SQLite** (`SQLiteOpenHelper`/`SQLiteDatabase`) with hand-written repositories and row mappers. **No Room**, because Room validates the schema and would reject or alter a file from the Open WebUI web app. Schema checks are done by our own tests against the Open WebUI web app's DDL |
| D5 | Secrets | Connection rows keep the Open WebUI web app's `config` layout (`openai.api_keys`, `openai.api_base_urls`, `ollama.base_urls`, ...), but key values are stored in **Keystore-backed storage** and the DB holds only `keystore:<id>` references. Merging a DB with plaintext keys moves them into the Keystore and stores references in the merged connection entries. Exports exclude secrets by default; an explicit opt-in toggle may include them, writing plaintext keys back in the Open WebUI web app's layout (replacing the `keystore:<id>` references) so the export is usable in stock Open WebUI. Never log keys |
| D6 | Dependency injection | **No Hilt, no KSP.** Manual DI: one `AppContainer` created in the `Application` class, ViewModels built via factories, fakes injected through constructors in tests |
| D7 | Settings storage | Synced settings live in `user.settings` JSON using the Open WebUI web app's exact `ui.*` keys. Device-only state (theme: light/dark/system, onboarding flags) lives in `SharedPreferences`. **No DataStore**. Theme is not part of imports/exports |
| D8 | Dependency policy | Keep the app lightweight: prefer the Android framework and the Kotlin standard library over extra libraries. Every new dependency needs a one-line justification in `docs/architecture.md` |
| D9 | Networking | **OkHttp** is the single networking library (cancellable streaming, redirect/credential control, timeouts, `MockWebServer` tests). SSE and NDJSON parsing is hand-written; no extra streaming library |
| D10 | Menu and sidebar scope | **Remove:** Sign Out, "Update your status", presence dot/count, the "new version available" banner, Admin Panel as a menu item, Channels. **Keep:** Workspace, Playground, Settings, Notes, Memories (under Personalization), the sidebar Models list, Folders, and chats grouped by date. Admin-only options useful to one user (connections, web search, documents/RAG, audio, images, code execution, functions) move into Settings or Workspace. **Evaluations are excluded entirely** (Arena models, leaderboard, feedback screens); existing `feedback` rows and related data are preserved untouched so they round-trip. **Calendar and Automations** are not shown until they are actually built (Phase 12); the menu entries are added then. Both are tracked in `docs/feature-parity.csv` |
| D11 | Python / code execution / plugins | Phases 10 and 11 and the Phase 2B spikes (Python, Python isolation, document extraction, local embeddings, offline speech) **stay in scope as written**, but are scheduled **after the Phase 4–5 chat milestone** (basic chat plus durable background generation). The `tool` and `function` tables are part of the schema from Phase 1 (D1) |
| D12 | Project docs | `AGENTS.md` is ignored for this planning session. Where it conflicts with this plan (Hilt/KSP/Room/DataStore, Python deferral), this plan wins. `AGENTS.md` is updated later in a separate change |
| D13 | Auxiliary model requests | Match the Open WebUI web app's defaults: automatic **title generation, follow-up suggestions and tag generation are all on by default**. Each toggle uses the Open WebUI web app's `ui.*` settings key, and a separate task model can be chosen in Settings → Interface. Helper requests are visible in the message info panel and counted in usage. First-run and Connections screens state that these extra requests go to the configured provider and can be switched off. **Phasing:** title generation, the task-model setting and the notice arrive in Phase 4; follow-ups and tags arrive in Phase 6, which extends the notice to cover them |
| D14 | Attachments and files | Only the bare `webui.db` is supported for merge import and export. File bytes are **not** carried with it. Attachments whose files are absent show an explicit "file unavailable" state (never a crash or silent blank), and the `file` rows are preserved untouched so IDs still round-trip. Any on-device retrieval index is app-local and is not part of an import or export; it is rebuilt from stored document text when available, and missing sources are reported |
| D15 | Settings screens | Settings is a full-height sheet with a Back chip and horizontally scrolling tabs, as in the screenshots. **Dropped as web/server-only:** Display Chat Title in Tab, Toast Notifications for New Updates, What's New modal, Keyboard shortcuts tab. **Adapted:** UI Scale becomes in-app text size layered on the system font scale; Theme and Language follow D7 and the system locale; Haptic Feedback stays. **Kept with the Open WebUI web app's semantics:** Message Queue, Chat Direction, High Contrast, Copy Formatted Text. All kept settings use the Open WebUI web app's `ui.*` keys in `user.settings` |
| D16 | Connections | One Connections screen backed by the global `config` entries (D1, D5): OpenAI-compatible (`openai.api_base_urls[]`, `api_keys[]`, `api_configs{index → enable, tags, prefix_id, model_ids}`) and Ollama (same layout). Each connection supports enable, tags, prefix ID and a model allow-list. **No separate Direct Connections screen** (the browser CORS concept does not apply); existing `user.settings.directConnections` data is preserved untouched so it round-trips. The screen carries the D13 notice about auxiliary requests. The app is designed for **many providers** behind the `ChatProvider` interface; see D17 for how providers outside the Open WebUI web app are stored |
| D17 | Provider types | Each connection may carry an extra `"provider_type"` key inside its `api_configs{index}` object (for example `"gemini"`, `"anthropic"`). A missing key means `openai` in the OpenAI list and `ollama` in the Ollama list, so an unmodified DB from the Open WebUI web app merges as is (D2), and the web app ignores the unknown key. Each type has one `ChatProvider` adapter; adding a provider never changes the DB schema. Unknown `provider_type` values show as "Unsupported provider" and are preserved untouched |
| D18 | Chat write path | During streaming, update only the `chat_message` row of the message being generated (small writes). Write the full `chat.chat` JSON at checkpoints: reply completed, stopped or failed, a bounded interval (see Phase 5), and app pause. While a job runs, the JSON may lag the table for that one message; the table copy is the newer one. On startup and after every import merge, reconcile in this order: (1) read and set aside the in-flight `chat_message` row of every job in `app_generation_job` with a non-terminal state; (2) **rebuild `chat_message` from the JSON**; (3) re-apply the saved in-flight row. If the job is still running, the row simply stays the newer copy. If the job was interrupted (Phase 5), copy the saved content into the matching JSON message by message ID as the latest checkpoint and mark it **Interrupted**. Never rebuild the JSON from the table otherwise. Reference DB sizes: chat JSON averages about 106 KB, maximum about 2.9 MB, so the full blob must not be rewritten per token |
| D19 | Model list and custom models | Reproduce the Open WebUI web app's merge (`utils/models.py`): discovered models from enabled connections, plus `model` rows. A row with `base_model_id` is a preset wrapping its base model (system prompt, params, capabilities, tags, suggestion prompts); a row with no base model overrides the discovered model with the same ID (name, meta); `is_active = 0` hides it. The selector, sidebar Models list and Workspace → Models all use this merged list. **Access** is removed from the UI but existing access data is preserved. The Knowledge, Tools, Skills and Filters pickers appear only once those features exist (Skills: Phase 12); until then the form shows the IDs already stored on the model as read-only so saving loses nothing. **Arena models are excluded** (multi-user evaluation, see D10) |
| D20 | Behavior parity vs. code | Match the Open WebUI web app's **behavior and stored data format** (message `output`, `sources`, citations, status history, usage; `task.*` config keys) but write all code **independently**. Prompt templates for title, tags, follow-ups, search queries and RAG are **re-authored, not copied verbatim**, because the web app's code after commit `60d84a3` is under the Open WebUI License (branding clause) and this app is Apache-2.0 with no Open WebUI branding. Templates are stored as editable settings under the `task.*` keys. Phase 0 produces a short behavior spec per feature (derived by reading the Open WebUI web app, no code copied), and the implementation is tested against that spec. Before any Open WebUI web app asset, text or schema file is copied, record its provenance and license in `docs/provenance.md` |
| D21 | Development README | Every setup step, script and command is documented in **`DEVELOPMENT.md`** at the repo root (separate from any user-facing README), and kept in sync in the same change that adds or alters a script or command. It lists: prerequisites, one-time setup (image, Gradle download, wrapper), every script with purpose, arguments, environment variables, expected output and failure modes, how to build, test, lint, install, run connected tests, regenerate the schema asset, and how to troubleshoot the Docker/aarch64 environment. A change that adds a script without documenting it in `DEVELOPMENT.md` is not done (see Definition of done) |
| D22 | Markdown and text selection | Assistant output is **rendered as Markdown from the first chat release (Phase 4)**, including while streaming, not deferred to Phase 6. All message text, including code blocks and table cells, is **selectable with the standard Android selection handles and copy toolbar**. Code blocks also have a one-tap copy button. The Markdown parser is **written in-house in Kotlin** and rendered with Compose `Text` inside a selection container; no Markdown library is added (D8). It must handle incomplete, partially streamed input by design. Math, syntax highlighting and other advanced rendering come in Phase 6. Raw HTML in model output is never rendered, and links never open without the user tapping them |

---

## 2. Engineering constraints to resolve early

### Open WebUI web app behavior and licensing

Open WebUI has both built-in features and Python/HTTP extension mechanisms. Python plugins can depend on its backend environment, so compatibility must be established through tests rather than assumed. ([docs.openwebui.com](https://docs.openwebui.com/features/extensibility/))

Use independently written Android code by default (D20). Review the license of the pinned Open WebUI web app revision before copying code, assets, icons, or branding; the published license includes branding conditions. The app's own license is Apache-2.0 with no Open WebUI name or logo; record provenance of anything copied in `docs/provenance.md`. ([github.com](https://github.com/open-webui/open-webui/blob/main/LICENSE))

### Android background execution

Select a foreground-service type that legitimately matches the implemented work. Do not declare an unrelated service type to bypass restrictions. Android imposes service-specific requirements and time limits, including limits on background `dataSync` services. ([developer.android.com](https://developer.android.com/develop/background-work/services/fgs/service-types))

Background continuation is a tested best-effort requirement—not a guarantee against force-stop, process termination, connectivity loss, or manufacturer-specific restrictions.

### Python execution and isolation

Evaluate an embedded Python runtime such as Chaquopy, but verify its Android, ABI, Python-package, and build-tool compatibility before committing to it. Its supported configurations are version-specific. Per D11 this runs in Phase 2B. ([chaquo.com](https://chaquo.com/chaquopy/doc/current/versions.html))

A separate process is **not automatically a security sandbox**. Android isolated services have a separate security boundary and no permissions of their own; determine experimentally whether the chosen runtime can operate there. ([developer.android.com](https://developer.android.com/guide/topics/manifest/service-element))

Do not release arbitrary code execution if adequate isolation cannot be demonstrated.

---

## 3. Proposed architecture

### Stack

Use this as the starting architecture. Exact dependency versions are pinned during Phase 1 (the build is created there), and each dependency is justified in `docs/architecture.md` (D8).

| Concern | Proposed choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose with customized Material 3 components |
| State | ViewModels, coroutines, StateFlow, unidirectional state updates |
| Navigation | AndroidX navigation |
| Database | Plain SQLite (`SQLiteOpenHelper`), `webui.db` schema compatible with the Open WebUI web app (D1, D4) |
| Preferences | `user.settings` JSON in the DB for synced settings; `SharedPreferences` for device-only state (D7) |
| Networking | OkHttp with provider-specific protocol adapters (D9) |
| Serialization | Kotlin serialization |
| Dependency injection | Manual `AppContainer` (D6); no Hilt/KSP |
| Background generation | Foreground service owning generation jobs |
| Deferrable maintenance | WorkManager |
| Secrets | Keystore-backed encryption; no plaintext credentials in preferences |
| Files | App-private storage plus Android document picker |
| Testing | JVM tests, mock HTTP server, Compose tests, instrumentation tests |
| Build | Gradle wrapper inside a pinned Docker environment |

**Compatibility baseline:** Android 8.0 / API 26 minimum. Select the current stable compile/target SDK during implementation and document the decision.

Support `arm64-v8a` and `x86_64` initially. Investigate `armeabi-v7a` support without making it a blocker for the core app. Advanced runtime availability must not determine whether basic chat works.

Native libraries must be checked for 16 KB page-size compatibility. Android explicitly requires additional validation for apps containing native dependencies. ([developer.android.com](https://developer.android.com/guide/practices/page-sizes))

### Module boundaries

Create modules gradually—do not generate dozens of empty modules at the start.

```text
app/
core/
  model/
  database/
  network/
  security/
  designsystem/
  testing/
engine/
  conversation/
  providers/
  tools/
  documents/
  retrieval/
  speech/
  images/
  runtime/
  plugins/
feature/
  chat/
  history/
  connections/
  settings/
  workspace/
```

Rules:

- Feature UI must not call provider APIs directly.
- Domain logic must not depend on Compose.
- Advanced features must be accessed through interfaces.
- Every optional integration needs a fake implementation for tests.
- Missing optional runtimes must not prevent app startup.
- Docker is the **build environment** only, never an Android runtime dependency.

### Core contracts

Define small interfaces for:

```text
ChatProvider
ModelCatalog
GenerationCoordinator
ConversationRepository
AttachmentRepository
ToolExecutor
SearchProvider
DocumentExtractor
EmbeddingEngine
RetrievalIndex
SpeechInputEngine
SpeechOutputEngine
ImageGenerationProvider
CodeExecutionRuntime
PluginHost
SecretStore
CapabilityRegistry
```

Use explicit capability states:

```text
Available
NeedsConfiguration
NeedsDownload
UnsupportedDevice
UnsupportedProvider
DisabledByUser
UnavailableWithReason
```

A configured capability is not necessarily available; validate it.

### Foundational data model

Open WebUI web app `webui.db` tables (D1), used under their original names:

- Local profile (`user`, with `user.settings`).
- Provider connections (global `config`) and credential references (D5, D16, D17).
- Models and presets (`model`, D19).
- Conversations and folders (`chat`, `folder`).
- Messages with parent/branch relationships (`chat.chat` JSON plus the derived `chat_message` index, D3).
- Structured message content parts and tool results (the message `output` items).
- Attachments (`file`).
- Prompts, notes, memory entries, knowledge, tools, functions and skills (`prompt`, `note`, `memory`, `knowledge`, `tool`, `function`, `skill`).

App-only data lives in separate tables prefixed `app_` so that an Open WebUI web app instance ignores them:

- Generation jobs (`app_generation_job`, Phase 5).
- Import batches (`app_import_batch`, `app_import_item`; merge-import provenance used to remove a batch later, D2).
- Generated artifacts and tool approvals.
- Documents, chunks and embedding-model versions (the local retrieval index, D14).
- Plugin versions, permissions and compatibility results.

**Model conversations as a message graph from the beginning**, exactly as the Open WebUI web app does it (D3): `chat.chat` JSON holds the tree and `currentId`, and `chat_message` mirrors it. Editing and regeneration create branches instead of destroying historical messages. Use the Open WebUI web app's table and column names rather than inventing new ones.

---

## 4. Implementation phases

### Phase 0 — Pin the Open WebUI web app, inventory features, establish references

**Dependencies:** None.

#### Tasks

1. Inspect the supplied Open WebUI repository at the pinned SHA.
2. Inventory all single-user features from source, routes, settings, and documentation, into `docs/feature-parity.csv`.
3. Map each supplied screenshot to screens, components, and interaction states.
4. Record missing visual references without blocking implementation.
5. Write a short behavior spec per feature (D20), derived by reading the Open WebUI web app with no code copied.
6. Create `docs/architecture.md` (stack and the one-line justification for each dependency, D8) and `docs/provenance.md` (D20). `DEVELOPMENT.md` is created in Phase 1 together with the scripts it documents (D21).

#### Acceptance criteria and tests

- Every inventoried feature has a disposition.
- Every screenshot has a screen/state mapping.
- No feature is marked complete without corresponding tests.

---

### Phase 1 — Docker build and application foundation

**Dependencies:** Phase 0.

#### Tasks

- Create the Android project and initial CI workflow.
- Add a pinned Dockerfile, Gradle wrapper, dependency verification, and build scripts.
- Initialize local profile **User** automatically.
- Add application shell, theme switching, navigation, preferences (D7), and Keystore-backed secret storage (D5).
- Produce installable APK artifacts.
- Create the database from the exact Open WebUI web app `webui.db` schema (D1), using plain SQLite (D4). Add a test that compares our schema (tables, columns, types, indexes) with the pinned Open WebUI web app DDL, and a test that opens an unmodified DB shaped like the Open WebUI web app's read-only and reads its tables (the foundation for the Phase 12 merge import, D2).
- Write `DEVELOPMENT.md` (D21).
- Wire dependencies manually through `AppContainer` (D6). Remove Hilt, KSP, Room and DataStore from the existing skeleton and `gradle/libs.versions.toml`.

Required commands (minimum set; the full list lives in `DEVELOPMENT.md`, D21):

```bash
./scripts/build-debug.sh
./scripts/test-unit.sh
```

Commands run inside Docker. Connected tests reach a physical device through a documented host ADB setup.

#### Acceptance criteria and tests

- A clean checkout builds without Android Studio.
- Fresh install launches without login.
- Themes survive restart.
- Unit tests in CI.

---

### Phase 2 — Feasibility gates

**Dependencies:** Phase 1. Per D11, 2A runs early; 2B runs after the Phase 4–5 chat milestone.

#### Tasks

Build minimal prototypes for:

**2A — early (alongside Phase 3):**

1. Streaming HTTP generation continuing while the app is backgrounded.
2. Native-library ABI and page-size compatibility.

**2B — after Phase 5 (D11):**

3. Embedded Python execution.
4. Python execution inside a suitable isolation boundary.
5. Document extraction.
6. Local embedding inference.
7. Offline speech capability.

For each prototype record:

- Tested device/runtime.
- Measured memory and execution time.
- Dependencies and licenses.
- Failure conditions.
- Supported configurations.
- Recommended production approach.

#### Acceptance criteria and tests

- 2A: Background stream survives a controlled screen-lock/app-switch test, and Stop cancels the local request.
- 2A: Native libraries pass the ABI and 16 KB page-size checks.
- 2B: Python prototype runs a simple fixture.
- 2B: Security tests distinguish ordinary separate-process execution from actual isolation.

**Never replace a failed local-processing requirement with a remote service without approval.**

---

### Phase 3 — Native visual shell

**Dependencies:** Phase 1; screenshot mapping from Phase 0.

#### Tasks

- Empty/new-chat screen.
- Chat transcript.
- Composer and attachment controls.
- Model selector.
- History drawer or native navigation equivalent.
- Search and conversation actions.
- Settings and connection screens.
- Workspace navigation.
- Loading, error, empty, and unsupported states.

Derive reusable typography, spacing, color, shape, and icon rules from screenshots. Preserve the intended appearance while adapting touch behavior, keyboard handling, navigation, and accessibility to Android.

#### Acceptance criteria and tests

- All major screens navigate correctly.
- Light/dark screenshot tests exist.
- Screen rotation preserves drafts and navigation state.
- Large text, TalkBack, keyboard insets, and landscape layouts are tested.
- No visible control silently does nothing.

---

### Phase 4 — Connections and basic persistent chat

**Dependencies:** Phases 1 and 3.

#### Tasks

Implement:

- OpenAI-compatible endpoint adapter first.
- Ollama adapter second.
- Additional provider adapters (native Gemini, Anthropic, and others) afterward, selected through `provider_type` (D17).
- Connection testing, model discovery, manual model identifiers, and capability overrides.
- Streaming responses.
- Markdown rendering while streaming, and text selection and copy, as specified in D22. Supported elements: headings, emphasis, lists, block quotes, links, inline code, fenced code blocks (copy button, horizontal scroll) and tables.
- Persistent conversations and drafts, using the write path in D18 from the start.
- Model parameters and system prompts.
- Automatic title generation, the task-model setting and the first-run/Connections notice, as phased in D13.
- Authentication, quota, connection, timeout, and malformed-response errors.

Use normalized events for text, tool calls, artifacts, usage, completion, and failure. Preserve provider-specific extension data where necessary.

#### Acceptance criteria and tests

- A user can configure an endpoint and complete a real conversation.
- Existing chats remain readable offline.
- Split stream frames, Unicode boundaries, unexpected EOF, and malformed events are tested.
- Markdown golden tests cover every element listed above, plus partially streamed (unclosed) fences, emphasis and tables, which must not flicker or crash.
- Compose UI tests verify that a word, a line, and a range spanning several paragraphs of one message can be selected and copied, and that selection survives the streaming updates of other messages.
- Unsupported provider options are omitted or explained.
- Requests are never accidentally duplicated during recomposition or rotation.
- Keys are not forwarded across unapproved origins or redirects.
- Private-network HTTP requires an explicit security warning; never implement “trust all certificates.”

**Checkpoint:** First useful native chat (foreground only). The **Usable chat milestone** is reached when Phase 5 is also complete.

---

### Phase 5 — Durable generation and background continuation

**Dependencies:** Phase 4 and the background feasibility gate.

#### Tasks

Move generation ownership out of the screen/ViewModel lifecycle.

Implement a durable job state machine:

```text
Queued → Connecting → Streaming ↔ AwaitingTool
                                  ↓
                Completed / Cancelled / Failed / Interrupted
```

Requirements:

- Foreground service owns active generation.
- UI observes persisted state.
- Quiet notification includes a safe Stop action.
- Persist progress per D18: update the in-flight `chat_message` row in small throttled writes (about once per second), and write the full `chat.chat` JSON when the reply completes, stops or fails, when the app pauses, and at a bounded interval (default 30 s, tunable; skipped if nothing changed). Job state lives in `app_generation_job`.
- Add explicit concurrency limits.
- Reconcile unfinished jobs after process restart.
- Do not automatically replay potentially billable requests.
- Do not claim stopping the local stream cancels remote computation unless the provider supports it.

#### Acceptance criteria and tests

- A controlled three-minute response continues through app switching and screen locking.
- Reopening the chat displays the same ongoing job.
- Stop from UI and notification yields consistent results.
- Force-stop recovery shows **Interrupted**, retaining the latest checkpoint.
- Connection loss does not falsely produce **Completed**.
- Exercise timeout, notification-permission denial, and foreground-service failure paths.
- Document actual physical-device results and limitations.

---

### Phase 6 — Complete conversation behavior and organization

**Dependencies:** Phases 4–5.

#### Tasks

- Edit messages and create branches.
- Regenerate responses and navigate alternatives.
- Copy, share, rename, archive, delete, and bulk actions.
- History search, folders, tags, and pins.
- Advanced rendering on top of the Phase 4 Markdown baseline (D22): math, syntax highlighting, task lists, and footnotes.
- Image display and attachment previews.
- Saved prompts, prompt variables, and composer shortcuts. (Model presets are managed in Phase 12 under Workspace → Models, D19; selecting one in chat works from Phase 4.)
- Multi-model responses/comparisons where present in the Open WebUI web app.
- Message queues and temporary chats.
- Automatic follow-up and tag generation and the extended notice, as phased in D13.

Do not execute code or load arbitrary active content merely because it appears in a response.

#### Acceptance criteria and tests

- Branch switching reconstructs the correct provider context.
- Regeneration preserves earlier alternatives.
- Long conversations remain responsive.
- Malformed Markdown and unsafe links do not crash or execute content.
- Automatic auxiliary model requests (title, follow-up, tag) are visible in the message info panel, counted in usage, and configurable; the notice covers all three.
- Delete and export behavior is correct for shared attachments and branches.

---

### Phase 7 — Tool orchestration and web search

**Dependencies:** Phases 4–5. Can run alongside Phase 6.

#### Tasks

Build the shared tool layer:

- Typed tool schemas and validation.
- Invocation history and progress.
- Timeout, cancellation, and maximum-step limits.
- Approval requests for sensitive or externally consequential actions.
- Structured errors and results.
- Per-provider function-calling adapters.

Then implement:

- Search-provider adapters.
- Direct page fetching and local text extraction.
- Source references and citation navigation.
- URL attachment/import.
- Explicit search credentials where required.

Protect against requests to unexpected local services, credential-bearing URLs, redirect abuse, oversized responses, and unbounded downloads. Intentional local endpoints must be separately configured and authorized.

#### Acceptance criteria and tests

- Search → fetch → extract → answer works against fixtures and a configured real service.
- Citations resolve to stored source metadata.
- Retrieved content cannot grant permissions or override the app’s trust policy.
- Tool loops terminate at configured limits.
- Cancellation stops pending search/fetch work.
- Duplicate tool events cannot silently repeat consequential operations.

---

### Phase 8 — Local documents, OCR, and knowledge/RAG

**Dependencies:** Phase 4; tool integration uses Phase 7; the Phase 2B document-extraction and local-embedding spikes.

#### Tasks

Implement incrementally:

1. Plain text, Markdown, and CSV.
2. PDF text extraction.
3. DOCX and other inventoried formats.
4. OCR for image/scanned documents.
5. Chunking with source/page metadata.
6. Local embedding generation.
7. Local retrieval index.
8. Knowledge collections and chat attachment retrieval.
9. Reindexing, progress, cancellation, and deletion.

Use capability-gated engine adapters. Track embedding model/version and extraction settings; never mix incompatible vectors.

Local document processing must work without a cloud parser. Explain that selected excerpts can subsequently be sent to the configured external chat model.

#### Acceptance criteria and tests

- Extraction and indexing operate offline after required models are downloaded.
- Golden files verify text, page references, chunking, and retrieval.
- Test encrypted PDFs, corrupt files, large files, duplicate documents, and low storage.
- Model changes trigger controlled reindexing.
- Deleting a document removes its associated index data.
- Cancellation leaves a recoverable state.
- Missing local embeddings are reported honestly; lexical search may be offered as a clearly labeled fallback.

---

### Phase 9 — Speech and image integrations

**Dependencies:** Phase 4; reuse Phase 5 job infrastructure where appropriate; the Phase 2B offline-speech spike for local speech (remote speech and image work do not wait for it).

#### Speech tasks

- Push-to-talk dictation.
- Read-aloud, pause, resume, and stop.
- Language and voice selection.
- Verified local recognition and synthesis.
- Explicitly configured remote alternatives.
- Voice conversation/realtime workflows where inventoried.

Android exposes dedicated on-device recognition APIs and availability checks; do not label ordinary recognition offline without verification. ([developer.android.com](https://developer.android.com/reference/android/speech/SpeechRecognizer.html))

#### Image tasks

- Configurable image-service adapters prioritized by the pinned Open WebUI web app inventory.
- Generation, editing, and variation workflows where supported.
- Progress, cancellation, generated-image history, and saving/sharing.
- Adapter-specific configuration without pretending all engines support identical parameters.

#### Acceptance criteria and tests

- Local speech is tested with networking disabled.
- No audio leaves the device without the selected configuration permitting it.
- Microphone use stops visibly and reliably.
- Text chat works when speech is unavailable.
- Image jobs survive UI recreation and preserve completed artifacts.
- Mock tests cover incompatible parameters, service errors, and partial failures.
- Real integration tests require explicitly supplied credentials/endpoints.

---

### Phase 10 — Sandboxed local code execution

**Dependencies:** Phase 2B security gate and Phase 7.

#### Tasks

- Implement the selected local runtime.
- Start with a documented Python subset.
- Provide per-run temporary files and explicit artifact export.
- Capture bounded stdout, stderr, images, and structured results.
- Enforce time, output, file-size, and resource limits where technically supported.
- Deny network and app-private storage access by default.
- Require approval before execution.
- Terminate unresponsive execution without terminating the app.

Open WebUI includes browser and remote execution approaches; this Android runtime is an intentional equivalent, not a claim to reproduce a full remote Docker environment. ([docs.openwebui.com](https://docs.openwebui.com/features/chat-conversations/chat-features/code-execution/))

#### Acceptance criteria and tests

- Execute arithmetic, text processing, and artifact-generation fixtures.
- Infinite loops can be terminated.
- Attempts to read app secrets, databases, or unrelated files fail.
- Network access fails by default.
- Runtime crashes leave the chat app usable.
- Resource-exhaustion tests are documented.
- Unsupported packages and languages are identified before execution where possible.

**Release gate:** no arbitrary-code feature ships under a “sandboxed” label without evidence supporting that claim.

---

### Phase 11 — Open WebUI plugin compatibility

**Dependencies:** Phases 7 and 10; build on the Phase 2B prototype.

#### Compatibility strategy

Build a versioned compatibility layer around the pinned Open WebUI web app API surface.

Prioritize:

1. Tool class discovery and invocation.
2. Metadata and configuration.
3. `Valves` and `UserValves`.
4. Sync and async functions.
5. Supported injected arguments and event callbacks.
6. Filters.
7. Pipes and streaming.
8. Actions.
9. Other hook types discovered in the pinned revision.

The Open WebUI web app documents Tools and multiple Function types; discover the precise API from the selected source revision instead of assuming a permanent fixed set. ([docs.openwebui.com](https://docs.openwebui.com/features/extensibility/plugin/))

#### Tasks

- Import plugins as inert files.
- Show source, origin, hash, version, dependencies, and requested capabilities.
- Perform static checks without importing/executing the plugin.
- Require explicit trust approval before executable inspection.
- Use sandboxed introspection afterward where needed.
- Provide a minimal local `User` context.
- Broker authorized network/filesystem access.
- Version configuration and invalidate approvals after relevant changes.
- Maintain a package support allowlist.
- Do not run arbitrary `pip install` commands on import.

Network-dependent plugins require explicit per-plugin approval; reject them if permissions cannot be enforced.

#### Acceptance criteria and tests

Maintain:

```text
tests/plugin-fixtures/
docs/plugin-compatibility.md
```

Classify plugins:

- Runs unchanged.
- Runs with documented adapter.
- Unsupported.
- Not tested.

Required fixtures include:

- Synchronous and asynchronous tools.
- Valve configuration.
- Progress/status events.
- Filter hooks.
- Streaming pipe.
- Action handler.
- Unsupported dependency.
- Backend-internal import.
- Attempted secret access.
- Infinite loop and crash.

Include representative real community plugins where licensing permits. **Self-authored fixtures alone are insufficient evidence of ecosystem compatibility.**

---

### Phase 12 — Workspace and remaining single-user parity

**Dependencies:** Relevant engines only; split into independently implementable tasks.

#### Tasks

Use the parity inventory to finish:

- Notes and personal knowledge workflows.
- Explicitly managed memory.
- Workspace → Models (merged model list, presets and overrides, D19) and the rest of workspace configuration.
- Workspace → Skills (the `skill` table exists in the pinned Open WebUI web app schema). Phase 0 documents what a skill is and how it is applied, in `docs/feature-parity.csv` and its behavior spec (D20). Until built, the model form shows stored skill IDs read-only (D19).
- Playground.
- Calendar (menu entry added only once built, D10).
- Merge import and export of settings, chats and other data (D2), including removing an import batch.
- Backup and restore (the pre-merge backup and manual backups, D2).
- Usage reporting where applicable.
- External tool protocols, if present in the Open WebUI web app.
- Automations and scheduling (menu entry added only once built, D10).
- Advanced voice, task, artifact, and workspace features.
- Any other discovered single-user functionality.

Current Open WebUI documentation includes areas such as memory, message queues, automations, and task management, so do not reduce parity to basic chat, search, and attachments. ([docs.openwebui.com](https://docs.openwebui.com/features/))

#### Acceptance criteria and tests

- Every feature has its own acceptance tests.
- Backup/restore round-trips supported data.
- Merge import preserves IDs, branches, attachment records, and settings where compatible, and reports unsupported fields instead of losing them silently. File bytes are not carried (D14); missing files show "file unavailable".
- ID collisions keep the existing row and appear in the import report; the source `webui.db` is never modified.
- Removing an import batch deletes exactly the rows and keys it added and leaves user-created data untouched.
- A pre-merge backup exists and can be restored.
- Secrets are excluded by default.
- Scheduled work explains timing limitations and external-model usage.
- No applicable Open WebUI web app feature disappears from tracking.

---

### Phase 13 — Hardening and open-source release

**Dependencies:** Completed features selected for the release.

#### Tasks

- Database migration testing.
- Security review of plugins, rendering, imports, tools, and networking.
- Accessibility and localization readiness.
- Performance and battery profiling.
- Low-memory and low-storage behavior.
- Physical-device background tests.
- ABI and native-library validation.
- Dependency/license inventory and software bill of materials.
- Contributor guide, final review of `docs/architecture.md` and `DEVELOPMENT.md`, privacy documentation, and release process.
- Signed release artifacts without placing signing keys in the container image or repository.

#### Acceptance criteria and tests

Publish a device matrix covering:

- Minimum supported Android version.
- Intermediate versions.
- Current stable Android at release.
- Low-memory configuration.
- Physical device.
- Tablet/landscape.
- Available 16 KB test environment.
- Supported ABIs.

Clearly distinguish automated, emulator, physical-device, and not-run results.

Release notes must include:

- Supported features.
- Partial implementations.
- Known device limitations.
- Tested plugin compatibility.
- Required external services.
- Remaining parity gaps.

Do not call the release “full parity” until all applicable inventory items are implemented or explicitly agreed as exceptions.

---

## 5. Dependency order and parallel work

```text
Phase 0: Source inventory
    ↓
Phase 1: Build and foundation
    ├── Phase 2A: Background-stream and ABI/page-size gates
    └── Phase 3: Native UI
             ↓
Phase 4: Basic chat
    ├── Phase 5: Durable/background generation
    │       ├── Phase 6: Conversation features
    │       └── Phase 7: Tools and search
    │               ├── Phase 10: Local code runtime ← Phase 2B (Python, isolation)
    │               │        ↓
    │               └──→ Phase 11: Plugins (needs Phases 7 and 10)
    ├── Phase 8: Documents/RAG ← Phase 2B (extraction, embeddings)
    │            (tool/RAG integration step needs Phase 7)
    └── Phase 9: Speech and images ← Phase 2B (offline speech, local speech only)
                 (reuses Phase 5 job infrastructure where appropriate)

Phase 12: Remaining parity, implemented incrementally
Phase 13: Hardening and release
```

Arrows show the hard dependencies listed in each phase header. Nesting does not imply that a phase must wait for its parent's completion unless stated.

Document extraction and local indexing can begin before their tool integration is ready. Speech, image services, and conversation organization should not wait for Python compatibility.

**Suggested milestones**

1. **Foundation:** Docker build, native screens, local profile.
2. **Usable chat:** Connections, streaming, persistence, background generation.
3. **Daily-use app:** Branches, organization, search, documents, speech, images.
4. **Extensible app:** Sandboxed execution and tested plugin compatibility.
5. **Parity release:** Remaining inventory closed and release gates passed.

---

## 6. Mandatory rules for each agent task

For every task:

1. Read relevant architecture decisions and Open WebUI web app references.
2. State the intended changes and affected contracts.
3. Implement one bounded vertical slice.
4. Add tests for success, failure, cancellation, and persistence where applicable.
5. Run the applicable Docker checks.
6. Update parity status and documentation, including `DEVELOPMENT.md` for any script, command or setup change (D21).
7. Report actual commands and results.
8. Leave the repository buildable.

Use this task template:

```text
Task ID:
Goal:
Open WebUI web app reference:
Dependencies:
In scope:
Out of scope:
Interfaces affected:
Implementation steps:
Acceptance criteria:
Automated tests:
Physical-device checks:
Security/privacy considerations:
Documentation updates:
Known limitations:
```

Every completion report must say:

```text
Implemented:
Tests run:
Tests passed/failed:
Tests not run and why:
Manual verification:
Remaining gaps:
Next unblocked tasks:
```

### Definition of done

A feature is not complete merely because its screen exists.

It is complete only when:

- The real implementation is connected.
- Loading, error, empty, unavailable, and cancellation states work.
- Data survives the relevant lifecycle events.
- Permissions and privacy behavior are explicit.
- Automated tests pass.
- Required device checks are recorded.
- Its parity entry is updated.
- Any new or changed script, command, environment variable or setup step is documented in `DEVELOPMENT.md` (D21).
- There are no hidden stubs or silent fallbacks.

**Start with Phases 0–1, run the Phase 2A experiments early, and deliver the basic chat milestone (Phases 4–5) before the Phase 2B Python experiments and the plugin work (D11).** This preserves a usable app even if advanced compatibility work takes longer than expected.