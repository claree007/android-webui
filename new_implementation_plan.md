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

## 2. Engineering constraints to resolve early

### Upstream behavior and licensing

Open WebUI has both built-in features and Python/HTTP extension mechanisms. Python plugins can depend on its backend environment, so compatibility must be established through tests rather than assumed. ([docs.openwebui.com](https://docs.openwebui.com/features/extensibility/?utm_source=openai))

Use independently written Android code by default. Review the license of the pinned upstream revision before copying code, assets, icons, or branding; the published license includes branding conditions. Document provenance and obtain owner approval for the application’s release license and branding. ([github.com](https://github.com/open-webui/open-webui/blob/main/LICENSE?utm_source=openai))

### Android background execution

Select a foreground-service type that legitimately matches the implemented work. Do not declare an unrelated service type to bypass restrictions. Android imposes service-specific requirements and time limits, including limits on background `dataSync` services. ([developer.android.com](https://developer.android.com/develop/background-work/services/fgs/service-types))

Background continuation is a tested best-effort requirement—not a guarantee against force-stop, process termination, connectivity loss, or manufacturer-specific restrictions.

### Python execution and isolation

Evaluate an embedded Python runtime such as Chaquopy, but verify its Android, ABI, Python-package, and build-tool compatibility before committing to it. Its supported configurations are version-specific. ([chaquo.com](https://chaquo.com/chaquopy/doc/current/versions.html?utm_source=openai))

A separate process is **not automatically a security sandbox**. Android isolated services have a separate security boundary and no permissions of their own; determine experimentally whether the chosen runtime can operate there. ([developer.android.com](https://developer.android.com/guide/topics/manifest/service-element?utm_source=openai))

Do not release arbitrary code execution if adequate isolation cannot be demonstrated.

---

## 3. Proposed architecture

### Stack

Use this as the starting architecture, with exact dependency versions pinned during Phase 0.

| Concern | Proposed choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose with customized Material 3 components |
| State | ViewModels, coroutines, StateFlow, unidirectional state updates |
| Navigation | AndroidX navigation |
| Database | Room |
| Preferences | DataStore |
| Networking | OkHttp with provider-specific protocol adapters |
| Serialization | Kotlin serialization |
| Dependency injection | Hilt |
| Background generation | Foreground service owning generation jobs |
| Deferrable maintenance | WorkManager |
| Secrets | Keystore-backed encryption; no plaintext credentials in preferences |
| Files | App-private storage plus Android document picker |
| Testing | JVM tests, mock HTTP server, Compose tests, instrumentation tests |
| Build | Gradle wrapper inside a pinned Docker environment |

**Compatibility baseline:** Android 8.0 / API 26 minimum. Select the current stable compile/target SDK during implementation and document the decision.

Support `arm64-v8a` and `x86_64` initially. Investigate `armeabi-v7a` support without making it a blocker for the core app. Advanced runtime availability must not determine whether basic chat works.

Native libraries must be checked for 16 KB page-size compatibility. Android explicitly requires additional validation for apps containing native dependencies. ([developer.android.com](https://developer.android.com/guide/practices/page-sizes?authuser=111&utm_source=openai))

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
- Docker is the **build environment**, not an Android runtime dependency.

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

Include:

- Local profile.
- Provider connection and credential references.
- Model identifiers, capabilities, and presets.
- Conversations and folders.
- Messages with parent/branch relationships.
- Structured message content parts.
- Generation jobs.
- Attachments and generated artifacts.
- Tool invocations and approvals.
- Documents, chunks, and embedding-model versions.
- Prompts, notes, and memory entries.
- Plugin versions, configuration, permissions, and compatibility results.

**Model conversations as a message graph from the beginning.** Editing and regeneration should create branches instead of destroying historical messages.

---

# 4. Implementation phases

## Phase 0 — Pin upstream, inventory features, establish references

**Dependencies:** None.

### Tasks

1. Inspect the supplied Open WebUI repository.
2. Pin its exact commit SHA; record tag/version if available.
3. Inventory all single-user features from source, routes, settings, and documentation.
4. Map each supplied screenshot to screens, components, and interaction states.
5. Record missing visual references without blocking implementation.
6. Review dependency licenses, upstream reuse, and branding.
7. Select and pin the initial Android toolchain.

Create:

```text
docs/upstream-baseline.md
docs/feature-parity.csv
docs/screen-inventory.md
docs/architecture.md
docs/security-model.md
docs/licensing-and-provenance.md
docs/decisions/
```

Each feature-parity row must contain:

```text
Feature ID
Upstream behavior
Source path/symbol at pinned commit
Android equivalent
Dependencies
Implementation phase
Acceptance-test IDs
Status
Known gaps
```

Statuses: `planned`, `implemented`, `partial`, `blocked`, `excluded-with-reason`.

### Acceptance criteria and tests

- Every inventoried feature has a disposition.
- Every screenshot has a screen/state mapping.
- No feature is marked complete without corresponding tests.
- The source baseline is a fixed commit, not a moving branch.
- Exclusions beyond the agreed scope are flagged for owner approval.

---

## Phase 1 — Docker build and application foundation

**Dependencies:** Phase 0.

### Tasks

- Create the Android project and initial CI workflow.
- Add a pinned Dockerfile, Gradle wrapper, dependency verification, and build scripts.
- Initialize local profile **User** automatically.
- Add application shell, theme switching, navigation, database, preferences, and secret storage.
- Add debug-only fake providers and sample data.
- Produce installable APK artifacts.

Required commands:

```bash
./scripts/build-debug.sh
./scripts/test-unit.sh
./scripts/lint.sh
./scripts/test-connected.sh
```

The first three run inside Docker. Connected tests may communicate with a physical device through a documented host ADB setup.

### Acceptance criteria and tests

- A clean checkout builds without Android Studio.
- Fresh install launches without login.
- Themes survive restart.
- Secrets never appear in logs, source control, or ordinary exports.
- Unit tests and lint run in CI.
- Missing device infrastructure produces a clear skipped/not-run result—not a false pass.

---

## Phase 2 — Early feasibility gates

**Dependencies:** Phase 1. These experiments must not block ordinary UI development.

### Tasks

Build minimal prototypes for:

1. Streaming HTTP generation continuing while the app is backgrounded.
2. Foreground-service notification and cancellation.
3. Embedded Python execution.
4. Python execution inside a suitable isolation boundary.
5. Local document extraction.
6. Local embedding inference on supported devices.
7. Offline speech capability detection.
8. Native-library ABI and page-size compatibility.

For each prototype record:

- Tested device/runtime.
- Measured memory and execution time.
- Dependencies and licenses.
- Failure conditions.
- Supported configurations.
- Recommended production approach.

### Acceptance criteria and tests

- Background stream survives a controlled screen-lock/app-switch test.
- Stop cancels the local request and saves partial output.
- Python prototype runs a simple fixture.
- Security tests distinguish ordinary separate-process execution from actual isolation.
- Any failed feasibility gate produces an explicit blocker and alternative proposal.

**Never replace a failed local-processing requirement with a remote service without approval.**

---

## Phase 3 — Native visual shell

**Dependencies:** Phase 1; screenshot mapping from Phase 0.

### Tasks

Implement with fake repositories:

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

### Acceptance criteria and tests

- All major screens navigate correctly using fake data.
- Light/dark screenshot tests exist.
- Screen rotation preserves drafts and navigation state.
- Large text, TalkBack, keyboard insets, and landscape layouts are tested.
- Tablet layout remains usable.
- No visible control silently does nothing.

---

## Phase 4 — Connections and basic persistent chat

**Dependencies:** Phases 1 and 3.

### Tasks

Implement:

- OpenAI-compatible endpoint adapter first.
- Ollama adapter second.
- Additional provider adapters identified by the parity inventory afterward.
- Connection testing, model discovery, manual model identifiers, and capability overrides.
- Streaming and non-streaming responses.
- Persistent conversations and drafts.
- Basic model parameters and system prompts.
- Authentication, quota, connection, timeout, and malformed-response errors.

Use normalized events for text, tool calls, artifacts, usage, completion, and failure. Preserve provider-specific extension data where necessary.

### Acceptance criteria and tests

- A user can configure an endpoint and complete a real conversation.
- Existing chats remain readable offline.
- Split stream frames, Unicode boundaries, unexpected EOF, and malformed events are tested.
- Unsupported provider options are omitted or explained.
- Requests are never accidentally duplicated during recomposition or rotation.
- Keys are not forwarded across unapproved origins or redirects.
- Private-network HTTP requires an explicit security warning; never implement “trust all certificates.”

**Milestone:** First useful native chat release.

---

## Phase 5 — Durable generation and background continuation

**Dependencies:** Phase 4 and the background feasibility gate.

### Tasks

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
- Persist partial content at bounded intervals and terminal transitions.
- Add explicit concurrency limits.
- Reconcile unfinished jobs after process restart.
- Do not automatically replay potentially billable requests.
- Do not claim stopping the local stream cancels remote computation unless the provider supports it.

### Acceptance criteria and tests

- A controlled ten-minute response continues through app switching and screen locking.
- Reopening the chat displays the same ongoing job.
- Stop from UI and notification yields consistent results.
- Force-stop recovery shows **Interrupted**, retaining the latest checkpoint.
- Connection loss does not falsely produce **Completed**.
- Exercise timeout, notification-permission denial, and foreground-service failure paths.
- Document actual physical-device results and limitations.

---

## Phase 6 — Complete conversation behavior and organization

**Dependencies:** Phases 4–5.

### Tasks

- Edit messages and create branches.
- Regenerate responses and navigate alternatives.
- Copy, share, rename, archive, delete, and bulk actions.
- History search, folders, tags, and pins.
- Rich Markdown, code blocks, tables, links, and math.
- Image display and attachment previews.
- Model presets, saved prompts, prompt variables, and composer shortcuts.
- Multi-model responses/comparisons where present upstream.
- Message queues, temporary chats, and automatic title generation where inventoried.

Do not execute code or load arbitrary active content merely because it appears in a response.

### Acceptance criteria and tests

- Branch switching reconstructs the correct provider context.
- Regeneration preserves earlier alternatives.
- Long conversations remain responsive.
- Malformed Markdown and unsafe links do not crash or execute content.
- Automatic auxiliary model requests are visible/configurable.
- Delete and export behavior is correct for shared attachments and branches.

---

## Phase 7 — Tool orchestration and web search

**Dependencies:** Phases 4–5. Can run alongside Phase 6.

### Tasks

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

### Acceptance criteria and tests

- Search → fetch → extract → answer works against fixtures and a configured real service.
- Citations resolve to stored source metadata.
- Retrieved content cannot grant permissions or override the app’s trust policy.
- Tool loops terminate at configured limits.
- Cancellation stops pending search/fetch work.
- Duplicate tool events cannot silently repeat consequential operations.

---

## Phase 8 — Local documents, OCR, and knowledge/RAG

**Dependencies:** Phase 4; tool integration uses Phase 7.

### Tasks

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

### Acceptance criteria and tests

- Extraction and indexing operate offline after required models are downloaded.
- Golden files verify text, page references, chunking, and retrieval.
- Test encrypted PDFs, corrupt files, large files, duplicate documents, and low storage.
- Model changes trigger controlled reindexing.
- Deleting a document removes its associated index data.
- Cancellation leaves a recoverable state.
- Missing local embeddings are reported honestly; lexical search may be offered as a clearly labeled fallback.

---

## Phase 9 — Speech and image integrations

**Dependencies:** Phase 4; reuse Phase 5 job infrastructure where appropriate.

### Speech tasks

- Push-to-talk dictation.
- Read-aloud, pause, resume, and stop.
- Language and voice selection.
- Verified local recognition and synthesis.
- Explicitly configured remote alternatives.
- Voice conversation/realtime workflows where inventoried.

Android exposes dedicated on-device recognition APIs and availability checks; do not label ordinary recognition offline without verification. ([developer.android.com](https://developer.android.com/reference/android/speech/SpeechRecognizer.html?utm_source=openai))

### Image tasks

- Configurable image-service adapters prioritized by the pinned upstream inventory.
- Generation, editing, and variation workflows where supported.
- Progress, cancellation, generated-image history, and saving/sharing.
- Adapter-specific configuration without pretending all engines support identical parameters.

### Acceptance criteria and tests

- Local speech is tested with networking disabled.
- No audio leaves the device without the selected configuration permitting it.
- Microphone use stops visibly and reliably.
- Text chat works when speech is unavailable.
- Image jobs survive UI recreation and preserve completed artifacts.
- Mock tests cover incompatible parameters, service errors, and partial failures.
- Real integration tests require explicitly supplied credentials/endpoints.

---

## Phase 10 — Sandboxed local code execution

**Dependencies:** Phase 2 security gate and Phase 7.

### Tasks

- Implement the selected local runtime.
- Start with a documented Python subset.
- Provide per-run temporary files and explicit artifact export.
- Capture bounded stdout, stderr, images, and structured results.
- Enforce time, output, file-size, and resource limits where technically supported.
- Deny network and app-private storage access by default.
- Require approval before execution.
- Terminate unresponsive execution without terminating the app.

Open WebUI includes browser and remote execution approaches; this Android runtime is an intentional equivalent, not a claim to reproduce a full remote Docker environment. ([docs.openwebui.com](https://docs.openwebui.com/features/chat-conversations/chat-features/code-execution/?utm_source=openai))

### Acceptance criteria and tests

- Execute arithmetic, text processing, and artifact-generation fixtures.
- Infinite loops can be terminated.
- Attempts to read app secrets, databases, or unrelated files fail.
- Network access fails by default.
- Runtime crashes leave the chat app usable.
- Resource-exhaustion tests are documented.
- Unsupported packages and languages are identified before execution where possible.

**Release gate:** no arbitrary-code feature ships under a “sandboxed” label without evidence supporting that claim.

---

## Phase 11 — Open WebUI plugin compatibility

**Dependencies:** Phases 7 and 10; build on the Phase 2 prototype.

### Compatibility strategy

Build a versioned compatibility layer around the pinned upstream API surface.

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

Upstream documents Tools and multiple Function types; discover the precise API from the selected source revision instead of assuming a permanent fixed set. ([docs.openwebui.com](https://docs.openwebui.com/features/extensibility/plugin/?utm_source=openai))

### Tasks

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

### Acceptance criteria and tests

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

## Phase 12 — Workspace and remaining single-user parity

**Dependencies:** Relevant engines only; split into independently implementable tasks.

### Tasks

Use the parity inventory to finish:

- Notes and personal knowledge workflows.
- Explicitly managed memory.
- Model presets and workspace configuration.
- Chat import/export compatibility.
- Backup and restore.
- Feedback and usage reporting where applicable.
- External tool protocols, if present upstream.
- Automations and scheduling.
- Advanced voice, task, artifact, and workspace features.
- Any other discovered single-user functionality.

Current Open WebUI documentation includes areas such as memory, message queues, automations, and task management, so do not reduce parity to basic chat, search, and attachments. ([docs.openwebui.com](https://docs.openwebui.com/features/?utm_source=openai))

### Acceptance criteria and tests

- Every feature has its own acceptance tests.
- Backup/restore round-trips supported data.
- Import preserves IDs, branches, attachments, and settings where compatible.
- Unsupported imported fields produce a report rather than silent loss.
- Secrets are excluded by default.
- Scheduled work explains timing limitations and external-model usage.
- No applicable upstream feature disappears from tracking.

---

## Phase 13 — Hardening and open-source release

**Dependencies:** Completed features selected for the release.

### Tasks

- Database migration testing.
- Security review of plugins, rendering, imports, tools, and networking.
- Accessibility and localization readiness.
- Performance and battery profiling.
- Low-memory and low-storage behavior.
- Physical-device background tests.
- ABI and native-library validation.
- Dependency/license inventory and software bill of materials.
- Contributor guide, architecture guide, privacy documentation, and release process.
- Signed release artifacts without placing signing keys in the container image or repository.

### Acceptance criteria and tests

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

# 5. Dependency order and parallel work

```text
Phase 0: Source inventory
    ↓
Phase 1: Build and foundation
    ├── Phase 2: Feasibility experiments
    └── Phase 3: Native UI
             ↓
Phase 4: Basic chat
             ↓
Phase 5: Durable/background generation
    ├── Phase 6: Conversation features
    ├── Phase 7: Tools and search
    │       ├── Phase 8: Documents/RAG integration
    │       └── Phase 10: Local code runtime ← Phase 2
    │                    ↓
    │             Phase 11: Plugins
    └── Phase 9: Speech and images

Phase 12: Remaining parity, implemented incrementally
Phase 13: Hardening and release
```

Document extraction and local indexing can begin before their tool integration is ready. Speech, image services, and conversation organization should not wait for Python compatibility.

**Suggested milestones**

1. **Foundation:** Docker build, native screens, local profile.
2. **Usable chat:** Connections, streaming, persistence, background generation.
3. **Daily-use app:** Branches, organization, search, documents, speech, images.
4. **Extensible app:** Sandboxed execution and tested plugin compatibility.
5. **Parity release:** Remaining inventory closed and release gates passed.

---

# 6. Mandatory rules for each agent task

For every task:

1. Read relevant architecture decisions and upstream references.
2. State the intended changes and affected contracts.
3. Implement one bounded vertical slice.
4. Add tests for success, failure, cancellation, and persistence where applicable.
5. Run the applicable Docker checks.
6. Update parity status and documentation.
7. Report actual commands and results.
8. Leave the repository buildable.

Use this task template:

```text
Task ID:
Goal:
Upstream reference:
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

## Definition of done

A feature is not complete merely because its screen exists.

It is complete only when:

- The real implementation is connected.
- Loading, error, empty, unavailable, and cancellation states work.
- Data survives the relevant lifecycle events.
- Permissions and privacy behavior are explicit.
- Automated tests pass.
- Required device checks are recorded.
- Its parity entry is updated.
- There are no hidden stubs or silent fallbacks.

**Start with Phases 0–1, run Phase 2’s risky experiments early, and deliver the basic chat milestone before investing heavily in plugins.** This preserves a usable app even if advanced compatibility work takes longer than expected.