# Open WebUI Android — Full Native App Plan

## Overview

**Goal**: Build a brand-new, standalone Android application that is a full-parity Open WebUI client. The new project starts from an empty Android Gradle project. The app is **completely self-contained** — it IS the Open WebUI stack running on the device. There is no separate backend server. All application logic, data storage, RAG processing, and state management live entirely within the new Android app. The only network calls are outbound to user-configured external AI model providers (OpenAI API, Ollama on a LAN machine, Anthropic, etc.).

**Key principle**: Everything Open WebUI's backend does (data persistence, RAG, function management, user management, settings, knowledge base) is implemented natively in Kotlin inside this new Android app. The `open-webui` repo is a **feature and design reference only** — no source files, assets, or build artefacts are shared with the new project.

**Model connectivity**: The user configures one or more external AI API providers in Settings (e.g. `http://192.168.1.10:11434` for Ollama, `https://api.openai.com` for OpenAI, Anthropic, etc.). The new app calls those APIs directly.

**Scope**: Full feature parity with Open WebUI's complete feature set, all implemented natively:
- Multi-provider AI API connections (Ollama, OpenAI, Anthropic, Google Gemini, custom OpenAI-compatible)
- Chat (streaming, multi-model, file attachments, voice input/output, multi-model arena/comparison mode)
- Knowledge / RAG (local vector search, document ingestion, web/YouTube ingestion)
- Notes (rich text, local storage)
- Channels (local group conversations, @mentions, message reactions)
- Prompts library
- Workspace: Models (custom configs), Functions, Tools, Skills, Knowledge
- Settings (full Open WebUI settings parity, all stored locally)
- Local user/profile management (single-user with optional PIN lock)
- File management
- Image generation (via configured provider API)
- Audio TTS/STT (via configured provider API or Android system)
- Code execution artifacts (sandboxed WebView)
- Playground (chat completions + image generation sub-sections)
- Memories (persistent user facts injected into chat context)
- Evaluations / model feedback and Elo leaderboard
- AI background tasks (auto-title, auto-tags, follow-up suggestions, emoji reactions)

**Package root**: `com.openwebui.android`
**Project location**: A new directory `open-webui-android/` at the workspace root.

---

## Architecture Decisions

### No Backend Server
There is no Open WebUI Python server. Every feature the server normally handles is implemented in the new Android app from scratch:
- Chat history → Room database
- RAG / vector search → local embedding + cosine similarity search in Room
- Functions/Tools/Skills → stored in Room, executed via a new Kotlin scripting engine
- User settings → DataStore
- Knowledge collections → Room + local file storage
- Notes, Channels, Prompts, Memories → Room
- AI background tasks (title generation, tag generation, follow-ups) → on-device calls to the configured model
- Evaluations / feedback → Room with local Elo calculation

### External Model Providers
The app talks directly to user-configured AI API endpoints. Multiple providers can be configured simultaneously. Four Retrofit provider interfaces are written from scratch: `OllamaApi`, `OpenAIApi`, `AnthropicApi`, `GoogleGeminiApi`. Provider selection per chat is handled by a new `ModelRouter` class in the repository layer.

### Local RAG
Since there is no server-side ChromaDB or PGVector, RAG is implemented on-device:
- Text extraction from uploaded documents (PDF via `android.graphics.pdf.PdfRenderer`, DOCX via Apache POI, plain text natively)
- Chunking and embedding via a configurable embedding endpoint (e.g. Ollama `POST /api/embeddings` or OpenAI `POST /v1/embeddings`)
- Embeddings stored in Room as `List<Float>` columns
- Similarity search done with cosine similarity computed in Kotlin at query time (top-K results)
- For web/YouTube ingestion: fetch URL content on-device, extract text, chunk and embed

### Kotlin Scripting Engine (New)
`FunctionLoader` and `FunctionExecutor` are written from scratch — since there is no server to execute functions, the local scripting engine is necessary for Pipe, Filter, and Action functions to work on-device. The exact contracts and caching strategy are fully specified in Implementation Note 4.

### Local User Management
Single-device, single-user by default. "Admin" features like user management and global functions are repurposed as local app management (manage local function library, configure providers, view local usage stats). A PIN/biometric app lock is the security model.

### Memories
Persistent key-value facts extracted from conversations (either manually added or auto-extracted by the model), stored in Room and injected as a system-level context block at the start of every chat. No vector search required — short text list appended to system prompt.

### AI Background Tasks
After a chat message completes, the app optionally fires lightweight follow-up model calls to:
- Generate a short chat title (already exists: `TitleGenerator.kt`)
- Generate descriptive tags for the chat
- Generate follow-up question suggestions displayed below the last message
- Generate an emoji reaction for the assistant message
All use `ModelRouter` with the same or a smaller/faster designated task model. These fire as background coroutines and do not block the chat UX.

### Evaluations
Open WebUI's evaluation system allows users to rate individual model responses (thumbs up/down) and compare two model responses side-by-side (arena mode), computing Elo ratings over time. In the Android app:
- Per-message thumbs up/down stored in `EvaluationEntity` in Room
- Arena mode: send the same prompt to two selected models simultaneously, display side-by-side, user picks winner → Elo updated locally
- Local Elo leaderboard shown in the App Management section

### Code Execution / Artifacts
LLM-generated HTML/JS artifacts are rendered in a sandboxed `WebView` composable (`ArtifactWebView`):
- `javaScriptEnabled = true`, `allowContentAccess = false`, `allowFileAccess = false`
- `WebViewClient` blocks all navigation (link clicks, redirects) — only the initial `loadData` call runs
- CDN script tags inside the artifact HTML (e.g. Chart.js, d3) are allowed to load over the network since the device has internet access
- Python artifact blocks (Pyodide) are shown as static code with a copy button — on-device Python execution is out of scope

### Math & Diagram Rendering
- **LaTeX math**: `jlatexmath-android` library — fully offline, no network, no assets from open-webui repo
- **Mermaid diagrams**: Sandboxed `ArtifactWebView` loading Mermaid.js from CDN (`cdn.jsdelivr.net`) — navigation blocked, only the CDN fetch is permitted
- Both rendered via the shared `ArtifactWebView` composable

### Sub-Task Ordering
1. **Infrastructure** — build config, provider API interfaces, domain models, database schema
2. **Core user flows** — auth/profile (local), settings, chat with streaming
3. **Feature areas** — notes, channels, knowledge/RAG, workspace (models/functions/tools/skills/prompts), file manager
4. **Extended features** — memories, evaluations, AI background tasks, multi-model arena
5. **Cross-cutting** — audio TTS/STT, image generation, playground, artifacts, markdown polish, notifications, permissions, cleanup

---

## Sub-Tasks

---

### Sub-Task 1 — New Project Scaffold & Build Configuration

**Status**: `[x] complete`

**Intent**
Create a brand-new Android Gradle project from scratch at `open-webui-android/` with the full dependency set required for all features. No files are copied from any existing project.

**Expected Outcomes**
- New Android project at `open-webui-android/` with a single `app` module, compiling cleanly
- `applicationId = "com.openwebui.android"`, `versionName = "1.0.0"`, `app_name = "Open WebUI"`
- Kotlin scripting dependencies included for on-device function execution
- Clean Architecture package structure scaffolded: `domain/`, `data/`, `presentation/`, `di/`, `navigation/`

**Todo List**
1. Create new Android project: `minSdk 26`, `targetSdk 35`, Kotlin + Jetpack Compose, Hilt
2. Add core Jetpack: `core-ktx`, `lifecycle-*`, `activity-compose`, `compose-bom`, `navigation-compose`, `room-*`, `datastore-preferences`, `paging-compose`, `work-runtime-ktx`, `biometric`
3. Add networking: `retrofit2`, `okhttp3`, `converter-gson`, `converter-kotlinx-serialization`, `logging-interceptor`
4. Add DI: `hilt-android`, `hilt-navigation-compose`, `hilt-compiler`
5. Add media/files: `coil-compose`, `coil-svg`, `exifinterface`
6. Add UI: `accompanist-permissions`, `material-icons-extended`
7. Add markdown & rendering: `markwon` (core + tables + code + syntax-highlight), `jlatexmath-android`
8. Add RAG/parsing: `jsoup`, `pdfbox-android`, `poi-ooxml`
9. Add Kotlin scripting: `kotlin-scripting-common`, `kotlin-scripting-jvm`, `kotlin-scripting-jvm-host`
10. Add logging: `timber`
11. Add serialisation: `kotlinx-serialization-json`, `gson`
12. Add security: `security-crypto`
13. Configure build variants: `debug`, `release` (with ProGuard/R8)
14. Create package skeleton: `com.openwebui.android.{domain,data,presentation,di,navigation}`

**Reference**
- Implementation Note 1 in this plan — all exact dependency versions and build config are specified there

---

### Sub-Task 2 — Provider API Interface Layer

**Status**: `[x] complete`

**Intent**
Write all four provider Retrofit API interfaces from scratch, along with a `ModelRouter` that normalises streaming across providers into a single `Flow<String>` abstraction.

**Expected Outcomes**
- Four provider interfaces fully declared: `OllamaApi`, `OpenAIApi`, `AnthropicApi`, `GoogleGeminiApi`
- `ModelRouter` routes any `Model` to the correct streaming call
- Auth injection per-provider handled via dedicated `OkHttpClient` interceptors
- All DTO data classes in `data/remote/dto/`
- `NetworkModule` (Hilt) provides all clients and API interfaces

**Todo List**
1. Create `OllamaApi.kt`: `generate`, `chat` (both `@Streaming`), `listModels`, `showModel`, `pullModel`, `deleteModel`, `createModel`, `embed`, `version`, `runningModels`
2. Create `OpenAIApi.kt`: `createChatCompletion` (`@Streaming`), `listModels`, `createEmbeddings`, `createImage`, `createSpeech`, `createTranscription` (`@Multipart`)
3. Create `AnthropicApi.kt`: `createMessage` (`@Streaming`, `anthropic-version` header, `x-api-key` auth)
4. Create `GoogleGeminiApi.kt`: `streamGenerateContent` (API key as query param, SSE stream)
5. Create `data/remote/dto/` with request/response DTO data classes for each provider
6. Create `ModelRouter.kt` in `data/remote/`:
   - Takes a `Model` + message list → returns `Flow<String>` of content tokens
   - Dispatches to correct provider based on `model.provider`
   - Handles SSE parsing differences per provider internally
7. Create `NetworkModule.kt` (Hilt `@Module`):
   - One `OkHttpClient` per provider (different base URL interceptor, auth header interceptor)
   - Base URLs sourced from `SettingsRepository` at call time via dynamic interceptor
   - `@Provides` all four API interfaces
8. Create `AppModule.kt` skeleton providing Room, DAOs, DataStore, Gson, mappers

---

### Sub-Task 3 — Domain Models (Open WebUI Schema Parity)

**Status**: `[x] complete`

**Intent**
Write all domain model data classes from scratch in `domain/model/`, with field-for-field parity to the Open WebUI SQLite schema (as derived from the Python SQLAlchemy models and `sample-export.json`). These Kotlin data classes are the app's internal source of truth; they map directly to Room entities and to the import/export JSON format.

**Open WebUI Schema Reference**
The following 29 tables exist in `webui.db`. Every table must have a corresponding domain model and Room entity so that a full data export from the web app can be imported into Android without data loss.

| Table | Key columns |
|---|---|
| `user` | id, email, username, role, name, profile_image_url, profile_banner_image_url, bio, gender, date_of_birth, timezone, presence_state, status_emoji, status_message, status_expires_at, info (JSON), settings (JSON), last_active_at, updated_at, created_at |
| `auth` | id, email, password (hashed), active |
| `api_key` | id, user_id, key, data (JSON), expires_at, last_used_at, created_at, updated_at |
| `chat` | id, user_id, title, chat (JSON — full history), created_at, updated_at, share_id, archived, pinned, meta (JSON), folder_id |
| `chat_message` | id, chat_id (FK→chat), user_id, role, parent_id, content (JSON), output (JSON), model_id, files (JSON), sources (JSON), embeds (JSON), done, status_history (JSON), error (JSON), usage (JSON), created_at, updated_at |
| `chat_file` | id, user_id, chat_id (FK→chat), message_id, file_id (FK→file), created_at, updated_at |
| `file` | id, user_id, hash, filename, path, data (JSON), meta (JSON), created_at, updated_at |
| `folder` | id, parent_id, user_id, name, items (JSON), meta (JSON), data (JSON), is_expanded, created_at, updated_at |
| `tag` | id, user_id (composite PK), name, meta (JSON) |
| `access_grant` | id, resource_type, resource_id, principal_type, principal_id, permission, created_at |
| `group` | id, user_id (owner), name, description, data (JSON), meta (JSON), permissions (JSON), created_at, updated_at |
| `group_member` | id, group_id (FK→group), user_id, created_at, updated_at |
| `channel` | id, user_id, type, name, description, is_private, data (JSON), meta (JSON), created_at, updated_at, updated_by, archived_at, archived_by, deleted_at, deleted_by |
| `channel_member` | id, channel_id, user_id, role, status, is_active, is_channel_muted, is_channel_pinned, data (JSON), meta (JSON), invited_at, invited_by, joined_at, left_at, last_read_at, created_at, updated_at |
| `channel_file` | id, user_id, channel_id (FK→channel), message_id (FK→message), file_id (FK→file), created_at, updated_at |
| `channel_webhook` | id, channel_id, user_id, name, profile_image_url, token, last_used_at, created_at, updated_at |
| `message` | id, user_id, channel_id, reply_to_id, parent_id, is_pinned, pinned_at, pinned_by, content, data (JSON), meta (JSON), created_at, updated_at |
| `message_reaction` | id, user_id, message_id, name, created_at |
| `knowledge` | id, user_id, name, description, meta (JSON), created_at, updated_at |
| `knowledge_file` | id, knowledge_id (FK→knowledge), file_id (FK→file), user_id, created_at, updated_at |
| `memory` | id, user_id, content, updated_at, created_at |
| `note` | id, user_id, title, data (JSON), meta (JSON), created_at, updated_at |
| `feedback` | id, user_id, version, type, data (JSON — RatingData), meta (JSON — MetaData), snapshot (JSON), created_at, updated_at |
| `model` | id, user_id, base_model_id, name, params (JSON), meta (JSON), is_active, updated_at, created_at |
| `prompt` | id, command, user_id, name, content, data (JSON), meta (JSON), tags (JSON), is_active, version_id, created_at, updated_at |
| `prompt_history` | id, prompt_id, parent_id, snapshot (JSON), user_id, commit_message, created_at |
| `skill` | id, user_id, name, description, content, meta (JSON), is_active, updated_at, created_at |
| `tool` | id, user_id, name, content, specs (JSON), meta (JSON), valves (JSON), updated_at, created_at |
| `function` | id, user_id, name, type, content, meta (JSON), valves (JSON), is_active, is_global, updated_at, created_at |
| `oauth_session` | id, user_id, provider, token (encrypted), expires_at, created_at, updated_at |

**Expected Outcomes**
- One Kotlin data class per Open WebUI table in `domain/model/`
- Every field name and type matches the schema above (using Kotlin-idiomatic camelCase)
- All timestamps are `Long` (epoch milliseconds, matching Open WebUI's `BigInteger` columns)
- JSON columns become `Map<String, Any>?` or typed data class fields
- `AppSettings.kt` covers multi-provider config and all Open WebUI user preferences (sourced from `user.settings` JSON)
- Export format data classes mirror `sample-export.json` structure for import/export (see Sub-Task 4b)

**Todo List**

*Core user & auth:*
1. Create `User.kt`: all 21 fields from `user` table; `UserSettings` nested class for `settings` JSON; `UserStatus` nested class for presence fields
2. Create `AppSettings.kt` (Android-local, not persisted to Room): wraps `UserSettings` + provider configs + `TTSEngine`/`STTEngine` enums + `AppLock` config + `RAGConfig`

*Chat:*
3. Create `Chat.kt`: all fields from `chat` table; `ChatMeta` data class for `meta` JSON (modelId, tags, params)
4. Create `ChatMessage.kt`: all fields from `chat_message` table; `MessageContent` sealed class (text string OR list of content blocks); `MessageUsage` data class for `usage` JSON
5. Create `ChatFile.kt`: all fields from `chat_file` table

*Files & Folders:*
6. Create `FileAttachment.kt`: all fields from `file` table; `FileMeta` nested class (name, content_type, size)
7. Create `Folder.kt`: all fields from `folder` table (including `parent_id` for nested folders)
8. Create `Tag.kt`: `id, userId, name, meta`

*Access control:*
9. Create `AccessGrant.kt`: all fields from `access_grant` table; `ResourceType` enum (KNOWLEDGE/MODEL/PROMPT/TOOL/NOTE/CHANNEL/FILE); `PrincipalType` enum (USER/GROUP); `Permission` enum (READ/WRITE)
10. Create `Group.kt` + `GroupMember.kt`: all fields from both tables

*Channels:*
11. Create `Channel.kt`: all fields from `channel` table (including soft-delete fields `deleted_at`, `archived_at`)
12. Create `ChannelMember.kt`: all fields from `channel_member` table
13. Create `ChannelMessage.kt`: all fields from `message` table (channel messages, distinct from chat messages)
14. Create `MessageReaction.kt`: all fields from `message_reaction` table
15. Create `ChannelFile.kt` + `ChannelWebhook.kt`

*Knowledge:*
16. Create `Knowledge.kt` + `KnowledgeFile.kt`: all fields from both tables
17. Create `DocumentChunk.kt`: id, documentId (→ file.id), content, chunkIndex, embedding (`List<Float>`), metadata — Android-only (no Open WebUI equivalent; needed for local RAG)

*AI assets:*
18. Create `Memory.kt`: id, userId, content, updatedAt, createdAt
19. Create `Note.kt`: id, userId, title, data (JSON), meta (JSON), createdAt, updatedAt
20. Create `Feedback.kt`: all fields from `feedback`; `RatingData` nested class; `FeedbackMeta` nested class; `FeedbackSnapshot` nested class
21. Create `Model.kt`: all fields from `model` table; `ModelParams` + `ModelMeta` nested classes
22. Create `Prompt.kt` + `PromptHistory.kt`: all fields from both tables
23. Create `Skill.kt`: all fields from `skill` table
24. Create `Tool.kt`: all fields from `tool` table
25. Create `Function.kt`: all fields from `function` table; `FunctionMeta` nested class; `FunctionType` enum (PIPE/FILTER/ACTION)

**Reference**
- `open-webui/backend/open_webui/models/` — the authoritative source for all column names and types

---

### Sub-Task 4 — Room Database Schema (Open WebUI Parity)

**Status**: `[x] complete`

**Intent**
Write the Room database from scratch with field-for-field parity to Open WebUI's 29-table SQLite schema. The Room schema is version 1 (no migration). This schema enables lossless import of a `webui.db` export, lossless export back to the Open WebUI JSON format, and future sync operations.

**Dual-Storage Pattern for Chats**
Open WebUI stores chat data in two places simultaneously:
1. `chat.chat` — a single JSON column containing the full message history tree (legacy format, used for sharing/export)
2. `chat_message` — a normalised table with one row per message (used for analytics, search, and the newer API)

The Android Room schema replicates both: `ChatEntity` has a `chatJson` column (mirrors `chat.chat`) AND individual `ChatMessageEntity` rows. During import the JSON is parsed into rows; during export rows are serialised back to the JSON blob.

**Expected Outcomes**
- `AppDatabase` version 1 with all 32 entities declared (29 Open WebUI + 3 Android-only: `DocumentChunkEntity`, `OAuthSessionEntity`, `FunctionUserValveEntity`)
- Room foreign keys with `CASCADE` delete replicate Open WebUI's FK constraints
- Composite primary keys and unique constraints match the source schema exactly
- `Converters.kt` handles all JSON column types
- One DAO per entity (or feature group)
- Entity ↔ domain model mappers in `data/local/mapper/`

**Entity Specification**

*User & Auth group:*
- `UserEntity` (table: `user`): id PK, email, username, role, name, profileImageUrl, profileBannerImageUrl, bio, gender, dateOfBirth, timezone, presenceState, statusEmoji, statusMessage, statusExpiresAt, infoJson, settingsJson, oauthJson, lastActiveAt, updatedAt, createdAt
- `ApiKeyEntity` (table: `api_key`): id PK, userId, key (unique), dataJson, expiresAt, lastUsedAt, createdAt, updatedAt

*Chat group:*
- `ChatEntity` (table: `chat`): id PK, userId, title, chatJson (full history JSON), createdAt, updatedAt, shareId (unique, nullable), archived, pinned, metaJson, folderId; indices on (userId+pinned), (userId+archived), (updatedAt+userId), folderId
- `ChatMessageEntity` (table: `chat_message`): id PK, chatId (FK→chat CASCADE), userId, role, parentId, contentJson, outputJson, modelId, filesJson, sourcesJson, embedsJson, done, statusHistoryJson, errorJson, usageJson, createdAt, updatedAt; indices on chatId, userId, modelId, createdAt, (chatId+parentId), (modelId+createdAt), (userId+createdAt)
- `ChatFileEntity` (table: `chat_file`): id PK, userId, chatId (FK→chat CASCADE), messageId, fileId (FK→file CASCADE), createdAt, updatedAt; unique(chatId+fileId)

*File & Folder group:*
- `FileEntity` (table: `file`): id PK, userId, hash, filename, path, dataJson, metaJson, createdAt, updatedAt
- `FolderEntity` (table: `folder`): id PK, parentId (nullable self-ref), userId, name, itemsJson, metaJson, dataJson, isExpanded, createdAt, updatedAt
- `TagEntity` (table: `tag`): composite PK (id + userId), name, metaJson; index on userId

*Access Control group:*
- `AccessGrantEntity` (table: `access_grant`): id PK, resourceType, resourceId, principalType, principalId, permission, createdAt; unique(resourceType+resourceId+principalType+principalId+permission)
- `GroupEntity` (table: `group`): id PK, userId, name, description, dataJson, metaJson, permissionsJson, createdAt, updatedAt
- `GroupMemberEntity` (table: `group_member`): id PK, groupId (FK→group CASCADE), userId, createdAt, updatedAt

*Channel group:*
- `ChannelEntity` (table: `channel`): id PK, userId, type, name, description, isPrivate, dataJson, metaJson, createdAt, updatedAt, updatedBy, archivedAt, archivedBy, deletedAt, deletedBy
- `ChannelMemberEntity` (table: `channel_member`): id PK, channelId, userId, role, status, isActive, isChannelMuted, isChannelPinned, dataJson, metaJson, invitedAt, invitedBy, joinedAt, leftAt, lastReadAt, createdAt, updatedAt
- `ChannelMessageEntity` (table: `message`): id PK, userId, channelId, replyToId, parentId, isPinned, pinnedAt, pinnedBy, content, dataJson, metaJson, createdAt, updatedAt
- `MessageReactionEntity` (table: `message_reaction`): id PK, userId, messageId, name, createdAt
- `ChannelFileEntity` (table: `channel_file`): id PK, userId, channelId (FK→channel CASCADE), messageId (FK→message CASCADE), fileId (FK→file CASCADE), createdAt, updatedAt; unique(channelId+fileId)
- `ChannelWebhookEntity` (table: `channel_webhook`): id PK, channelId, userId, name, profileImageUrl, token, lastUsedAt, createdAt, updatedAt

*Knowledge group:*
- `KnowledgeEntity` (table: `knowledge`): id PK, userId, name, description, metaJson, createdAt, updatedAt
- `KnowledgeFileEntity` (table: `knowledge_file`): id PK, knowledgeId (FK→knowledge CASCADE), fileId (FK→file CASCADE), userId, createdAt, updatedAt; unique(knowledgeId+fileId)
- `DocumentChunkEntity` (Android-only, no Open WebUI equivalent): id PK, fileId (FK→file), content, chunkIndex, embeddingJson (List<Float> as JSON), metadataJson, createdAt — needed for on-device cosine-similarity RAG

*AI Assets group:*
- `MemoryEntity` (table: `memory`): id PK, userId, content, updatedAt, createdAt
- `NoteEntity` (table: `note`): id PK, userId, title, dataJson, metaJson, createdAt, updatedAt
- `FeedbackEntity` (table: `feedback`): id PK, userId, version, type, dataJson, metaJson, snapshotJson, createdAt, updatedAt
- `ModelEntity` (table: `model`): id PK, userId, baseModelId, name, paramsJson, metaJson, isActive, updatedAt, createdAt
- `PromptEntity` (table: `prompt`): id PK, command (unique+indexed), userId, name, content, dataJson, metaJson, tagsJson, isActive, versionId, createdAt, updatedAt
- `PromptHistoryEntity` (table: `prompt_history`): id PK, promptId (FK→prompt, indexed), parentId, snapshotJson, userId, commitMessage, createdAt
- `SkillEntity` (table: `skill`): id PK, userId, name (unique), description, content, metaJson, isActive, updatedAt, createdAt
- `ToolEntity` (table: `tool`): id PK, userId, name, content, specsJson, metaJson, valvesJson, updatedAt, createdAt
- `FunctionEntity` (table: `function`): id PK, userId, name, type, content, metaJson, valvesJson, isActive, isGlobal (indexed), updatedAt, createdAt
- `FunctionUserValveEntity` (Android-only): functionId+userId composite PK, valvesJson, updatedAt
- `OAuthSessionEntity` (table: `oauth_session`): id PK, userId, provider, token, expiresAt, createdAt, updatedAt; indices on userId, expiresAt, (userId+provider)

**Todo List**
1. Create all 32 `@Entity` data classes in `data/local/entity/` using the specification above — column names must match Open WebUI snake_case converted to camelCase
2. Add `@ForeignKey` with `onDelete = CASCADE` for every FK relationship listed in the spec
3. Add `@Index` annotations matching the Open WebUI indices (see per-entity spec above)
4. Create `Converters.kt`: `@TypeConverter` for `List<String>`, `List<Float>`, `Map<String, Any>?`, `List<Any>?` — all via Gson; also converters for `Long?`↔`java.util.Date` for `dateOfBirth`
5. Create `AppDatabase.kt`: `@Database(entities=[...], version=1, exportSchema=true)` listing all 32 entities; `fallbackToDestructiveMigration()` during development
6. Create DAOs in `data/local/dao/`:
   - `ChatDao`: query chats by userId, folderId, archived, pinned; paged; search by title; CRUD; tag operations
   - `ChatMessageDao`: get by chatId (ordered by createdAt), by userId, by modelId; upsert; delete by chatId
   - `ChatFileDao`: by chatId, by messageId; upsert; delete by chatId
   - `FileDao`: CRUD; get by hash (dedup check); get by userId
   - `FolderDao`: CRUD; get by userId; get children by parentId
   - `TagDao`: get by userId; get by (id+userId); delete orphan tags
   - `AccessGrantDao`: CRUD; get by (resourceType+resourceId); get by principalId
   - `GroupDao` + `GroupMemberDao`: CRUD for both
   - `ChannelDao`: CRUD; get by userId; active channels only (deletedAt IS NULL)
   - `ChannelMemberDao`: by channelId, by userId; unread count via lastReadAt
   - `ChannelMessageDao`: by channelId, paged; by parentId (thread); search; reactions
   - `MessageReactionDao`: by messageId; by (messageId+userId+name)
   - `KnowledgeDao` + `KnowledgeFileDao`: CRUD; files by knowledgeId
   - `DocumentChunkDao`: by fileId; cosine-similarity query (returns all chunks for a knowledgeId, sorting done in Kotlin)
   - `MemoryDao`: all memories by userId; CRUD
   - `NoteDao`: paged by userId; search by title; CRUD
   - `FeedbackDao`: by userId, by type, paged; CRUD
   - `ModelDao`: all active models; by userId; CRUD
   - `PromptDao`: by userId; by command (unique lookup); active only; CRUD
   - `PromptHistoryDao`: by promptId (ordered); by parentId; CRUD
   - `SkillDao` + `ToolDao`: CRUD; active only
   - `FunctionDao`: CRUD; active only; global only (isGlobal=true)
   - `UserDao` + `ApiKeyDao` + `OAuthSessionDao`: CRUD; clean up expired sessions
7. Create mappers in `data/local/mapper/`: one `*EntityMapper.kt` per entity ↔ domain model pair
8. Register all DAOs in `AppModule.kt`

---


### Sub-Task 4b — Import / Export / Sync Engine

**Status**: `[x] complete`

**Intent**
Implement lossless data portability between the Android app and Open WebUI's `webui.db`. This covers three operations:
1. **Export** — dump all local Room data to the Open WebUI JSON format so it can be imported into a web instance
2. **Import** — parse an Open WebUI JSON export (or a direct `webui.db` SQLite file) and load it into Room without data loss
3. **Sync** — two-way reconciliation between the Android local database and a live Open WebUI server instance via the REST API (conflict resolution: last-write-wins by `updated_at` timestamp)

**Export Format (Open WebUI JSON)**

Derived from `sample-export.json` and the `ChatImportForm` / `ChatsImportForm` Pydantic models:

```json
{
  "version": 1,
  "exportDate": 1717484000000,
  "chats": [
    {
      "id": "uuid",
      "userId": "string",
      "title": "string",
      "chat": { /* full history JSON blob — mirrors chat.chat column */ },
      "messages": [ /* flat array for convenience; derived from chat.chat.history.messages */ ],
      "createdAt": 1717484000000,
      "updatedAt": 1717484015000,
      "shareId": null,
      "archived": false,
      "pinned": true,
      "folderId": null,
      "tags": ["python", "coding"],
      "meta": { "modelId": "...", "systemPrompt": "...", "temperature": 0.7, ... }
    }
  ],
  "folders": [ { "id": "...", "name": "...", "parentId": null, ... } ],
  "memories": [ { "id": "...", "content": "...", ... } ],
  "notes": [ { "id": "...", "title": "...", "data": {...}, ... } ],
  "prompts": [ { "command": "...", "name": "...", "content": "...", ... } ],
  "models": [ { "id": "...", "name": "...", "params": {...}, "meta": {...}, ... } ],
  "tools": [ { "id": "...", "name": "...", "content": "...", ... } ],
  "functions": [ { "id": "...", "name": "...", "type": "...", "content": "...", ... } ],
  "knowledge": [ { "id": "...", "name": "...", "description": "...", ... } ],
  "settings": null
}
```

Message object shape (within `messages` array):
```json
{
  "id": "msg-001",
  "role": "user",
  "content": "string or list-of-blocks",
  "parentId": null,
  "modelId": "gpt-4",
  "files": [],
  "sources": [],
  "embeds": [],
  "done": true,
  "statusHistory": [],
  "error": null,
  "usage": { "promptTokens": 0, "completionTokens": 0 },
  "createdAt": 1717484000000,
  "updatedAt": 1717484005000
}
```

**Sync Strategy**

The sync target is a live Open WebUI REST API. Authentication is via the user's Open WebUI API key (stored in `AppSettings`). Sync is always incremental using `updated_at` timestamps:
1. Fetch all remote records modified since `lastSyncedAt` for each resource type
2. Compare with local records by `id` + `updated_at`
3. Conflict: `updated_at` remote > local → take remote; local > remote → push local; equal → skip
4. New remote records: insert locally
5. New local records (created offline): push to server via POST
6. Soft-deleted records (channel `deleted_at` set): delete locally
7. After sync completes, write `lastSyncedAt = now()` to DataStore

**Resource types synced**: chats, chat_messages, folders, tags, memories, notes, prompts, models, tools, functions, knowledge, files, channels, channel_messages, feedback

**Expected Outcomes**
- `ExportManager.kt` serialises all Room data to the Open WebUI JSON format and writes to `Downloads/` via `MediaStore`
- `ImportManager.kt` parses the JSON, validates version field, upserts all records into Room (no duplicates)
- `SyncManager.kt` performs incremental two-way sync against a configured Open WebUI server
- Import handles both the flat `messages[]` array format AND the nested `chat.chat` JSON blob
- Timestamps normalised: Open WebUI uses epoch **seconds** in some places and **milliseconds** in others — `TimestampNormalizer.kt` handles both via the same heuristic as `_normalize_timestamp()` in the Python backend (values < 1e10 are seconds, multiply by 1000)
- Export produces a file that can be dragged into Open WebUI's import dialog with zero errors

**Todo List**
1. Create `ExportManager.kt`:
   - Query all entities from Room via DAOs
   - Map `ChatMessageEntity` rows back into `chat.chat` JSON blob format (reconstruct `history.messages` dict keyed by message id)
   - Serialize to `WebUIExportDto` data class (mirrors the JSON structure above)
   - Convert to JSON via Gson
   - Write to `Downloads/openwebui-export-{timestamp}.json` via `MediaStore.Downloads`
   - Include: chats, folders, memories, notes, prompts, models, tools, functions, knowledge collections
2. Create `ImportManager.kt`:
   - Accept `Uri` (from file picker) or `File` (from direct DB copy)
   - Parse JSON to `WebUIExportDto`
   - Validate `version == 1`, reject unknown versions with user-visible error
   - For each resource type: upsert to Room (INSERT OR REPLACE); preserve existing `createdAt` if record already exists
   - For chats: parse `messages[]` array → insert/update `ChatMessageEntity` rows AND write `chatJson` column from the original `chat` blob (if present) or reconstruct it
   - Create `TimestampNormalizer.kt`: normalise `Long` values (< 1e10 → multiply ×1000, else use as-is)
   - Report progress via `Flow<ImportProgress>` with counts per resource type
3. Create `SyncManager.kt` (`@Singleton`):
   - Reads `syncEnabled` + `serverBaseUrl` + `apiKey` from `SettingsRepository`
   - Calls the Open WebUI REST API (reuse `OpenAIApi` or add a `WebUIServerApi` interface)
   - Per-resource incremental sync loop (see strategy above)
   - Writes `lastSyncedAt` to DataStore after success
   - `sync()` runs in a `WorkManager` `CoroutineWorker` for background execution
   - Emits `SyncStatus` (IDLE / SYNCING / SUCCESS / FAILED) as a `StateFlow`
4. Create `WebUIServerApi.kt` Retrofit interface (if separate from existing provider API):
   - `GET /api/chats?limit=&skip=` + `GET /api/chats/{id}`
   - `POST /api/chats/new`, `POST /api/chats/{id}`, `DELETE /api/chats/{id}`
   - `GET /api/memories`, `POST /api/memories/add`, `DELETE /api/memories/{id}`
   - `GET /api/notes`, `POST /api/notes/add`, `PUT /api/notes/{id}`, `DELETE /api/notes/{id}`
   - `GET /api/prompts`, `POST /api/prompts/create`, `DELETE /api/prompts/{command}`
   - `GET /api/models`, `POST /api/models/create`, `DELETE /api/models/{id}`
   - `GET /api/tools`, `POST /api/tools/create`, `DELETE /api/tools/{id}`
   - `GET /api/functions`, `POST /api/functions/create`, `DELETE /api/functions/{id}`
   - `GET /api/knowledge`, `POST /api/knowledge/create`, `DELETE /api/knowledge/{id}`
   - `GET /api/folders`, `POST /api/folders/create`, `DELETE /api/folders/{id}`
   - `GET /api/channels`, `GET /api/channels/{id}/messages`
5. Create `SyncWorker.kt` (`CoroutineWorker`): schedules periodic sync (default: every 15 min when on WiFi) + one-shot manual trigger; shows a persistent notification during sync
6. Add sync settings to `SettingsDataScreen`: server URL, API key, sync enabled toggle, "Sync now" button, last synced timestamp
7. Add "Export Data" and "Import Data" buttons to `SettingsDataScreen` (already planned in Sub-Task 8)

**Reference**
- `open-webui/backend/open_webui/models/chats.py` — `ChatImportForm`, `ChatsImportForm`, `import_chats()` method
- `sample-export.json` — canonical export format

---


### Sub-Task 5 — Repository Layer

**Status**: `[x] complete`

**Intent**
Write all repository interfaces (in `domain/repository/`) and implementations (in `data/repository/`) from scratch. All data is local-first; the only external calls are to AI provider APIs.

**Expected Outcomes**
- All repository interfaces declared in `domain/repository/`
- All implementations in `data/repository/` bound via Hilt `@Binds`
- `ChatRepositoryImpl` streams through `ModelRouter`
- `KnowledgeRepositoryImpl` does full on-device RAG pipeline
- `FunctionRepositoryImpl` delegates execution to `FunctionExecutor`

**Todo List**
1. **Create `ChatRepositoryImpl`**: `getAllChats()`, `getChatById()`, `createChat()`, `updateChat()`, `deleteChat()` → `ChatDao`; `sendMessage()` → apply filter inlets → `ModelRouter.stream()` → save chunks → apply filter outlets; `setChatFolder()`, `setChatTags()`, `toggleWebSearch()`, `archiveChat()`, `pinChat()`
2. **Create `ModelRepositoryImpl`**: for each enabled `ProviderConfig` fetch models, merge, cache in `ModelDao`; per-provider failure isolated; `createCustomModel()` → local `ModelEntity`
3. **Create `SettingsRepositoryImpl`**: all prefs in DataStore with `getSettings(): Flow<AppSettings>`; `getProviderConfigs()`, `addProviderConfig()`, `updateProviderConfig()`, `deleteProviderConfig()`; `testProviderConnection()` pings provider endpoint
4. **Create `NoteRepositoryImpl`**: CRUD against `NoteDao`
5. **Create `ChannelRepositoryImpl`**: channel + message CRUD against `ChannelDao`; unread count tracking
6. **Create `FolderRepositoryImpl`**: CRUD against `FolderDao`; cascade-move chats to root on folder delete
7. **Create `TagRepositoryImpl`**: tag CRUD + `getChatsByTag()` via `ChatTagCrossRef`
8. **Create `PromptRepositoryImpl`**: CRUD against `PromptDao` with version history support
9. **Create `FunctionRepositoryImpl`**: CRUD against `FunctionDao`; execution delegated to `FunctionExecutor`
10. **Create `ToolRepositoryImpl`**: local tool CRUD + valve config against `ToolDao`
11. **Create `SkillRepositoryImpl`**: local skill CRUD against `SkillDao`; execution via `FunctionLoader` as `ActionFunction`
12. **Create `KnowledgeRepositoryImpl`**: text extraction (PDF → `PdfRenderer`, DOCX → Apache POI, text natively); chunking by `RAGConfig`; embed via provider API → `DocumentChunkEntity`; `searchSimilarChunks()` → cosine similarity top-K; `ingestUrl()` via Jsoup; `ingestYoutube()` via subtitle XML
13. **Create `FileRepositoryImpl`**: file metadata in `FileAttachmentEntity`; binary in app-private `files/` dir
14. **Create `AudioRepositoryImpl`**: `transcribe()` → Android `SpeechRecognizer` or provider API; `synthesize()` → Android `TextToSpeech` or provider API
15. **Create `ImageRepositoryImpl`**: `generateImage()` via `OpenAIApi.createImage()` or provider equivalent
16. **Create `EvaluationRepositoryImpl`**: ratings in `EvaluationDao`; Elo recalculated on new comparison
17. **Create `MemoryRepositoryImpl`**: CRUD against `MemoryDao`; `getAllMemories()` injected into chat system prompt
18. Declare all repository interfaces in `domain/repository/`; bind in `AppModule.kt` via `@Binds`

---

### Sub-Task 6 — Navigation & Scaffold Structure

**Status**: `[x] complete`

**Intent**
Write the navigation layer from scratch: a sealed `Screen` hierarchy, **single-stack `NavHost`**, sidebar-only scaffold, and `MainActivity`. **No bottom navigation bar** — all navigation is driven by the sidebar drawer and user menu popover, matching the web app exactly.

**Expected Outcomes**
- `Screen.kt` sealed class covers all destinations
- `NavGraph.kt` is a **single flat back-stack** (no nested tab graphs)
- `MainScaffold.kt` hosts only a `ModalNavigationDrawer` — **no** `NavigationBar` bottom bar
- `SidebarDrawer` contains: header / New Chat + Search rows / **Models section** / **Folders section** / date-grouped **Chats section** / footer with User Menu popover
- `UserMenuPopover` contains: Update status / Workspace / Calendar / Automations / Playground / Admin Panel / Settings / Sign Out
- `MainActivity` hosts the nav host and handles PIN lock on `onResume`
- Deep link `openwebui://chat/{chatId}` declared in `AndroidManifest.xml`

**Todo List**
1. Create `Screen.kt` sealed class with all destinations: `Onboarding`, `PinLock`, `NewChat`, `Chat(chatId)`, `NoteList`, `NoteEditor(noteId)`, `ChannelList`, `Channel(channelId)`, `Workspace`, `CreateModel`, `Playground`, `AdminPanel`, `AdminSettings`, `Settings`, `Memories`, `Automations`, `Calendar`, `Profile`, `AppManagement`
2. Create `NavGraph.kt` with a **single flat** `NavHost` — all secondary screens push onto the back stack from `NewChat`/`Chat`
3. Create `MainScaffold.kt`: `ModalNavigationDrawer` wrapping the content `NavHost`. **No `NavigationBar`**
4. Create `SidebarDrawer.kt`: header (OI logo + "Open WebUI" + collapse icon) / New Chat row / Search row / **Models section** (pinned models list, tap → new chat with that model) / **Folders section** (`+` button) / **Chats section** (date-grouped, `···` overflow, chat rows: title + relative time) / footer (avatar + "User" + green dot)
5. Create `UserMenuPopover.kt`: dark card popover anchored above sidebar footer, items with icons: Update your status / Workspace / Calendar / Automations / Playground / Admin Panel / Settings / Sign Out
6. Create `MainActivity.kt`: `@AndroidEntryPoint`, edge-to-edge, hosts `NavHost`, checks PIN lock on `onResume`
7. Create `AndroidManifest.xml`: all permissions (INTERNET, RECORD_AUDIO, CAMERA, READ_MEDIA_*, POST_NOTIFICATIONS), `FileProvider`, deep link intent filter, `usesCleartextTraffic=true`

---

### Sub-Task 7 — Onboarding, Profile & App Lock

**Status**: `[x] complete`

**Intent**
Write first-launch onboarding, local profile screen, and optional PIN/biometric lock from scratch. Single-user, no server auth.

**Expected Outcomes**
- Onboarding: name → provider setup → optional PIN
- PIN/biometric lock screen on resume if enabled
- Profile screen: edit name, avatar, app lock toggle

**Todo List**
1. Create `ProfileRepository` interface + `ProfileRepositoryImpl`: stores `displayName`, `avatarPath`, `onboardingComplete` in DataStore
2. Create `OnboardingScreen.kt` + `OnboardingViewModel.kt`: 3-step flow (welcome/name, add provider with test, optional PIN); marks `onboardingComplete = true` on finish
3. Create `PinLockScreen.kt` + `PinLockViewModel.kt`: 6-digit PIN grid; `BiometricPrompt` if biometric enabled; shown from `MainActivity.onResume()`
4. Create `ProfileScreen.kt` + `ProfileViewModel.kt`: display/edit name; avatar pick (gallery or camera) → compress JPEG 85% max 512px → save to `files/avatar.jpg`; show providers; app lock toggle
5. Create `ImageCaptureHelper.kt`: `ActivityResultContracts.TakePicture`, JPEG compression, EXIF rotation correction

---

### Sub-Task 8 — Settings Screens

**Status**: `[x] complete`

**Intent**
Write all Settings screens from scratch.

**Expected Outcomes**
- Settings is a **full-screen modal** (pushed from sidebar user menu) with a **horizontally-scrollable tab bar** at top
- Tabs (confirmed from live screenshots): General | Interface | Notifications | Keyboard | Connections | Integrations | Personalization | Audio | Data Controls
- `← Back` styled as first tab item (chevron + blue outline), `Save` pill button fixed bottom-right
- All toggles, value rows, and section headers match the live screenshot spec exactly

**Todo List**
1. Create `SettingsScreen.kt` + `SettingsViewModel.kt`: full-screen overlay with horizontally-scrollable tab bar (NOT a hub with cards). Tabs: General / Interface / Notifications / Keyboard / Connections / Integrations / Personalization / Audio / Data Controls / Usage / Archived Chats. `← Back` as first tab item (orange/yellow outline box). `Save` pill fixed bottom-right (not shown on Keyboard or Notifications tabs).
2. **General tab**: Section header "WebUI Settings"; `Theme` dropdown button (gear icon + value + chevron) + description; `Language` dropdown + description; Section "System Prompt" + textarea + description; Section "Advanced Parameters" + `Model parameters` row with `Show` value + description. Each field has a description line below it.
3. **Interface tab** (complete toggle list from screenshots — each toggle has description text below): UI section: UI Scale / High Contrast Mode / Display Chat Title in Tab / Allow User Location / **Haptic Feedback (Android)** / Copy Formatted Text / Toast Notifications for New Updates / Show "What's New" Modal on Login — Chat section: Enable Message Queue / Chat Direction / Landing Page Mode / Chat Background Image / Chat Bubble UI / Display Username Instead of "You" / Widescreen Mode / Temporary Chat by Default / Fade Effect for Streaming Text / Render Markdown in User Messages / Render Markdown in Assistant Messages / Title Auto-Generation / Follow-Up Auto-Generation / **Chat Tags Auto-Generation** / **Auto-Copy Response to Clipboard** / **Response Auto-Scroll** / **Insert Suggestion Prompt to Input** / **Keep Follow-Up Prompts in Chat** / **Insert Follow-Up Prompt to Input**
4. **Notifications tab**: Browser Notifications toggle + description / Notification Sound toggle / **Notification Targets** section header (bold) + `+` button + empty state "No notification targets configured."
5. **Keyboard tab** (interactive, NOT static): `Enable Keyboard Shortcuts` toggle + `Reset Defaults` button. Command/Key table with groups: Chat (New Chat ⌘⇧O / New Temporary Chat / Delete Chat / Open Model Selector / Toggle Dictation / Navigate to Prev/Next Chat) / Global (Search ⌘K / Open Settings / Show Shortcuts / Toggle Sidebar / Toggle Controls / Close Modal Esc) / Input (Focus Chat Input / Accept Autocomplete Generation / Jump to Prompt Variable). Rows are tappable to reassign keys.
6. **Connections tab**: "Connections" page heading; Manage Direct Connections section; + button (right of section header); description texts; empty state.
7. **Data Controls tab**: Chats section (Import [Import] / Export [Export] / Shared Chats [Manage] / Archive All Chats [Archive All] / Delete All Chats [Delete All]) + Files section (Manage Files [Manage]). Tab bar also shows Usage + Archived Chats tabs.
8. **Integrations, Personalization, Audio tabs**: implement content matching the web equivalents.
9. Wire all settings to `SettingsRepository` (DataStore). The Connections tab manages provider API endpoints.
10. `ExportManager.kt` + `ImportManager.kt` wired from Data Controls → Export Chats / Import Chats rows.

---

### Sub-Task 9 — Chat Screens (Core Chat Feature)

**Status**: `[x] complete`

**Intent**
Write the home screen, chat thread screen, message input, message bubbles, controls panel, and sidebar drawer from scratch — the central UI of the app.

**Expected Outcomes**
- Home: OI logo + model name headline centred; rounded input card with `+` / `◈` / model chip / mic / waveform-FAB toolbar; `⚡ Suggested` label + 3 suggestion rows (bold title + grey subtitle)
- Chat thread: streaming messages, thinking blocks, citations row (overlapping avatars + "N Sources"), 7-icon action strip, "Follow up" plain-text rows
- Input bar (exact toolbar order): `+` attach → `◈` tools → model name chip → 🎤 mic → ⚫ send FAB
- Controls panel: **full-screen overlay** (not a side panel), `Controls | Overview` tabs, Valves + System Prompt + 20 Advanced Params as label/Default pairs
- Model selector: **inline dropdown** anchored below chip, search + `All ▾` filter + model list with □ clone and 🔗 link icons per row + `Set as default`
- Sidebar (also built here — the sole navigation surface): header / New Chat / Search / **Models section** / **Folders section** / date-grouped Chats / footer User Menu

**Todo List**
1. Create `NewChatScreen.kt` + `NewChatViewModel.kt`: centred OI logo + model name headline; `MessageInputBar`; `⚡ Suggested` label + 3 suggestion rows (bold title + grey subtitle)
2. Create `ChatScreen.kt` + `ChatViewModel.kt`: `sendMessage()` → filter inlets → `ModelRouter` → chunk stream → filter outlets; `toggleWebSearch()`, `setKnowledgeCollection()`, `shareChat()`, `archiveChat()`, `pinChat()`
3. Create `MessageInputBar.kt`: multiline `BasicTextField`; toolbar row (left to right): `+` / `◈` four-diamond sparkles / `ModelChip` (truncated name + `▾`) / 🎤 mic / ⚫ waveform-FAB; slash-command autocomplete overlay; `#` knowledge picker overlay
4. Create `MessageBubble.kt`: user (right-aligned, ~`#2A2A2A` bg, white text, 18dp radius, 80% max-width); assistant (left, transparent, full-width); markdown rendered (bold, italic, blockquotes with left accent border, hyperlinks); **RAG citation row** (overlapping circular source avatars + `+N` + `N Sources`, above action strip); **7-icon action strip** (✏ 🗑 🔊 ℹ ▶ ↻ ↗); `<think>` collapsible card; **"Follow up" label + 5 plain grey tappable text rows** (not chips)
5. Create `ControlsPanel.kt`: **full-screen overlay** (not a side panel). `Controls | Overview` tabs + `✕` close. Sections: Valves (collapsible) / System Prompt (textarea) / Advanced Params (20 rows: Stream Chat Response, Stream Delta Chunk Size, Context Compaction Threshold, Function Calling, Reasoning Tags, Seed, Stop Sequence, Temperature, Reasoning Effort, logit_bias, max_tokens, top_k, top_p, min_p, frequency_penalty, presence_penalty, mirostat, mirostat_eta, mirostat_tau, repeat_last_n). Each param: label (left) + `Default` tappable value (right).
6. Create `ModelSelectorDropdown.kt`: **inline dropdown** (not a bottom sheet) anchored below the model chip. Dark card, 🔍 search + `◈◈` multi-select icon + `All ▾`. Model rows: OI icon + name + □ clone + 🔗 link. Selected model: ✓ checkmark. Bottom: `Set as default` text action.
7. Create `SidebarDrawer.kt` (this is the **sole navigation surface** — no bottom nav bar): header (OI logo + "Open WebUI" + collapse icon) / New Chat row (✏ pencil icon) / Search row (🔍) / **Models section** (list of configured models, tap → open New Chat with that model) / **Folders section** (with `+` button) / date-grouped **Chats section** (`···` overflow) / footer (avatar + "User" + green dot → `UserMenuPopover`)
8. Create `UserMenuPopover.kt`: dark card above sidebar footer. Items: 😊 Update your status / ⊞ Workspace / 📅 Calendar / ⏱ Automations / `</>` Playground / 👤 Admin Panel / ⚙ Settings / ⎋ Sign Out
9. Create `AudioRecorder.kt`: M4A/AAC via `MediaRecorder`, start/stop/cancel

---

### Sub-Task 10 — Knowledge / RAG

**Status**: `[x] complete`

**Intent**
Build the Knowledge screen backed entirely by on-device RAG: local document ingestion with chunking and embedding, cosine similarity search, web URL and YouTube transcript ingestion — no external vector DB.

**Expected Outcomes**
- Knowledge list screen with collections
- Upload documents from device (PDF, DOCX, TXT, MD, CSV)
- Ingest web URL (on-device HTTP fetch + Jsoup text extraction)
- Ingest YouTube URL (transcript fetch)
- View chunks of a document
- Per-chat knowledge toggle in chat header (Sub-Task 9)
- Embedding progress shown during processing

**Todo List**
1. Create `KnowledgeScreen.kt` + `KnowledgeViewModel.kt`:
   - Collection list with document counts, search, filter
   - `Create ▾` dropdown: Upload / Connect website / YouTube
   - File upload → `KnowledgeRepository.ingestFile()` → show `PENDING → PROCESSING → COMPLETED/FAILED`
   - URL dialog → `KnowledgeRepository.ingestUrl()`
   - YouTube dialog → `KnowledgeRepository.ingestYoutube()`
   - Document detail: chunk count, embedding model, delete
2. Dependencies for RAG parsing already declared in Sub-Task 1 (Jsoup, PdfBox, POI)

---

### Sub-Task 11 — Notes

**Status**: `[x] complete`

**Intent**
Build the Notes feature: a dedicated section for creating and editing markdown notes stored locally in Room.

**Expected Outcomes**
- Notes list with search and pagination
- Create/edit note with title and markdown body
- Auto-save on navigate away
- Delete with confirmation

**Todo List**
1. Create `NoteListScreen.kt` + `NoteListViewModel.kt`:
   - Paginated `LazyColumn` of note cards (title, excerpt, relative timestamp)
   - Search bar filters via `NoteDao.searchNotes(query)`
   - FAB to create new note
   - Swipe-to-delete with undo snackbar
2. Create `NoteEditorScreen.kt` + `NoteEditorViewModel.kt`:
   - Editable title `TextField`
   - Body: `BasicTextField` with markdown-aware input (bold/italic toolbar shortcuts)
   - Auto-save via `debounce` on text change flow
   - Preview toggle: switch between edit and rendered markdown view
   - Delete from top bar action
3. Wire to `NoteRepository` CRUD
4. Add `NoteList` and `NoteDetail` to `NavGraph`


---

### Sub-Task 12 — Channels

**Status**: `[x] complete`

**Intent**
Build Channels as local group conversation threads (not networked — all data is on-device). Channels are useful for organising conversations by topic or persona, distinct from the main chat history.

**Expected Outcomes**
- Channel list screen
- Channel detail: threaded message view
- Create/delete channels
- Unread count badge on channel list items
- Messages support text and file attachments

**Todo List**
1. Create `ChannelListScreen.kt` + `ChannelListViewModel.kt`:
   - List channels from `ChannelRepository`
   - Unread count badge per channel
   - Create channel dialog (name, description)
   - Long-press to delete
2. Create `ChannelScreen.kt` + `ChannelViewModel.kt`:
   - Message list via `ChannelDao`
   - Mark messages as read on view (clears unread badge)
   - Compose bar: text + file attach (reuse `MessageInput` components)
   - Channel messages are plain (no AI response); this is a local discussion board / notepad by topic
3. Wire to `ChannelRepository` (Sub-Task 5)

---

### Sub-Task 13 — Workspace: Models, Functions, Tools, Skills, Prompts

**Status**: `[x] complete`

**Intent**
Write the Workspace section from scratch: hub screen and 5 sub-screens for managing AI assets stored locally in Room.

**Expected Outcomes**
- Workspace hub with tab bar (Models / Knowledge / Prompts / Skills / Tools) matching observed UI
- Full CRUD for each asset type
- `FunctionLoader` + `FunctionExecutor` written from scratch (Sub-Task 5 repo impl) for on-device execution
- Prompts support `{{variable}}` substitution and version history

**Todo List**
1. Create `WorkspaceScreen.kt`: horizontal tab bar (`□` sidebar icon + `Models N` / `Knowledge N` / `Prompts N` / `Skills N` / `Tools` using full words + `Create ▾` split-button right). Column headers `Title` + `Updated at ▾` on list views.
2. Create `ModelsScreen.kt` + `ModelsViewModel.kt`: model card rows (OI icon + name + base model below + `···` menu + active toggle); search; `Actions ▾` filter. Create model form has: OI icon header + editable `Model Name` / `Model ID` + `Access` lock button; Base Model dropdown; Description + Custom label; Tags; System Prompt textarea; Advanced Params [Show]; Prompts [Default]; Knowledge (Select Knowledge + Upload); Tools; Skills; Filters checklist (all active filter functions shown as checkboxes); Capabilities checkboxes (Vision, Web Search, Image Generation). Form saves on back navigation — no Save button.
3. Create `FunctionsScreen.kt` + `FunctionsViewModel.kt`: list with type badge + active toggle + global toggle; create/edit with monospace code editor; validate + test run
4. Create `FunctionLoader.kt`: compiles Kotlin script source via `BasicJvmScriptingHost`; returns typed function instance
5. Create `FunctionExecutor.kt`: `@Singleton`; `Mutex`-protected cache; `executePipe()`, `executeFilterInlet()`, `executeFilterOutlet()`, `executeAction()`
6. Create `ToolsScreen.kt` + `ToolsViewModel.kt`: tool CRUD, parameter JSON schema editor, valve config
7. Create `SkillsScreen.kt` + `SkillsViewModel.kt`: skill CRUD, code editor, active toggle → appears in `/skill` slash command
8. Create `PromptsScreen.kt` + `PromptsViewModel.kt`: category chips, tag search; create/edit with `{{variable}}` highlighting; version history (last 5)

---

### Sub-Task 14 — File Manager

**Status**: `[x] complete`

**Intent**
Build a standalone file browser for all files uploaded in the app (used in chats or ingested into Knowledge). Files are stored in app-private storage.

**Expected Outcomes**
- File list with type icons, name, size, date
- Preview: images (Coil full-screen), PDFs (scrollable pages via `PdfRenderer`), text/code (scrollable text card)
- Delete files (with check that they are not referenced by an active chat)
- Accessible from Workspace section

**Todo List**
1. Create `FileManagerScreen.kt` + `FileManagerViewModel.kt`:
   - Load all files from `FileRepository.getAllFiles()`
   - `LazyColumn` with type icon, name, size, date
   - Tap → preview composable (type-dispatched)
   - Long-press / swipe → delete with confirmation
   - `ImagePreview`: full-screen Coil `AsyncImage` with pinch-to-zoom
   - `PdfPreview`: `AndroidView { RecyclerView }` rendering pages via `android.graphics.pdf.PdfRenderer`
   - `TextPreview`: scrollable `SelectionContainer { Text }` with monospace font
2. Add `FileManager` to Workspace graph in `NavGraph`

---

### Sub-Task 15 — "Admin" — Local App Management

**Status**: `[x] complete`

**Intent**
Repurpose the Open WebUI admin panel as a local app management section: local usage statistics, global function management, and provider health. There is no user management (single-user app) — the "Users" section becomes a local profile/account view.

**Expected Outcomes**
- App Management hub with cards: Usage Stats, Global Functions, Provider Health
- Usage stats: total chats, messages, tokens used (tracked locally), knowledge documents, notes count
- Global functions: same as Workspace Functions but toggles a function as "global" (applies to all chats automatically via FilterFunction pipeline)
- Provider health: ping each configured provider and show latency / status
- Accessible from Settings or as a dedicated section in the navigation

**Todo List**
1. Create `AppManagementScreen.kt` + `AppManagementViewModel.kt`:
   - 3 stat cards: Chats, Messages, Documents
   - Token usage counter (accumulated from `Message` metadata)
   - Navigate to sub-sections
2. Create `ProviderHealthScreen.kt`:
   - For each `ProviderConfig`, show status (ping model list endpoint)
   - Latency display
   - Quick-navigate to edit provider in Settings
3. Create `GlobalFunctionsScreen.kt`:
   - Same list as Workspace Functions but filtered to `isGlobal = true`
   - Toggle global on/off per function (writes to `FunctionEntity.isGlobal`)
4. Add as a card/section in Settings or as a 6th optional tab

---

### Sub-Task 16 — Audio: TTS & STT

**Status**: `[x] complete`

**Intent**
Write the audio pipeline from scratch: `AudioRecorder`, `STTProcessor`, and `TTSPlayer` covering all Open WebUI STT/TTS providers, with Android system TTS/STT as the always-available offline fallback.

**Expected Outcomes**
- Voice recording → transcription via selected STT provider (Android STT, Whisper via provider API, Deepgram, Azure) → text populated in input field
- TTS playback of assistant responses via selected TTS provider (Android TTS, OpenAI, ElevenLabs, Azure)
- Auto-play toggle in settings
- Audio queue for sequential playback
- All STT/TTS providers configured in `SettingsAudioScreen`

**Todo List**
1. Create `AudioRecorder.kt`: M4A/AAC via `MediaRecorder`, start/stop/cancel (already specified in Sub-Task 9, step 8 — implement here)
2. Create `STTProcessor.kt`: dispatch on `sttEngine`; `ANDROID_STT` → `SpeechRecognizer`; `OPENAI`/`WHISPER_LOCAL` → POST to provider endpoint; `DEEPGRAM`/`AZURE` → POST to respective endpoints; returns `Flow<Result<String>>`
3. Create `TTSPlayer.kt`: dispatch on `ttsEngine`; `ANDROID_TTS` → `TextToSpeech`; `OPENAI`/`ELEVENLABS`/`AZURE` → POST text → receive bytes → `MediaPlayer`; `kotlinx.coroutines.channels.Channel<TTSRequest>` queue; stop current playback
4. Create `VoiceInputBar.kt` composable: waveform animation while recording; after stop → `STTProcessor.transcribe()` → populate `MessageInputBar`
5. Integrate `TTSPlayer` into `ChatViewModel`: when `autoPlayEnabled` and message `done == true` → enqueue response text
6. Update `AudioRepositoryImpl` to delegate to `STTProcessor` and `TTSPlayer`

**Reference**
- `AudioRecorder.kt`: use `MediaRecorder` with `OutputFormat.MPEG_4` and `AudioEncoder.AAC`; request `RECORD_AUDIO` permission before starting
- `VoiceInputBar.kt`: animate a waveform while recording using `animateFloatAsState` on amplitude values from `MediaRecorder.getMaxAmplitude()`

---

### Sub-Task 17 — Image Generation & Playground

**Status**: `[x] complete`

**Intent**
Add an Image Generation screen (calls image generation endpoint on configured provider) and a Playground screen for direct API parameter testing.

**Expected Outcomes**
- Playground: prompt → model completion, full parameter control, streaming response
- Image generation: prompt → generated image displayed, save to gallery
- Both accessible from Workspace section

**Todo List**
1. Create `PlaygroundScreen.kt` + `PlaygroundViewModel.kt`:
   - Provider + model selector
   - System + user prompt inputs
   - Temperature, max tokens, top-p sliders
   - Generate → streaming response via `ModelRouter`
   - Response rendered in `MarkdownTextEnhanced`
2. Create `ImageGenerationScreen.kt` + `ImageGenerationViewModel.kt`:
   - Prompt input, size selector, count selector
   - Provider must support image generation (shown only when at least one does)
   - Generate → image displayed with Coil
   - Save to gallery via `MediaStore` insert
   - Error state if no provider supports image generation
3. Add `Playground` and `ImageGen` to Workspace graph in `NavGraph`

**Relevant Context**
- `ModelRouter` from Sub-Task 2
- `ImageApi` from Sub-Task 2

---

### Sub-Task 18 — Code Execution Artifacts (Sandboxed WebView)

**Status**: `[x] complete`

**Intent**
Render LLM-generated HTML/JS code artifacts in a sandboxed `WebView` composable embedded in the message view. The LLM generates the HTML; the WebView executes it locally. CDN script tags inside the artifact are permitted to fetch (the device has internet). Navigation away from the artifact is fully blocked.

**Expected Outcomes**
- HTML artifact blocks in messages rendered in a contained `WebView` preview
- Expandable to full-screen `Dialog`
- No navigation permitted from the artifact WebView (all link clicks and redirects blocked)
- Python (`pyodide`) artifact blocks shown as static code with copy button (no execution)
- Mermaid diagram blocks rendered in a separate sandboxed WebView loading Mermaid from CDN

**Todo List**
1. Create `ArtifactWebView.kt` composable:
   - `AndroidView { WebView }` wrapper
   - `settings.javaScriptEnabled = true`
   - `settings.allowContentAccess = false`, `settings.allowFileAccess = false`
   - Custom `WebViewClient`: override `shouldOverrideUrlLoading` → return `true` for all URLs (blocks navigation); allow initial `loadData` call only
   - `loadDataWithBaseURL(null, artifactHtml, "text/html", "UTF-8", null)`
   - Fixed height in message bubble (e.g. 240dp); full-screen via `Dialog` on tap
2. Create `ArtifactDetector.kt`:
   - Scans message content for ```` ```html ```` blocks and ```` ```mermaid ```` blocks
   - Returns `List<Artifact>` with type and content
3. Integrate into `MessageBubble` composable:
   - For each `Artifact` in message: show `ArtifactWebView` (HTML type) or mermaid `ArtifactWebView` with Mermaid CDN wrapper (mermaid type)
   - Python blocks: render as styled code block with copy button, add "Python execution not supported on Android" note
4. Mermaid wrapper: a minimal HTML string that loads `https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js` and renders the diagram block — this is hardcoded in the app, not from the open-webui repo

**Reference**
- `ArtifactWebView`: use `AndroidView { WebView }` with `settings.javaScriptEnabled = true`, `settings.allowContentAccess = false`, `settings.allowFileAccess = false`; override `shouldOverrideUrlLoading` to return `true` for all URLs

---

### Sub-Task 19 — Markdown & Rendering Polish

**Status**: `[x] complete`

**Intent**
Ensure markdown rendering covers all content types produced by Open WebUI-style LLM responses: LaTeX math, tables, code blocks with syntax highlight + copy, thinking blocks, and citations.

**Expected Outcomes**
- Code blocks: syntax highlighted, copy button, language label
- Tables: rendered as horizontally scrollable grids
- Math: inline `$...$` and block `$$...$$` rendered offline via `jlatexmath-android`
- Mermaid: "View Diagram" button opens `ArtifactWebView` (handled in Sub-Task 18)
- Thinking blocks: `<think>...</think>` rendered as collapsible card with "Thinking..." header
- Citations: tappable chips below message referencing source document name

**Todo List**
1. Audit `MarkdownTextEnhanced.kt` — identify gaps vs the above list
2. Add table rendering: parse markdown tables → `LazyRow`-based grid composable
3. Wrap code blocks with `Card` containing language label + `IconButton` for copy via `ClipboardManager`
4. Integrate `jlatexmath-android`: detect math delimiters (`$`, `$$`) → render `LaTeXMathView` composable
5. Add `<think>` tag parser: strip from main content → store in `Message.thinkingContent` (already in model); render as `ExpandableCard` in `MessageBubble`
6. Render `Message.sources` as a `FlowRow` of citation chips below the message text

**Reference**
- Use `com.halilibo.compose-richtext:richtext-commonmark` — see Implementation Note 11 for the full `MarkdownText.kt` build-from-scratch spec
- `ArtifactWebView` from Sub-Task 18 — integrate here

---

### Sub-Task 20 — Notifications

**Status**: `[x] complete`

**Intent**
Local notifications for channel messages and (optionally) when a long-running AI response completes while the app is backgrounded.

**Expected Outcomes**
- Notification when a new channel message is added while app is backgrounded
- Notification when a streaming response finishes while app is in background
- Tapping notification deep-links to the relevant chat or channel
- Notification permission requested at appropriate time

**Todo List**
1. Create `AppNotificationManager.kt` (Hilt singleton):
   - Create Android notification channels: "Channel Messages", "Response Complete"
   - `showChannelMessageNotification(channelName, preview, channelId)`
   - `showResponseCompleteNotification(chatTitle, chatId)`
2. Trigger from `ChannelRepository` when a message is inserted and app is backgrounded
3. Trigger from `ChatViewModel` when streaming completes (`done == true`) and app is in background (check via `ProcessLifecycleOwner`)
4. Use `NavDeepLinkBuilder` for pending intents to `Chat(chatId)` and `Channel(channelId)`
5. Request `POST_NOTIFICATIONS` permission at first channel message or first backgrounded completion

**Relevant Context**
- `POST_NOTIFICATIONS` already in `AndroidManifest.xml`

---

### Sub-Task 21 — Permissions, Share & Android System Integration

**Status**: `[x] complete`

**Intent**
Ensure clean Android system integration: runtime permissions requested at point of use, receiving shared files from other apps, sharing chat content out, and deep links.

**Expected Outcomes**
- Camera, microphone, media read permissions requested at point of use with rationale
- Receiving a shared image/file from another app opens New Chat with file pre-attached
- Share button on messages sends text via Android share sheet
- Deep links `openwebui://chat/{chatId}` open the correct chat

**Todo List**
1. Use `accompanist-permissions` for runtime permissions:
   - `RECORD_AUDIO` before voice recording starts
   - `CAMERA` before image capture
   - `READ_MEDIA_IMAGES` / `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` (API < 33) before file picker
   - `POST_NOTIFICATIONS` before first notification is issued
2. Add `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intent filters in `AndroidManifest.xml`
3. Handle incoming shared intent in `MainActivity.kt` → extract URI(s) → navigate to `NewChat` with pre-attached files
4. Add share button in `MessageBubble` → `ShareCompat.IntentBuilder` with message text
5. Add deep link intent filter `openwebui://chat/{chatId}` and handle in `NavGraph`

**Reference**
- All required permissions: `INTERNET`, `RECORD_AUDIO`, `CAMERA`, `READ_MEDIA_IMAGES`, `READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE` (API < 33), `POST_NOTIFICATIONS`; also declare `FileProvider` and `usesCleartextTraffic=true`
- Handle `ACTION_SEND` / `ACTION_SEND_MULTIPLE` in `MainActivity.onCreate` and `onNewIntent` by extracting `ClipData` URIs and navigating to `NewChat` with pre-attached files

---

### Sub-Task 22 — Architecture Hardening & Release Polish

**Status**: `[x] complete`

**Intent**
After all features are implemented in Sub-Tasks 1–21, audit the new project for consistency, completeness, and production readiness. This is a quality gate — no new features, only hardening.

**Expected Outcomes**
- No placeholder screen bodies remaining (every screen has real content)
- Every ViewModel exposes a `UiState` sealed class and `StateFlow<UiState>`
- Every repository suspend function returns `Result<T>`
- ProGuard/R8 rules complete for all added libraries
- All `strings.xml` entries populated (no hardcoded English strings in composables)
- Lint passes with zero new warnings
- App compiles cleanly in both `debug` and `release` build variants

**Todo List**
1. Audit all ViewModels across every sub-task: confirm `UiState` sealed class + `StateFlow<UiState>` pattern — fix any that use raw `MutableState` without a sealed class
2. Audit all `*RepositoryImpl` classes: confirm every suspend function returns `Result<T>` — wrap any missing try/catch in `runCatching {}`
3. Complete `proguard-rules.pro` rules for: `jlatexmath-android`, Apache POI (`poi-ooxml`), Jsoup, Kotlin scripting host, Markwon, kotlinx-serialization
4. Move all hardcoded English strings in composables to `res/values/strings.xml`
5. Run Android lint: fix all `UnusedResource`, `HardcodedText`, `MissingPermission`, and `Deprecation` warnings
6. Verify `release` build variant compiles and R8 minification does not strip scripting host classes (add keep rules if needed)
7. Perform a manual smoke-test checklist: onboarding → add provider → new chat → send message → receive streaming response → open sidebar → tap model → tap user menu → navigate to Workspace → navigate to Settings → navigate to Playground → navigate to Admin Panel → Android back returns to previous screen each time

---

## Feature Coverage Matrix

| Open WebUI Feature | Android Implementation | Source of Truth | Sub-Task |
|---|---|---|---|
| Multi-provider AI (Ollama, OpenAI, Anthropic, Gemini) | ModelRouter + provider Retrofit APIs | External API call | 2 |
| Custom provider (any OpenAI-compatible) | ProviderConfig with custom base URL | External API call | 2, 8 |
| Chat CRUD + streaming | ChatRepositoryImpl + ModelRouter | Room + API stream | 5, 9 |
| Slash commands (/model, /prompt, /skill) | Autocomplete overlay in MessageInput | Local Room | 9 |
| File attachment in chat | FileRepository + ImageCapture | App-private storage | 9 |
| Voice input (STT) | AudioRecorder + STTProcessor | Android STT / API | 16 |
| TTS playback | TTSPlayer | Android TTS / API | 16 |
| Image attach (camera/gallery) | ImageCapture (existing) | App-private storage | 9 |
| Thinking blocks `<think>` | Parsed from response, collapsible card | Local parse | 19 |
| RAG citations rendering | Source chips below message | Local Room | 9, 19 |
| Code blocks with syntax + copy | MarkdownTextEnhanced | Local render | 19 |
| LaTeX math rendering | jlatexmath-android (offline) | Local render | 19 |
| Mermaid diagrams | ArtifactWebView + CDN Mermaid.js | WebView + CDN | 18, 19 |
| HTML/JS artifact execution | ArtifactWebView (sandboxed) | WebView local exec | 18 |
| Python artifacts | Static code view + copy (no exec) | Local render | 18 |
| Chat folders | FolderRepository + Room | Room | 5, 9 |
| Chat tags | TagRepository + Room | Room | 5, 9 |
| Pin / archive chats | ChatRepository (existing) | Room | 5 |
| Temporary chat | ChatViewModel flag (not persisted) | Memory only | 9 |
| Share chat | Android ShareCompat (text/JSON export) | Local export | 9, 21 |
| Notes (CRUD, rich text) | NoteEditorScreen + NoteRepository | Room | 11 |
| Channels (local threads) | ChannelScreen + ChannelRepository | Room | 12 |
| Channel notifications | AppNotificationManager | Android Notifications | 20 |
| Knowledge / RAG (local vector) | KnowledgeRepository + cosine search | Room | 10 |
| Document upload (PDF, DOCX, TXT) | Text extraction + chunking + embedding | App-private + Room | 10 |
| Web URL ingestion | Jsoup on-device fetch + chunk + embed | On-device HTTP | 10 |
| YouTube transcript ingestion | YT subtitle API + chunk + embed | On-device HTTP | 10 |
| Models management | ModelRepository aggregating all providers | Provider APIs + Room | 5, 13 |
| Custom model configs | Local ModelEntity in Room | Room | 13 |
| Functions (PIPE/FILTER/ACTION) | FunctionExecutor (Kotlin scripting, on-device) | Room + local exec | 5, 13 |
| Tools | ToolRepository + local definitions | Room | 5, 13 |
| Skills (Python agents) | SkillRepository + ActionFunction scripting | Room + local exec | 5, 13 |
| Prompts library + variables | PromptRepository + version history | Room | 5, 13 |
| File manager | FileManagerScreen + FileRepository | App-private storage | 14 |
| Image generation | ImageRepository + provider API | External API call | 17 |
| Playground (API tester) | PlaygroundScreen + ModelRouter | External API call | 17 |
| Usage stats | AppManagementScreen (local counters) | Room aggregates | 15 |
| Global functions | GlobalFunctionsScreen (isGlobal flag) | Room | 15 |
| Provider health check | ProviderHealthScreen | External API ping | 15 |
| Settings: providers config | SettingsProvidersScreen | DataStore | 8 |
| Settings: RAG config | SettingsRAGScreen | DataStore | 8 |
| Settings: audio (STT/TTS) | SettingsAudioScreen | DataStore | 8 |
| Settings: appearance | AppearanceDetailScreen | DataStore | 8 |
| Settings: security (PIN/biometric) | SettingsSecurityScreen | DataStore | 8 |
| Settings: data management | DataManagementDetailScreen | Room + export | 8 |
| First-run onboarding | OnboardingScreen | DataStore | 7 |
| PIN / biometric app lock | PinLockScreen + BiometricPrompt | DataStore | 7 |
| User profile (name, avatar) | ProfileScreen | DataStore + storage | 7 |
| Receive shared files | ACTION_SEND intent filter | Android intent | 21 |
| Share message out | ShareCompat.IntentBuilder | Android intent | 21 |
| Deep links | NavDeepLink (openwebui://chat/{id}) | Android intent | 21 |
| Response complete notification | AppNotificationManager | Android Notifications | 20 |
| Export chats (JSON) | ExportImportManager (existing, kept) | Local file | 8 |

---

## UI Design Specification

> Derived from **live pixel-accurate inspection of Open WebUI v0.11.0** at 390×844px (mobile viewport, 2× device pixel ratio). Every screen, overlay, and component documented here is confirmed from actual running screenshots captured at `localhost:8085`. Use these specs as the **absolute ground truth** for the native Android implementation.

### Screenshot Reference Catalogue

All screenshots were captured at **390×844px** (mobile viewport, 2× DPR) from a live Open WebUI v0.11.0 instance (`localhost:8085`). They are stored in [`android-llm-webui/screenshots/`](android-llm-webui/screenshots/) relative to the workspace root. Use these as the **primary visual ground truth** when implementing any screen.

| Screenshot file | Screen / State | Key UI details visible |
|---|---|---|
| [`01-new-chat-clean.png`](android-llm-webui/screenshots/01-new-chat-clean.png) | **New Chat (Home)** | Dark `#171717` bg. Top-left: ☰ sidebar toggle. Top-right: dashed-circle temporary-chat icon + sliders/controls icon. Centre: OI logo (white square with "OI" text, ~40dp) + model name headline `Web search: gpt-6-astra` (~24sp bold). Rounded card input: "How can I help you today?" placeholder, toolbar: `+` / ◈ sparkles / `Web search: gpt-6...  ▾` chip (truncated, 6+ chars + `...` + `▾`) / 🎤 mic / ⚫ send FAB (white waveform icon on black circle). Section: `⚡ Suggested` label (small, grey, lightning icon). 3 suggestion rows (bold title + grey subtitle). Update banner bottom: dark navy bg. |
| [`03-sidebar-open.png`](android-llm-webui/screenshots/03-sidebar-open.png) | **Sidebar Drawer — open** | Full-width dark drawer. Header: `□` collapse icon (top-left) + `Open WebUI` title + sidebar-collapse icon. **No ☰ hamburger** in header — header has `□` left icon + title. Action rows (48dp): ✏ `New Chat` / 🔍 `Search`. **Models section** (grey label): rows of OI icon + model name (gpt-5.6-terra, models/gemini-3.6-flash, models/gemini-pro-latest). **Folders section** (grey label, `+` right). **Chats section** (grey label, `···` right): date groups Today/Yesterday/Previous 7 days/Previous 30 days/month — each chat row is **plain title text + relative time only (no emoji favicon)**. Footer: avatar circle + `User` + `●` green dot. |
| [`04-user-menu-popover.png`](android-llm-webui/screenshots/04-user-menu-popover.png) | **Sidebar + User Menu popover** | Popover appears above footer, dark card with rounded corners ~12dp. Top: avatar circle + `User` + green `● 1` (active session count). Divider. Menu items (each with icon + label): Update your status / Workspace / Calendar / Automations / Playground / Admin Panel / Settings / Sign Out. Items are 44dp tall rows with left icon. |
| [`05-settings-general.png`](android-llm-webui/screenshots/05-settings-general.png) | **Settings Modal — General tab** | Full-screen modal. Top: horizontally-scrollable tab bar: `← Back` (orange-outlined box with `<` chevron) + `⚙ General` (active, dark pill) + `≡ Interface` + `🔔 Notifications`. Content: **WebUI Settings** section header (bold, 17sp); `Theme` dropdown button (gear icon + `System` + `▾`); description: "Choose the colour theme used by the interface."; `Language` dropdown (`English (GB)` + `▾`); description line. **System Prompt** section header; multiline textarea "Enter system prompt here"; description "Set the default system prompt for new chats." **Advanced Parameters** section header; `Model parameters` row + `Show` value (right); description "Show or hide custom generation parameters." `Save` pill button fixed bottom-right. |
| [`06-settings-interface-top.png`](android-llm-webui/screenshots/06-settings-interface-top.png) | **Settings — Interface tab (top half)** | Tab bar: `← Back` + `Interface` (active) + `Notifications` + `Keyboard`. **Interface** heading (bold 17sp). **UI** section label (grey). Toggle rows with description text below each: `UI Scale` (Default value row) / `High Contrast Mode` (OFF) / `Display Chat Title in Tab` (ON) / `Allow User Location` (OFF) / `Haptic Feedback (Android)` (OFF) / `Copy Formatted Text` (OFF) / `Toast Notifications for New Updates` (ON) / `Show "What's New" Modal on Login` (ON). **Chat** section label. `Enable Message Queue` (ON) / `Chat Direction` (Auto value row) / `Landing Page Mode` (Default value row). `Save` pill fixed bottom-right. |
| [`settings-interface-scroll1.png`](android-llm-webui/screenshots/settings-interface-scroll1.png) | **Settings — Interface tab (bottom half, scrolled)** | Continuation: `Widescreen Mode` (ON) / `Temporary Chat by Default` (OFF) / `Fade Effect for Streaming Text` (ON) / `Render Markdown in User Messages` (OFF) / `Render Markdown in Assistant Messages` (ON) / `Title Auto-Generation` (ON) / `Follow-Up Auto-Generation` (ON) / **`Chat Tags Auto-Generation`** (ON) / **`Auto-Copy Response to Clipboard`** (OFF) / **`Response Auto-Scroll`** (ON) / **`Insert Suggestion Prompt to Input`** (OFF) / **`Keep Follow-Up Prompts in Chat`** (OFF) / **`Insert Follow-Up Prompt to Input`** (OFF). |
| [`09-settings-notifications.png`](android-llm-webui/screenshots/09-settings-notifications.png) | **Settings — Notifications tab** | Tab bar: `← Back` + `Notifications` (active) + `Keyboard` + `Connectio...`. `Browser Notifications` toggle (OFF) + description "Allow browser notifications for completed responses." `Notification Sound` toggle (ON). **Notification Targets** section header (bold) + `+` button right. Empty state: "No notification targets configured." No `Save` button visible (no unsaved state). |
| [`settings-keyboard.png`](android-llm-webui/screenshots/settings-keyboard.png) | **Settings — Keyboard tab** | Tab bar: `← Back` + `Keyboard` (active) + `Connections` + `Integratio...`. **Keyboard** heading + `Reset Defaults` button (top-right). `Enable Keyboard Shortcuts` toggle (ON) + description. Table with `Command` / `Key` column headers. **Chat** group label: New Chat (`⌘⇧O`) / New Temporary Chat (`⌘⇧'`) / Delete Chat (`⌘⇧⌫`) / Open Model Selector (`⌘⇧M`) / Toggle Dictation (`⌘⇧L`) / Navigate to Previous Chat (Unassigned) / Navigate to Next Chat (Unassigned). **Global** group: Search (`⌘K`) / Open Settings (`⌘.`) / Show Shortcuts (`⌘/`) / Toggle Sidebar (`⌘⇧S`) / Toggle Controls (Unassigned) / Close Modal (`Esc`). **Input** group: Focus Chat Input (`⇧Esc`) / Accept Autocomplete Generation (`Tab`) / Jump to Prompt Variable (shown). NOT a static list — shortcuts are interactive rows. |
| [`10-settings-connections.png`](android-llm-webui/screenshots/10-settings-connections.png) | **Settings — Connections tab** | Tab bar: `← Back` + `Connections` (active) + `Integrations` + `Persona...`. **Connections** heading. **Manage Direct Connections** section header. Grey text: "Connect to your own OpenAI compatible API endpoints." `+` button (right of section). "CORS must be properly configured by the provider to allow requests from Open WebUI." Empty state (no connections). `Save` pill fixed bottom-right. |
| [`admin-settings-connections.png`](android-llm-webui/screenshots/admin-settings-connections.png) | **Admin Settings — Connections tab** | Admin settings tab bar: `← Back` + `Connections` (active) + `Integrations` + `Persona...`. Same Manage Direct Connections layout as user Settings Connections. |
| [`14-workspace-models.png`](android-llm-webui/screenshots/14-workspace-models.png) | **Workspace — Models tab (with model)** | Tab bar: `□` sidebar icon (top-left) + `Models 1` (active) / `Knowledge 0` / `Prompts 0` / `Skills 0` / `Tools` (full word, not `S`) + `Create ▾` split-button (right). `Title` + `Updated at ▾` column headers. Search row: 🔍 `Search Models` + `All ▾` + `Actions ▾`. Model card row: OI icon + model name (`Web search: ...gpt...` truncated) + base model name below (`gpt-6-astra`) + `···` overflow + active toggle (ON). |
| [`workspace-create-model.png`](android-llm-webui/screenshots/workspace-create-model.png) | **Workspace — Create Model form (top)** | Full page, shares tab bar with Workspace. `← Back` text link (top-left below tabs). Header area: large OI icon (~72dp square) + `Model Name` editable heading + `Model ID` subheading + **`Access` button** (top-right, lock icon). Fields (top to bottom): `Base Model (From)` → dropdown "Select a base model (e.g. llama3, gpt-4o)" with `▾`; `Description` label + `Custom` right label + placeholder "Add a short description about what this model does"; `Add a tag...` row; **Model Params** section header; `System Prompt` subheader + large textarea (placeholder: "Write your model system prompt content here e.g.) You are Mario from Super Mario Bros, acting as an assistant."); `Advanced Params` row + `Show` right; `Prompts` row + `Default` right; `Knowledge` row + `Select Knowledge` + `Upload` buttons; `Tools` section (empty, instruction text); `Skills` section (empty, instruction text); `Filters` section + checklist (e.g. `✓ Cost Tracker For Open WebUI`); `Capabilities` section → `Vision` checkbox (checked). NO "Save & Create" button — form autosaves or uses Back navigation. |
| [`19-admin-users.png`](android-llm-webui/screenshots/19-admin-users.png) | **Admin Panel — Users tab** | Full-page layout (no bottom nav). Top tab bar: sidebar icon + `Users` / `Evaluations` / `Functions` / `Settings`. Sub-tabs: `Overview 1` / `Groups 0`. Search bar `🔍 Search` + `Add User` button (right). Table header: `Name` / `Role` / `Email` / `Last Active`. Row: circular avatar ("U..", ~28dp, grey bg) + green online dot + `Admin` (blue tappable role badge) + `admin@localhost` + "a few seconds ago". Info note: "ⓘ Click on the user role button to change a user's role." |
| [`admin-evaluations.png`](android-llm-webui/screenshots/admin-evaluations.png) | **Admin Panel — Evaluations tab** | Sub-tabs: `Leaderboard 206` / `Feedback 0`. Search bar: 🔍 `Search`. Table: `RK ▾` (rank col with sort) + `MODEL` col. 20+ model rows each with OI icon + model name (e.g. `OpenAI: gpt-5`, `gpt-4-turbo-2024-04-09`, etc.). Rank column shows `-` for all (no ratings yet). Update banner at bottom. |
| [`21-admin-functions.png`](android-llm-webui/screenshots/21-admin-functions.png) | **Admin Panel — Functions tab** | `Functions 3` + `Create ▾` **split-button** (Create text + `▾` chevron, top right). Search: `Search Functions` + `All ▾` + `Tag ▾`. Table header: `Title` / `Updated at ▾`. 3 function rows: type badge (`FILTER` or `PIPE`, dark grey pill) + name (truncated) + version + relative time + ⚙ settings + `···` menu + toggle. **♥ heart icon only on FILTER rows** (not PIPE). Each row has a **description text line** below the name (grey, truncated). |
| [`admin-settings-top.png`](android-llm-webui/screenshots/admin-settings-top.png) | **Admin Settings — General tab** | Top tab bar: `← Back` (blue outline box) + `⚙ General` (active, dark bg) + `🔒 Authentication` + `🔗 Connections` (more off-screen). Section **General**: `Version: v0.11.0 (latest)` / `See what's new` link / `Check for updates` button. Section **Help**: "Discover how to use Open WebUI and seek support from the community." + `Discord` / `X` / `GitHub` links. Section **Licence**: upgrade blurb. Section **Features** (toggle rows): `Community Sharing` (OFF, desc: "Allow users to share chats with the Open WebUI community.") / `Message Rating` (OFF) / `Folders` (ON) / **`Folder Max File Count`** text input (placeholder "Leave empty for unlimited") / `Memories` (ON) / `Memory System Context` (ON) / `Notes` (partially visible, OFF). `Save` pill button fixed bottom-right. |
| [`chat-thread-top.png`](android-llm-webui/screenshots/chat-thread-top.png) | **Chat Thread — assistant message (real data)** | Top bar: ☰ sidebar icon + chat title `Hanoi and Cat Ba Cruises` (truncated) + `···` menu + `⊕` new chat icon + sliders controls icon. Assistant message content: bold/italic markdown rendered, blockquote style for quoted text (left blue border line), bold hyperlink inline (`catbapanorama.com`). **RAG citation row**: circular model icons (overlapping, 3 shown) + `+3` + `6 Sources` — tappable to expand sources. **Action strip**: ✏ edit / 🗑 delete / 🔊 audio / ℹ info / ▶ play / ↻ regenerate / ↗ share — 7 small icon buttons in a row. **Follow up** section label + 5 greyed plain-text follow-up rows (full width, light grey text, truncated with `…`): each is a tappable row. Bottom input: card with `Se...` placeholder visible + `+` button + send FAB (waveform icon, large). |
| [`chat-controls-panel.png`](android-llm-webui/screenshots/chat-controls-panel.png) | **Chat Controls Panel — full screen** | Presented as a **full-screen overlay** (not a side panel). Top: `Controls` tab (active, white text) + `Overview` tab (grey) + `✕` close button (right). Sections with expand/collapse chevrons: **Valves** `▾` (collapsed) / **System Prompt** `▲` (expanded, shows textarea placeholder "Enter system prompt"). **Advanced Params** `▲` (expanded). Full parameter list as label + `Default` value rows: `Stream Chat Response` / `Stream Delta Chunk Size` / `Context Compaction Threshold` / `Function Calling` / `Reasoning Tags` / `Seed` / `Stop Sequence` / `Temperature` / `Reasoning Effort` / `logit_bias` / `max_tokens` / `top_k` / `top_p` / `min_p` / `frequency_penalty` / `presence_penalty` / `mirostat` / `mirostat_eta` / `mirostat_tau` / `repeat_last_n` (more below fold). |
| [`model-selector.png`](android-llm-webui/screenshots/model-selector.png) | **Model Selector — inline dropdown** | **NOT a bottom sheet** — appears as an inline dropdown anchored below the model chip in the input bar. Dark card (~`#1A1A1A`), slight border. Top row: 🔍 `Search a model` + `◈◈` icon (multi-select indicator) + `All ▾`. Model rows: OI icon (white square) + model name (truncated) + 2 icons per row: □ (clone/copy) + 🔗 (link). Special rows: `Arena Model` (ℹ info icon) / `Web search: gpt-6-astra` (🔗 link + ✓ checkmark = currently selected). Bottom: `Set as default` text link. |
| [`26-playground.png`](android-llm-webui/screenshots/26-playground.png) | **Playground screen** | Top: ☰ sidebar icon + `Chat` tab (active) + `Completions` tab + `Images` tab. Section: **System Instructions** header + ✏ edit pencil icon + `···` menu (right). Large empty chat response area. Bottom input: `Enter a user message here` text (placeholder style, not a card). Bottom toolbar partially visible: model selector + send button. |
| [`admin-settings-connections.png`](android-llm-webui/screenshots/admin-settings-connections.png) | **Admin Settings — Connections tab** | Confirms Admin Settings tab bar continues to the right: `← Back` + `Connections` (active) + `Integrations` + `Persona...` (Personalization). Content mirrors user Settings Connections tab. |

---

**Critical UI observations confirmed from live screenshots** (pixel-accurate, not estimated):

1. **New Chat model display**: The model name is shown as a **large H2-style headline** (not a subheader) next to the OI logo. Current model was `Web search: gpt-6-astra` — the full model name including capability prefix appears in the headline, not just a short name.
2. **Input bar toolbar exact order** (left to right): `+` (attach) → `◈` (sparkles/tools, a 4-diamond icon) → `[model name truncated...  ▾]` chip → 🎤 mic → ⚫ send FAB. The FAB shows a waveform icon (not an arrow) when idle.
3. **Suggestions label is `⚡ Suggested`** (lightning bolt icon, not a star or arrow) — white/light text on dark.
4. **Sidebar chat rows**: **Plain title text only** — NO emoji favicon, no icon. Title single-line truncated. Relative time right-aligned grey. (The earlier playwright screenshots showed favicons, but the fresh live screenshot `03-sidebar-open.png` shows plain text — the live screenshot is ground truth.)
5. **Model selector is an INLINE DROPDOWN** (not a bottom sheet) — appears anchored directly below the model chip, scrollable list, dark card style. Each model row has clone (□) and link (🔗) icons. `Set as default` at the bottom.
6. **Chat action strip is 7 icons**: ✏ edit / 🗑 delete / 🔊 audio / ℹ info / ▶ play / ↻ regenerate / ↗ share — all small outlined icon buttons in a horizontal row, no labels.
7. **RAG citations row format**: overlapping circular model/source avatars (up to 3 shown) + `+N` overflow count + `N Sources` text — tappable to expand source list. Appears ABOVE the action strip.
8. **Follow up section**: Labelled `Follow up` (plain text, no icon). Below that: plain grey text rows (full width, truncated with `…`), NOT pill/chip styled. Each row is individually tappable.
9. **Controls Panel is FULL-SCREEN** (not a side panel) — presented as a full-screen overlay with its own tabs (`Controls | Overview`) and close button. Contains: Valves (collapsed) / System Prompt (textarea) / Advanced Params with 20+ parameters shown as label + `Default` value pairs.
10. **Advanced Params in Controls Panel** include (exact names from screenshot): `Stream Chat Response`, `Stream Delta Chunk Size`, `Context Compaction Threshold`, `Function Calling`, `Reasoning Tags`, `Seed`, `Stop Sequence`, `Temperature`, `Reasoning Effort`, `logit_bias`, `max_tokens`, `top_k`, `top_p`, `min_p`, `frequency_penalty`, `presence_penalty`, `mirostat`, `mirostat_eta`, `mirostat_tau`, `repeat_last_n`.
11. **Playground has 3 tabs**: `Chat | Completions | Images`. The Chat tab shows a System Instructions section (editable with pencil icon) + large empty chat area + `Enter a user message here` input + model selector in bottom bar.
12. **Admin Evaluations Leaderboard** shows 206 model entries with columns `RK ▾` (rank, sortable) + `MODEL`. Each row has OI icon + full model ID string. Sub-tabs: `Leaderboard N` + `Feedback N`.
13. **Admin Functions list**: Each row has a type badge (`FILTER` or `PIPE`, uppercase pill), name, version, timestamp, ♥ favourite, ⚙ settings, `···` overflow, and an ON/OFF toggle. `Create ▾` dropdown button top-right.
14. **Admin Settings General Features list** (exact from screenshot, some toggled ON in this instance): `Community Sharing` OFF / `Message Rating` OFF / `Folders` **ON** / `Folder Max File Count` (text input) / `Memories` **ON** / `Memory System Context` **ON** / `Notes` OFF (partially visible).
15. **Admin Settings tab order** (scrollable): `← Back` | `⚙ General` | `🔒 Authentication` | `🔗 Connections` | `Integrations` | `Personalization` | (more off-screen).
16. **Settings modal tab order** (scrollable): `← Back` | `⚙ General` | `≡ Interface` | `🔔 Notifications` | `Keyboard` | `🔗 Connections` | `Integrations` | `Personalization` | `Audio` | `Data Controls`.
17. **Workspace tab counts are visible**: e.g. `Models 1` (1 model configured) — confirms count badges on each workspace tab. Tabs show full text: **`Models N` / `Knowledge N` / `Prompts N` / `Skills N` / `Tools`** — NOT truncated to `S` for Skills. `Create ▾` is a split-button at the right.
18. **Workspace Skills / Tools tabs** use full words in the tab bar (not truncated). Empty state: "No skills found / Try adjusting your search or filter to find what you are looking for."
19. **Notes and Channels are disabled** (404) in this instance because Admin Settings → Features → Notes = OFF, Channels = OFF. The Android app must handle these as feature toggles that show/hide those tabs.
20. **Blockquote rendering**: Text inside `""` with a leading blue/accent left-border vertical line — visible in the chat thread screenshot with the hotel message.
21. **Settings Interface tab has 6 additional toggles** not previously documented (confirmed from `settings-interface-scroll1.png`): `Chat Tags Auto-Generation` (ON) / `Auto-Copy Response to Clipboard` (OFF) / `Response Auto-Scroll` (ON) / `Insert Suggestion Prompt to Input` (OFF) / `Keep Follow-Up Prompts in Chat` (OFF) / `Insert Follow-Up Prompt to Input` (OFF).
22. **Settings Keyboard tab** is NOT a static reference list. It has `Enable Keyboard Shortcuts` toggle + `Reset Defaults` button + interactive shortcut assignment rows grouped by Chat / Global / Input sections. Each row shows Command + current key binding.
23. **Settings Data Controls** tab bar continues to: `Usage` + `Archived Ch...` (Archived Chats) tabs beyond Data Controls. Content: Chats section (Import / Export / Shared Chats Manage / Archive All / Delete All) + Files section (Manage Files → file manager).
24. **Settings General** — `Theme` and `Language` are rendered as dropdown buttons (not plain value rows). Each field has a description text line below it. Section and field labels use normal-case (not ALL-CAPS).
25. **Create Model form** is completely different from prior spec: shares workspace tab bar, uses `← Back` text link not back arrow, has OI icon + `Model Name` + `Model ID` header with `Access` lock button. Sections include: Base Model / Description / Tags / System Prompt / Advanced Params / Prompts / Knowledge / Tools / Skills / Filters (checklist of filter functions) / Capabilities checkboxes. No "Save & Create" button visible — navigation-based saving.
26. **Admin Functions** — `♥ heart` icon only appears on FILTER-type function rows, not PIPE rows. Each function row shows a description text line below the name.
27. **Settings each toggle row has description text** below the label explaining what the setting does (confirmed across all settings tabs).

---

### Design Language

**Colour System**
- Background (light): `#FFFFFF` surface, `#F4F4F4` secondary surface
- Background (dark): `#171717` primary, `#1E1E1E` surface, `#2A2A2A` raised card
- Background (OLED dark): `#000000` primary
- Background (Her theme): `#983724` brand accent
- Text primary (light): `#0D0D0D`
- Text secondary: `#6B7280` (gray-500)
- Text tertiary/placeholder: `#9CA3AF`
- Accent / interactive: `#000000` (light) / `#FFFFFF` (dark) — high contrast buttons
- Destructive: `#EF4444`
- Online indicator: `#22C55E` (green dot)
- Toggle on: `#1C1C1E` (dark pill)
- Border / divider: `#E5E7EB` (light) / `#2D2D2D` (dark)

**Typography**
- Font: System default (San Francisco on iOS parity → Roboto on Android)
- Screen title: 17sp semibold
- Section header: 13sp semibold, ALL CAPS, `#6B7280`
- Body: 15sp regular
- Caption / subtitle: 13sp regular, `#6B7280`
- Code blocks: monospace 13sp
- Message text: 15sp regular, `lineHeight = 24sp`

**Shape**
- Cards / modals: `cornerRadius = 16dp`
- Input field: `cornerRadius = 14dp`
- Chip / pill: `cornerRadius = 999dp` (fully rounded)
- Message bubble: `cornerRadius = 18dp` (12dp for continuation bubbles)
- Icon buttons: `cornerRadius = 8dp`
- Send FAB: `cornerRadius = 999dp`, `size = 44dp`

**Spacing**
- Screen horizontal padding: `16dp`
- Card internal padding: `16dp`
- List item padding: `12dp` vertical, `16dp` horizontal
- Section gap: `24dp`
- Element gap within card: `8dp`

**Elevation / Shadow**
- Modals / bottom sheets: `elevation = 24dp` with dim scrim (`#00000066`)
- Navigation bar: subtle top divider line only, no shadow
- Input bar: `elevation = 4dp` with light shadow

---

### Screen Inventory & Layout Specs

---

#### 1. Home / New Chat Screen

**Route**: `/` (new chat)
**Screenshot**: [`01-new-chat-clean.png`](android-llm-webui/screenshots/01-new-chat-clean.png)

**Layout** (390×844px, live confirmed):
```
TopBar (56dp tall):
  [☰ SidebarToggle — left]    [○ DashedCircle — right-1]  [⊟ SlidersIcon — right-2]

Body (centred vertically in remaining space):
  [OI Logo — white square ~40dp]  [Model Name — ~22sp bold, single line]  ← centred row

  InputCard (rounded ~14dp, very subtle shadow, ~`#262626` bg):
    [multiline text input]
    placeholder: "How can I help you today?"
    ── bottom toolbar row ─────────────────────────────────
    [+]  [◈ sparkles]    [model name truncated...  ▾]  [🎤]  [⚫ SendFAB]
    ←──── left group ────────────────────────────────────────────────────►

⚡ Suggested   ← label row (small, grey text + lightning icon)
  [Suggestion 1 title bold]
  [suggestion 1 subtitle grey]
  ─────────────────────────
  [Suggestion 2 title bold]
  [suggestion 2 subtitle grey]
  ─────────────────────────
  [Suggestion 3 title bold]
  [suggestion 3 subtitle grey]
```

**Key Components** (confirmed from screenshot):
- `TopBar`: ☰ opens sidebar drawer; right side: dashed-circle (temporary-chat toggle) + sliders icon (controls/params)
- `ModelSelector chip`: shows full model name truncated with `...` + ` ▾`. Tapping opens **inline dropdown** (NOT bottom sheet)
- `+ icon`: opens attachment picker
- `◈ sparkles icon`: 4-diamond pattern icon (not a star ✦) — opens Tools picker
- `SendFAB`: solid black circle, ~44dp, shows **waveform bars** icon when idle (not an arrow)
- `⚡ Suggested` label: uses a lightning bolt icon (⚡), not a star or sparkle
- Suggestions: 3 rows max; each row is **bold title** + **grey subtitle** on next line; tapping fills input

---

#### 2. Sidebar Drawer

**Trigger**: tap ☰ icon in TopBar (accessible from every screen)
**Width**: full width (390dp), slides in from left
**Screenshot**: [`03-sidebar-open.png`](android-llm-webui/screenshots/03-sidebar-open.png) — the **primary navigation surface**, no bottom nav bar exists

**Layout** (confirmed pixel-accurate from live screenshot):
```
Header (56dp):
  [OI Logo ~24dp square]  "Open WebUI" (~17sp bold)      [□ collapse icon — right]

──── Action Rows (48dp each) ──────────────────────────────────────────────────────
  [✏ pencil icon]  New Chat
  [🔍 search icon]  Search

──── Models  ──────────────────────────────────────────────────────────────────────
  (section label "Models" — grey 12sp)
  [OI icon]  gpt-5.6-terra
  [OI icon]  models/gemini-3.6-flash
  [OI icon]  models/gemini-pro-latest
  (each row is tappable — opens New Chat with that model pre-selected)

──── Folders ───────────────────────────────────────── [+ button right] ───────────
  (section label "Folders" — grey 12sp)
  (empty if no folders; shows folder rows when created)

──── Chats ─────────────────────────────────────────── [··· button right] ─────────
  (section label "Chats" — grey 12sp)

  Today  (group label — grey, smaller)
    Hanoi and Cat Ba Cruises                                              1h
    (title is plain text, no emoji favicon — confirmed from screenshot)

  Yesterday
    Hanoi Budget Accommodation                                           19h
    Vietnam Travel Forex Cards                                           22h

  Previous 7 days
    Vietnam Day Cruise Comparis...                                        1d
    Vietnam Bay Cruise Comparis...                                        1d
    Debugging Missing Email                                               2d
    ...

  Previous 30 days
    Quick Delhi Attractions                                               1w
    ...

  [Month name, e.g. "June"]
    ...

  (lazy-loaded, scroll to see older chats)

──── Footer (fixed bottom, 56dp) ─────────────────────────────────────────────────
  [Avatar circle ~28dp, grey bg, "U" initials]  User  [● green online dot]
  ← tap → User Menu popover
```

**Key sidebar observations** (from live screenshot `03-sidebar-open.png`):
- Chat rows have **no emoji favicons** — plain title text + relative time only
- **"Automations" is NOT an action row** in the sidebar — it only appears in the User Menu popover
- The sidebar has a **Search bar at the top** (separate from the main action rows, shown as `🔍 Search`)
- The **Models section** is always visible above Folders/Chats — it shows all configured AI models
- **Date group labels** ("Today", "Yesterday", "Previous 7 days", etc.) are lowercase, not all-caps
- The collapse icon (top-right of header) is a `□` toggle-sidebar icon (not a `←` arrow)

**User Menu Popover** (appears above footer on tap, dark card ~12dp radius):
```
╔═══════════════════════════════════════════╗
║  [Avatar ~32dp]  User              ● 1   ║  ← green dot + active session count
╠═══════════════════════════════════════════╣
║  😊  Update your status                  ║
║  ⊞   Workspace        → WorkspaceScreen  ║
║  📅  Calendar         → CalendarScreen   ║
║  ⏱   Automations      → AutomationsScreen║
║  </>  Playground      → PlaygroundScreen ║
║  👤  Admin Panel      → AdminPanelScreen ║
║  ⚙   Settings         → SettingsScreen  ║
║  ⎋   Sign Out                            ║
╚═══════════════════════════════════════════╝
```
*(Each row ~48dp, left icon + label text, tapping closes popover and pushes screen)*
*(Notes and Channels do NOT appear here — they are accessed via Settings/Admin features when enabled)*

---

#### 3. Chat Thread Screen

**Route**: `/c/{chatId}`
**Screenshot**: [`chat-thread-top.png`](android-llm-webui/screenshots/chat-thread-top.png)

**TopBar** (56dp):
```
[☰]  [Chat Title truncated]  [··· overflow]  [⊕ new chat]  [⊟ controls sliders]
```

**Body**:
```
ScrollArea (grows to fill space above input bar):
  Message list (chronological, newest at bottom)
  
  User Message Bubble:
    Right-aligned, dark background (~`#2A2A2A` or `#1C1C1E`)
    Text white/light 15sp, line-height 24sp
    Max width 80% of screen, cornerRadius 18dp
    (No action strip by default — appears on tap)
  
  Assistant Message Bubble:
    Left-aligned, NO background (transparent), full width
    Content: Markdown rendered (bold, italic, inline code, headers, lists)
    Blockquotes: left blue/accent border line, indented text
    Hyperlinks: bold + underline inline
    
    BELOW message content:
    [RAG Citation row]:
      Overlapping circular source icons (3 shown) + "+N" + "N Sources" text
      ← tappable to expand source list, appears ABOVE action strip
    
    [Action strip] — 7 icon buttons, horizontal, small outlined icons:
      [✏ edit]  [🗑 delete]  [🔊 audio]  [ℹ info]  [▶ play]  [↻ regenerate]  [↗ share]
    
    [Follow up] — plain text section label (no icon)
    [Greyed suggestion row 1 — full width, grey text, truncated with …]
    [Greyed suggestion row 2]
    [Greyed suggestion row 3]
    [Greyed suggestion row 4]
    [Greyed suggestion row 5]
    ← each row is individually tappable, populates input field
  
InputBar (fixed bottom, ~56dp min height):
  [rounded card ~14dp, `#1F1F1F` bg, subtle shadow]:
    placeholder: "Send a Message"
    ── toolbar row ──
    [+]  [◈ tools]  [model chip ▾ (may show "+1" for multi-model)]  [🎤]  [⚫ SendFAB]
```

**Controls Panel** (FULL-SCREEN overlay — NOT a side panel):
```
Top: [Controls tab]  [Overview tab]  [✕ close — right]

[Valves]            ▾   ← collapsed section
[System Prompt]     ▲   ← expanded, shows textarea "Enter system prompt"

[Advanced Params]   ▲   ← expanded, label + "Default" value for each:
  Stream Chat Response          Default
  Stream Delta Chunk Size       Default
  Context Compaction Threshold  Default
  Function Calling              Default
  Reasoning Tags                Default
  Seed                          Default
  Stop Sequence                 Default
  Temperature                   Default
  Reasoning Effort              Default
  logit_bias                    Default
  max_tokens                    Default
  top_k                         Default
  top_p                         Default
  min_p                         Default
  frequency_penalty             Default
  presence_penalty              Default
  mirostat                      Default
  mirostat_eta                  Default
  mirostat_tau                  Default
  repeat_last_n                 Default
  [more params below fold...]
```

---

#### 4. Settings Modal

**Trigger**: User Menu → Settings
**Presentation**: Full-screen overlay (covers entire screen, NOT a bottom sheet)
**Screenshot**: [`05-settings-general.png`](android-llm-webui/screenshots/05-settings-general.png)

**Tab bar** (horizontally scrollable, at top, ~48dp):
```
[← Back]  [⚙ General]  [≡ Interface]  [🔔 Notifications]  [⌨ Keyboard]
[🔗 Connections]  [Integrations]  [Personalization]  [Audio]  [Data Controls]
```
*(active tab has filled dark background pill; `← Back` has chevron icon and blue outline)*

**Tab 1: General**
```
WebUI Settings:  ← section header (grey, 12sp, caps)
  Theme:         [System]   ← tappable value row + chevron
  Language:      [English (GB)]

System Prompt:  ← section header
  [multiline textarea, placeholder "Enter system prompt here"]
  
Advanced Parameters:  ← section header
  Model parameters    [Show]   ← toggle row
```

**Tab 2: Interface** (each row has description text below label)
```
Interface  ← page heading (bold 17sp)

UI:  ← section label (grey)
  UI Scale:                        [Default]
  High Contrast Mode:              [toggle OFF]
  Display Chat Title in Tab:       [toggle ON]
  Allow User Location:             [toggle OFF]
  Haptic Feedback (Android):       [toggle OFF]
  Copy Formatted Text:             [toggle OFF]
  Toast Notifications for New Updates: [toggle ON]
  Show "What's New" Modal on Login: [toggle ON]

Chat:  ← section label (grey)
  Enable Message Queue:            [toggle ON]
  Chat Direction:                  [Auto]
  Landing Page Mode:               [Default]
  Chat Background Image:           [Upload button]
  Chat Bubble UI:                  [toggle ON]
  Display Username Instead of "You": [toggle OFF]
  Widescreen Mode:                 [toggle ON]
  Temporary Chat by Default:       [toggle OFF]
  Fade Effect for Streaming Text:  [toggle ON]
  Render Markdown in User Messages: [toggle OFF]
  Render Markdown in Assistant Messages: [toggle ON]
  Title Auto-Generation:           [toggle ON]
  Follow-Up Auto-Generation:       [toggle ON]
  Chat Tags Auto-Generation:       [toggle ON]
  Auto-Copy Response to Clipboard: [toggle OFF]
  Response Auto-Scroll:            [toggle ON]
  Insert Suggestion Prompt to Input: [toggle OFF]
  Keep Follow-Up Prompts in Chat:  [toggle OFF]
  Insert Follow-Up Prompt to Input: [toggle OFF]
```

**Tab 3: Notifications**
```
Browser Notifications:   [toggle OFF]   "Allow browser notifications for completed responses."
Notification Sound:      [toggle ON]

Notification Targets:  ← section header (bold)
  [+ add button (top right)]
  "No notification targets configured."
```

**Tab 4: Keyboard** (interactive — NOT a static list)
```
Keyboard                                              [Reset Defaults]
Enable Keyboard Shortcuts:  [toggle ON]

Command                            Key
Chat  ← group label
  New Chat                         ⌘⇧O
  New Temporary Chat               ⌘⇧'
  Delete Chat                      ⌘⇧⌫
  Open Model Selector              ⌘⇧M
  Toggle Dictation                 ⌘⇧L
  Navigate to Previous Chat        Unassigned
  Navigate to Next Chat            Unassigned

Global  ← group label
  Search                           ⌘K
  Open Settings                    ⌘.
  Show Shortcuts                   ⌘/
  Toggle Sidebar                   ⌘⇧S
  Toggle Controls                  Unassigned
  Close Modal                      Esc

Input  ← group label
  Focus Chat Input                 ⇧Esc
  Accept Autocomplete Generation   Tab
  Jump to Prompt Variable          (key shown)
```

**Tab 5: Connections**
```
Manage Direct Connections:  ← section header
  [+ button (top right)]
  "Connect to your own OpenAI compatible API endpoints."  ← grey subtext
  "CORS must be properly configured by the provider to allow requests from Open WebUI."
  [empty state — no connections yet]
```

**Tab 6: Integrations**
**Tab 7: Personalization** (confirmed spelling — NOT "Personalisation")
**Tab 8: Audio**
**Tab 9: Data Controls**
```
Data Controls  ← page heading

Chats:  ← section header
  Import Chats          [Import]     "Import chat history from a JSON export file."
  Export Chats          [Export]     "Download your chat history as a JSON export."
  Shared Chats          [Manage]     "Review and manage chats you have shared."
  Archive All Chats     [Archive All]  "Move every chat into the archive after confirmation."
  Delete All Chats      [Delete All]   "Permanently delete every chat after confirmation."

Files:  ← section header
  Manage Files          [Manage]     "Open the file manager for uploaded files."
```
Tab bar continues past Data Controls: `Usage` + `Archived Chats` (partially visible)

**`Save` button**: pill-shaped white/light button, fixed bottom-right corner of modal (not shown on Keyboard or Notifications tabs)

---

#### 5. Admin Panel

**Route**: `/admin` → redirects to `/admin/users/overview`
**Screenshot**: [`19-admin-users.png`](android-llm-webui/screenshots/19-admin-users.png)

**TopBar** (full-page layout — no bottom nav visible):
```
[☰ sidebar icon]  Users  Evaluations  Functions  Settings   ← tab bar (horizontal)
```

**Admin > Users tab**:
```
Sub-tabs: Overview [1]    Groups [0]

[🔍 Search bar]                              [Add User button]

Table:
  Name          Role              Email               Last Active
  ─────────────────────────────────────────────────────────────────
  [Avatar "U.." ~28dp]
  + [● green online dot]
  + [Admin] (blue badge, tappable to change role)   admin@localhost   a few seconds ago

Info note: "ⓘ Click on the user role button to change a user's role."
```

**Admin > Evaluations tab**:
```
Sub-tabs: Leaderboard [206]    Feedback [0]

[🔍 Search bar]

Table:
  RK ▾   MODEL
  ─────────────────────
  -      [OI icon] OpenAI: gpt-5
  -      [OI icon] OpenAI: gpt-5-pro
  -      [OI icon] gpt-4-turbo-2024-04-09
  -      [OI icon] gpt-realtime-2.1-mini
  ... (206 rows, paginated, all showing "-" rank until rated)
```

**Admin > Functions tab**:
```
Functions 3    [Create ▾ button — top right pill]

[🔍 Search Functions]        [All ▾]  [Tag ▾]

Table:
  Title                                    Updated at ▾
  ─────────────────────────────────────────────────────────────────────
  [FILTER]  Cost Tra...  v0.3.1  2 days ago   [♥] [⚙] [···]  [toggle OFF]
  [PIPE]    O3 Pro O1 Pro Support  4 days ago   [⚙] [···]  [toggle ON]
  [PIPE]    OpenAI imag...  v0.1.1  5 months ago [⚙] [···]  [toggle OFF]
```
*(FILTER / PIPE are uppercase pill badges in grey-outlined style)*

**Admin > Settings tab** (route: `/admin/settings`):
```
Top tab bar: [← Back]  [⚙ General]  [🔒 Authentication]  [🔗 Connections]  [Integrations]  [Personalization]  ...

General tab:
  Version: v0.11.0 (latest)     [Check for updates]
  See what's new (link)
  
  Help: "Discover how to use Open WebUI..."
    [Documentation]  Discord  X  GitHub  (links)
  
  Licence: upgrade section
  
  Features:  ← section header (grey)
    Community Sharing         [toggle OFF]
    "Allow users to share chats with the Open WebUI community."
    Message Rating            [toggle OFF]
    "Let users rate assistant responses."
    Folders                   [toggle ON]
    "Allow users to organise chats into folders."
    Folder Max File Count     [text input "Leave empty for unlimited"]
    "Maximum number of files allowed per folder."
    Memories                  [toggle ON]
    "Allow users to save memories for more personalised responses."
    Memory System Context     [toggle ON]
    "Include saved memories in the system context."
    Notes                     [toggle OFF (partially visible)]
    ...
```

---

#### 6. Workspace

**Route**: `/workspace/models` (and `/knowledge`, `/prompts`, `/tools`, `/skills`)
**Screenshot**: [`14-workspace-models.png`](android-llm-webui/screenshots/14-workspace-models.png), [`workspace-skills.png`](android-llm-webui/screenshots/workspace-skills.png)

**Tab bar** (`□` sidebar icon top-left, full-width, horizontally scrollable):
```
[□]  [Models N]  [Knowledge N]  [Prompts N]  [Skills N]  [Tools]     [Create ▾]
                                                                  ← split-button right
```
*(Count badge N shown on each tab, e.g. `Models 1`; all tab labels use full words — NOT truncated)*
*(Column headers: `Title` left + `Updated at ▾` right)*

**Each tab body**:
```
Search row:
  [🔍 Search {Models/Knowledge/Prompts/Tools/Skills}]   [All ▾]  [Actions ▾ (Models only)]

Knowledge tab also has: [All Sources ▾]

Empty state (centred):
  "No {type} found"
  "Try adjusting your search or filter to find what you are looking for."

Model card row (when items exist):
  [OI icon]  model name (truncated)     ···      [toggle ON/OFF]
             base model name (grey, below)
```

**Create dropdown options** (from `Create ▾` split-button):
- Models: "Create model"
- Knowledge: "Create knowledge" / "Upload files" / "Connect website" / "YouTube"
- Prompts: "Create prompt"
- Skills: "Create skill"
- Tools: "Create tool"

**Create Model form** (`/workspace/models/create`):
```
[← Back]  ← text link (top-left, below workspace tab bar)

[OI icon ~72dp]  Model Name  (editable heading)         [Access 🔒]
                 Model ID    (editable subheading)

Base Model (From):
  [Select a base model (e.g. llama3, gpt-4o)  ▾]

Description                                              [Custom]
  "Add a short description about what this model does"

[Add a tag...]

Model Params  ← section header
System Prompt  ← subsection
  [large textarea: "Write your model system prompt content here
   e.g.) You are Mario from Super Mario Bros, acting as an assistant."]

Advanced Params                                          [Show]
Prompts                                                  [Default]

Knowledge    [Select Knowledge]  [Upload]
  "To attach knowledge base here, add them to the Knowledge workspace first."
Tools
  "To select toolkits here, add them to the Tools workspace or enable a tool server first."
Skills
  "To select skills here, add them to the Skills workspace first."

Filters  ← section header
  [✓ checkbox]  Cost Tracker For Open WebUI
  "To select filters here, add them to the Functions workspace first."

Capabilities  ← section header
  [✓] Vision
  [ ] Web Search
  [ ] Image Generation
```
*(No bottom Save/Create button — form saved via navigation back or autosave)*

---

#### 7. Playground Screen

**Route**: `/playground`
**Screenshot**: [`26-playground.png`](android-llm-webui/screenshots/26-playground.png)

**Tab bar** (full-page, no bottom nav):
```
[☰ sidebar icon]  Chat (active)  Completions  Images
```

**Chat tab**:
```
[System Instructions]  [✏ edit icon]          [··· overflow]
← section header row ─────────────────────────────────────────

[large empty chat area — grows to fill screen]

[Enter a user message here]  ← flat input (not a card)

Bottom toolbar:
  [model selector chip]  ...  [Send button]
```

**Completions tab**: API-style text completion interface (like OpenAI Playground)
**Images tab**: Image generation interface

---

#### 8. Auth / Sign-In Screen

**Route**: `/auth`
*(Inferred from codebase — not directly captured as app was already logged in)*

**Layout** (full screen, centred):
```
[Open WebUI Logo — centred]

Form:
  Email:    [text field]
  Password: [password field]
  [Sign in button — full width, black]

OR:
  [Sign in with Google]
  [Sign in with GitHub]
  [Sign in with Microsoft]

Footer: "Don't have an account? Sign up"
```

---

### Component Library (Reusable Compose Composables)

The following reusable components are derived **directly from live screenshots**:

#### `TopBarScaffold`
- Height: 56dp
- Left slot: icon button (sidebar toggle ☰ or back arrow ←)
- Centre slot: title text (truncated single line, 17sp semibold) — left-aligned after icon
- Right slot: up to 3 icon buttons (e.g. `···` overflow + `⊕` new chat + `⊟` sliders)
- Background: matches scaffold background (no elevation line for chat, divider only for admin)

#### `SidebarDrawer`
- Full-width on mobile (390dp), `ModalNavigationDrawer`, slides from left edge
- Opens via ☰ tap (any TopBar) or swipe-right from left edge
- Closes via `□` collapse tap, scrim tap, or swipe-left
- Animation: 300ms ease-out slide; scrim `#00000080` behind drawer
- **This is the sole navigation surface — no bottom nav bar exists**
- Internal structure (top → bottom, scrollable body):
  1. **Header** (56dp, fixed): OI logo + "Open WebUI" title + `□` collapse icon
  2. **New Chat row** (48dp): ✏ pencil icon + "New Chat" label
  3. **Search row** (48dp): 🔍 icon + "Search" label
  4. **Models section**: grey 12sp section label + list of configured model rows (OI icon + model name); tapping a model starts a new chat with it pre-selected
  5. **Folders section**: grey 12sp section label + `+` button (right) + folder rows when created
  6. **Chats section**: grey 12sp section label + `···` overflow button (right) + date-group headers + chat rows
  7. **Chat rows**: plain title text (truncated, no emoji) + relative time (right-aligned, grey); **no emoji favicons**
  8. **Date group headers**: "Today" / "Yesterday" / "Previous 7 days" / "Previous 30 days" / month name (e.g. "June") — lowercase, grey, 12sp
  9. **Footer** (56dp, fixed): avatar circle (28dp, grey bg, initials) + "User" label + green online dot `●`; tap → `UserMenuPopover`

#### `MessageInputBar`
- Fixed to bottom of scaffold, above system nav bar inset
- Rounded card ~14dp, `#1F1F1F` bg (dark), subtle shadow
- Multiline `BasicTextField` (grows up to ~4 lines, then inner-scrolls)
- Placeholder: "How can I help you today?" (home) or "Send a Message" (chat)
- Toolbar row (left to right): `+` attach → `◈` tools → model chip → 🎤 mic → send FAB

#### `MessageBubble(role)`
- **User**: right-aligned, ~`#2A2A2A` background, white text, `cornerRadius = 18dp`, max-width 80%
- **Assistant**: left-aligned, transparent (no background), full-width, plain text colour
- Long-press → 7-icon action strip (✏ 🗑 🔊 ℹ ▶ ↻ ↗)
- RAG citations row appears ABOVE the action strip: overlapping circular avatars + "+N" + "N Sources"
- Follow-up section BELOW action strip: "Follow up" label + 5 plain grey tappable text rows

#### `ModelSelectorDropdown`
- **Inline dropdown anchored below model chip** (NOT a full bottom sheet)
- Dark card ~`#1A1A1A`, slight border radius, scrollable list
- Top: 🔍 search field + multi-select `◈◈` icon + `All ▾` filter
- Each model row: OI icon + name (truncated) + □ clone icon + 🔗 link icon
- Special rows: `Arena Model` (ℹ) + any active Web Search model
- Currently selected model shows ✓ checkmark (right-aligned)
- Bottom: `Set as default` text action

#### `ControlsPanel`
- **Full-screen overlay** (NOT a side panel drawer)
- Top: `Controls` tab + `Overview` tab (horizontal) + `✕` close button
- Sections: Valves (collapsible ▾) / System Prompt (collapsible, textarea) / Advanced Params (collapsible, 20+ rows)
- Each Advanced Param row: `param_name` (left) + `Default` (right, grey)
- No sliders — parameters are tapped to enter a specific value

#### `SettingsModal`
- Full-screen overlay
- Horizontally scrollable tab bar at top: `← Back` | `⚙ General` | `≡ Interface` | `🔔 Notifications` | `⌨ Keyboard` | `🔗 Connections` | `Integrations` | `Personalization` | `Audio` | `Data Controls`
- `← Back` styled as first tab item with `<` chevron icon + blue outline box
- Active tab: dark filled pill/badge
- Content area: vertically scrollable
- `Save` button: pill-shaped white button, fixed bottom-right corner

#### `ToggleRow`
- Label (15sp, white/light) + optional description line (13sp, grey)
- `Switch` composable on right
- Min height: 48dp (taller if description present)
- Section is grouped under grey ALL-CAPS section header

#### `ValueRow`
- Label on left, current value text (grey) on right
- Tappable → opens picker sheet or inline expand
- Same height as `ToggleRow` (48dp min)

#### `SectionHeader`
- 12sp, `#6B7280` grey, text (not all-caps in all places — check design)
- 8dp padding below, 16dp left padding
- Used to group related `ToggleRow` + `ValueRow` items

#### `EmptyState`
- Centred vertically in available space
- Title (17sp semibold): "No {type} found"
- Subtitle (14sp grey): "Try adjusting your search or filter to find what you are looking for."
- No icon in Workspace empty states (text only)

#### `WorkspaceTabBar`
- Horizontally scrollable row of text tabs with count badges: `Models N` / `Knowledge N` / `Prompts N` / `S` (Skills) / etc.
- `Create ▾` is a fixed pill button at the RIGHT (not a tab) — stays in place as tabs scroll

#### `FunctionRow` (Admin Functions list)
- Type badge: `FILTER` or `PIPE` (uppercase, grey-outlined pill, ~8dp radius)
- Name (bold, truncated) + version tag + relative time
- Right icons: ♥ favourite + ⚙ settings + `···` overflow + `ON/OFF` toggle

#### `AdminUserRow`
- Avatar circle ~28dp (initials fallback, grey bg)
- Online dot `●` green (bottom-right of avatar)
- Name (15sp) + Role badge (tappable, blue pill, changes on tap)
- Email (grey, 13sp)
- Last active (grey, 13sp, relative time)

#### `ModelPickerBottomSheet`
- Standard bottom sheet (drag handle at top)
- Search field
- Grouped list: provider sections → model items
- Checkmark on selected model

#### `SettingsModal`
- Presented as full-screen dialog
- Horizontal scrollable tab bar at top
- Content area is vertically scrollable
- `Save` button fixed at bottom-right
- Tabs: General, Interface, Notifications, Keyboard, Connections, Integrations, Personalisation, Audio, Data Controls

#### `ToggleRow`
- Label (15sp) + optional description (13sp, grey) on left
- `Switch` composable on right
- Height: min 56dp (taller if description present)

#### `ValueRow`
- Label on left, current value + chevron on right
- Tappable → opens picker or inline expand

#### `SectionHeader`
- 13sp semibold, `#6B7280`, ALL CAPS
- 8dp bottom margin
- 16dp left padding

#### `EmptyState`
- Centred vertically
- Large icon (48dp, grey)
- Title 17sp semibold
- Subtitle 14sp grey
- Optional CTA button

#### `ChatHistoryGroup`
- Date group label (e.g. "Today", "Previous 7 days") — `SectionHeader` style
- `LazyColumn` of `ChatHistoryItem` rows

#### `ChatHistoryItem`
- 48dp tall
- Title text 15sp (truncated single line)
- Relative timestamp 12sp grey, right-aligned
- On long-press: context menu (rename, share, archive, delete)

#### `ProviderConnectionCard`
- Card with provider icon + name
- Status indicator (green dot = connected, grey = disconnected)
- URL + optional API key fields
- Test connection button

#### `AdminUserRow`
- Avatar (28dp circle, initial fallback)
- Name + online status dot
- Role badge (tappable to change: Admin / User / Pending)
- Email
- Last active timestamp

---

### Navigation Architecture

> **Design decision**: The Android app mirrors the web app's navigation exactly — a **persistent sidebar drawer** is the sole navigation surface. There is **no bottom navigation bar**. Every section (Workspace, Notes, Channels, Playground, Settings, Admin Panel) is accessed from the sidebar's user menu popover, exactly as on the web.

```
Root NavHost
├── OnboardingGraph (shown on first launch only)
│   ├── WelcomeScreen
│   ├── AddProviderScreen
│   └── SetupPinScreen (optional)
│
├── PinLockScreen (shown on resume if PIN lock enabled)
│
└── MainGraph  ← single-stack, NO bottom nav bar
    │
    ├── NewChatScreen (home, default destination)
    │     Scaffold:
    │       TopBar: [☰] ........... [○ temp-chat] [⊟ sliders]
    │       Body:   model logo + name + InputCard + Suggestions
    │       Drawer: SidebarDrawer (slides from left)
    │
    ├── ChatScreen(chatId)
    │     Scaffold:
    │       TopBar: [☰] [title] [···] [⊕] [⊟]
    │       Body:   message list + InputBar (fixed bottom)
    │       Overlay: ControlsPanel (full-screen, on ⊟ tap)
    │       Drawer: SidebarDrawer (same instance)
    │
    ├── WorkspaceScreen  ← pushed from sidebar user menu → Workspace
    │   ├── ModelsScreen  (tab in WorkspaceScreen)
    │   ├── KnowledgeScreen  (tab)
    │   ├── PromptsScreen    (tab)
    │   ├── ToolsScreen      (tab)
    │   ├── SkillsScreen     (tab)
    │   ├── FunctionsScreen  (admin only, via Admin Panel)
    │   └── CreateModelScreen
    │
    ├── PlaygroundScreen  ← pushed from sidebar user menu → Playground
    │   (tabs: Chat | Completions | Images)
    │
    ├── NoteListScreen    ← pushed from sidebar user menu (if Notes enabled)
    │   └── NoteEditorScreen(noteId)
    │
    ├── ChannelListScreen ← pushed from sidebar user menu (if Channels enabled)
    │   └── ChannelScreen(channelId)
    │
    ├── AdminPanelScreen  ← pushed from sidebar user menu → Admin Panel
    │   ├── AdminUsersScreen    (tab)
    │   ├── AdminEvaluationsScreen (tab)
    │   ├── AdminFunctionsScreen   (tab)
    │   └── AdminSettingsScreen    (tab)
    │       (sub-tabs: General | Authentication | Connections | Integrations | ...)
    │
    ├── SettingsScreen    ← pushed from sidebar user menu → Settings
    │   (full-screen modal with scrollable tab bar)
    │   (tabs: General | Interface | Notifications | Keyboard | Connections |
    │          Integrations | Personalization | Audio | Data Controls)
    │
    ├── ProfileScreen     ← from sidebar footer avatar long-press
    ├── MemoriesScreen    ← from sidebar user menu (via Settings or dedicated entry)
    ├── AutomationsScreen ← from sidebar user menu → Automations
    ├── CalendarScreen    ← from sidebar user menu → Calendar
    └── PinLockSetupScreen ← from Settings → Security
```

**Sidebar Drawer structure** (confirmed from live screenshots):
```
[☰ toggle collapses sidebar — top right of drawer header]

Header:  [OI logo]  "Open WebUI"              [□ collapse icon]

─── action rows ──────────────────────────────────────────────────
[✏ pencil]  New Chat
[🔍 search] Search

─── Models ───────────────────────────────────────────────────────
  [OI icon]  gpt-5.6-terra
  [OI icon]  models/gemini-3.6-flash
  [OI icon]  models/gemini-pro-latest
  (tapping a model opens New Chat with that model pre-selected)

─── Folders ──────────────────────────────  [+ add button]────────
  (empty if no folders created)

─── Chats ────────────────────────────────  [··· overflow]────────
  Today
    Hanoi and Cat Ba Cruises              1h
  Yesterday
    Hanoi Budget Accommodation           19h
    Vietnam Travel Forex Cards           22h
  Previous 7 days
    Vietnam Day Cruise Comparis...        1d
    ...
  Previous 30 days
    ...
  [Month name]
    ...

─── Footer ───────────────────────────────────────────────────────
  [Avatar circle ~28dp]  User  [● green online dot]
  ↑ tapping opens User Menu popover
```

**User Menu Popover** (confirmed from live screenshots — exact items & icons):
```
╔══════════════════════════════════════╗
║  [Avatar]  User              ● 1    ║
╠══════════════════════════════════════╣
║  😊  Update your status             ║
║  ⊞   Workspace                      ║  → pushes WorkspaceScreen
║  📅  Calendar                       ║  → pushes CalendarScreen
║  ⏱   Automations                    ║  → pushes AutomationsScreen
║  </>  Playground                    ║  → pushes PlaygroundScreen
║  👤  Admin Panel                    ║  → pushes AdminPanelScreen
║  ⚙   Settings                      ║  → pushes SettingsScreen (modal)
║  ⎋   Sign Out                       ║
╚══════════════════════════════════════╝
```

**Navigation rules**:
1. **No bottom nav bar** — the sidebar is the only navigation surface, matching the web exactly.
2. All secondary screens (Workspace, Settings, Playground, etc.) are **pushed onto the back stack** from the sidebar — pressing Android back returns to the previous screen.
3. The `SidebarDrawer` is a `ModalNavigationDrawer` that is accessible from every screen (via the ☰ icon in TopBar). It maintains its open/closed state independently of which screen is showing.
4. **Deep links** (`openwebui://chat/{chatId}`) open `ChatScreen` directly, bypassing the sidebar.
5. Notes and Channels are **conditionally shown** in the sidebar/menu only when enabled in Admin Settings features toggles. If disabled, tapping navigates to a "Feature disabled" explanation screen.

---

### Gesture & Interaction Patterns

| Gesture | Action |
|---|---|
| Swipe right from left edge (any screen) | Open sidebar drawer |
| Swipe left on sidebar / tap scrim | Close sidebar drawer |
| Tap ☰ icon in TopBar | Open/close sidebar drawer |
| Tap avatar footer in sidebar | Open User Menu popover |
| Tap item in User Menu | Push corresponding screen, close sidebar |
| Tap model in sidebar Models section | Start new chat with that model |
| Long-press on chat row in sidebar | Show chat options (rename / share / archive / delete) |
| Swipe left on chat row in sidebar | Reveal delete swipe action |
| Long-press on message bubble | Show 7-icon action strip (✏ 🗑 🔊 ℹ ▶ ↻ ↗) |
| Tap follow-up suggestion row | Populate input with that text |
| Tap `N Sources` citation row | Expand source list sheet |
| Tap model chip in input bar | Open inline model dropdown |
| Tap ⊟ sliders in TopBar | Open Controls Panel (full-screen overlay) |
| Tap `✕` in Controls Panel | Close Controls Panel |
| Pull-to-refresh on chat list | Reload chat list from local DB |
| Tap outside settings modal / press Back | Dismiss settings (prompt save if unsaved changes) |

---

### Theming

The app supports four themes matching Open WebUI exactly:
1. **System** (default): follows Android system dark/light mode
2. **Light**: forced light
3. **OLED Dark** (`🌃`): `#000000` background, optimised for OLED screens
4. **Her** (`🌷`): `#983724` brand colour accent, warm tones

All themes use Material3 `ColorScheme`. Custom tokens are mapped:
- `colorSurface` → screen backgrounds
- `colorOnSurface` → primary text
- `colorSurfaceVariant` → card/input backgrounds
- `colorOutline` → borders/dividers

---


---

## Implementation Notes — Critical Details for the Implementing Agent

This section documents every decision, exact API contract, format, and convention that is **not deducible from the sub-tasks alone**. An implementing agent must read this section before starting any sub-task.

---

### 1. Exact Dependency Versions (Sub-Task 1)

Use these exact coordinates in `build.gradle.kts`. Do not pick versions independently.

```kotlin
// Compose BOM
implementation(platform("androidx.compose:compose-bom:2024.04.00"))

// Core
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
implementation("androidx.activity:activity-compose:1.8.2")
implementation("androidx.navigation:navigation-compose:2.7.7")

// Hilt
implementation("com.google.dagger:hilt-android:2.48")
ksp("com.google.dagger:hilt-android-compiler:2.48")
implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

// Room
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// Networking
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

// Serialization
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.0.0")

// Paging
implementation("androidx.paging:paging-runtime-ktx:3.2.1")
implementation("androidx.paging:paging-compose:3.2.1")

// WorkManager
implementation("androidx.work:work-runtime-ktx:2.9.0")

// Coil (image loading)
implementation("io.coil-kt:coil-compose:2.5.0")

// Markdown — use compose-richtext, NOT markwon (different API)
implementation("com.halilibo.compose-richtext:richtext-ui:0.17.0")
implementation("com.halilibo.compose-richtext:richtext-ui-material3:0.17.0")
implementation("com.halilibo.compose-richtext:richtext-commonmark:0.17.0")

// Permissions
implementation("com.google.accompanist:accompanist-permissions:0.34.0")
implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")

// Kotlin Scripting — ALL FOUR are required; missing compiler-embeddable will crash at runtime
implementation("org.jetbrains.kotlin:kotlin-scripting-common:1.9.21")
implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:1.9.21")
implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:1.9.21")
implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:1.9.21")

// Security / EXIF
implementation("androidx.security:security-crypto:1.1.0-alpha06")
implementation("androidx.exifinterface:exifinterface:1.3.7")

// Logging
implementation("com.jakewharton.timber:timber:5.0.1")
```

**Required packaging block** — without this the build fails with duplicate Kotlin built-in file errors when scripting dependencies are present:

```kotlin
packaging {
    resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
        pickFirsts += "kotlin/kotlin.kotlin_builtins"
        pickFirsts += "kotlin/internal/internal.kotlin_builtins"
        pickFirsts += "kotlin/collections/collections.kotlin_builtins"
        pickFirsts += "kotlin/ranges/ranges.kotlin_builtins"
        pickFirsts += "kotlin/annotation/annotation.kotlin_builtins"
        pickFirsts += "kotlin/reflect/reflect.kotlin_builtins"
        pickFirsts += "kotlin/coroutines/coroutines.kotlin_builtins"
    }
}
```

**Build config:**
- `compileOptions { sourceCompatibility = JavaVersion.VERSION_17 }`
- `kotlinOptions { jvmTarget = "17" }`
- `composeOptions { kotlinCompilerExtensionVersion = "1.5.6" }`
- `buildFeatures { compose = true; buildConfig = true }`

---

### 2. `chat.chat` JSON Blob Internal Structure (Sub-Tasks 3, 4, 4b)

The `chat.chat` column (Room: `ChatEntity.chatJson`) stores this exact structure:

```json
{
  "history": {
    "messages": {
      "msg-001": {
        "id": "msg-001",
        "role": "user",
        "content": "Hello",
        "parentId": null,
        "children": ["msg-002"],
        "timestamp": 1717484000,
        "done": true
      },
      "msg-002": {
        "id": "msg-002",
        "role": "assistant",
        "content": "Hi there!",
        "parentId": "msg-001",
        "children": [],
        "model": "gpt-4",
        "modelName": "GPT-4",
        "timestamp": 1717484005,
        "done": true,
        "info": { "total_duration": 1200, "eval_count": 15 }
      }
    },
    "currentId": "msg-002"
  },
  "messages": ["msg-001", "msg-002"],
  "title": "Hello chat",
  "tags": []
}
```

Key rules:
- `history.messages` is a **dict keyed by message ID**, NOT an array
- `messages` at root level is an **ordered array of message IDs** (the linearised conversation path — branch not included)
- `history.currentId` is the ID of the last visible message on the active branch
- Each message has a `children: List<String>` array of child message IDs (for branching/regeneration)
- Timestamps inside the blob are epoch **seconds** (not milliseconds); `TimestampNormalizer` converts to ms on import
- When writing `ExportManager`, reconstruct this structure from `ChatMessageEntity` rows: parentId chain → build the dict; order of insertion → build the array

---

### 3. SSE / Streaming Response Parsing Per Provider (Sub-Task 2 — `ModelRouter`)

Each provider uses a different streaming wire format. `ModelRouter.stream()` must handle all four:

**Ollama `/api/chat` (newline-delimited JSON, no `data:` prefix):**
```json
{"model":"llama3","message":{"role":"assistant","content":"Hello"},"done":false}
{"model":"llama3","message":{"role":"assistant","content":" world"},"done":false}
{"model":"llama3","done":true,"total_duration":1234}
```
- Parse each newline as a JSON object
- Accumulate `message.content`
- Stop when `done == true`

**OpenAI `/v1/chat/completions` (SSE with `data:` prefix):**
```
data: {"id":"chatcmpl-xxx","choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxx","choices":[{"delta":{"content":" world"},"finish_reason":null}]}

data: [DONE]
```
- Strip `data: ` prefix from each line
- Skip empty lines
- Stop when line equals `[DONE]`
- Accumulate `choices[0].delta.content` (can be null on first chunk which has `role`)

**Anthropic `/v1/messages` (SSE with event type lines):**
```
event: message_start
data: {"type":"message_start","message":{...}}

event: content_block_start
data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}

event: message_stop
data: {"type":"message_stop"}
```
- Parse `event:` line then `data:` line as a pair
- Only extract text from `content_block_delta` events where `delta.type == "text_delta"`
- Stop on `event: message_stop`
- Auth header: `x-api-key: {apiKey}`, `anthropic-version: 2023-06-01`

**Google Gemini `streamGenerateContent` (JSON array stream):**
```
[
{"candidates":[{"content":{"parts":[{"text":"Hello"}],"role":"model"},"finishReason":null}]}
,
{"candidates":[{"content":{"parts":[{"text":" world"}],"role":"model"},"finishReason":"STOP"}]}
]
```
- The stream is a JSON array opened with `[` and closed with `]`; chunks arrive as individual array elements separated by `,\n`
- Each element is a complete JSON object — parse it standalone after stripping leading `,`
- Accumulate `candidates[0].content.parts[0].text`
- Stop when `finishReason` is non-null (e.g. `"STOP"`)
- Auth: `?key={apiKey}` query parameter

---

### 4. `FunctionLoader` / `FunctionExecutor` Implementation Contract (Sub-Task 13)

These are the exact class contracts. Do not deviate from this design.

**`FunctionLoader`** (`@Singleton`):
- Uses `BasicJvmScriptingHost` from `kotlin-scripting-jvm-host`
- Compiles via `scriptingHost.compiler(source, compilationConfig)` — does **not** use `javax.script.ScriptEngine`
- Configuration uses `createJvmCompilationConfigurationFromTemplate<Any> { jvm { dependenciesFromCurrentContext(wholeClasspath = true) } }`
- After compile: calls `scriptingHost.evaluator(compiledScript)` to get the evaluated result
- The function instance is extracted from `evaluationResult.valueOrThrow().returnValue.scriptInstance`
- Checks `ScriptDiagnostic.Severity.ERROR` reports; throws `FunctionCompilationException` on errors
- Three public methods: `loadPipeFunction()`, `loadFilterFunction()`, `loadActionFunction()` — each returns `Result<T>`
- Public `validateCode()` method (used by the code editor UI to show inline errors before saving)

**`FunctionExecutor`** (`@Singleton`):
- Three `ConcurrentHashMap` caches: one per function type
- One `Mutex` for thread-safe loading (double-checked locking: check map → lock → check map again → load)
- Public methods: `executePipe()`, `executePipeSync()`, `getPipes()`, `executeFilterInlet()`, `executeFilterOutlet()`, `executeAction()`, `getActionInfo()`, `invalidateCache(id, type)`, `clearCache()`
- `invalidateCache` calls `function.cleanup()` before removing from map
- `clearCache()` calls `cleanup()` on all cached instances before clearing

**`FunctionInterfaces`** (in `domain/function/`):
- `BaseFunction`: `id`, `name`, `description`, `version`; `initialize(valves, userValves?)`, `validate()`, `cleanup()`
- `PipeFunction extends BaseFunction`: `pipes(): List<PipeModel>`, `pipe(model, messages, options): Flow<PipeResponse>`, `pipeSync()` (default impl)
- `FilterFunction extends BaseFunction`: `inlet(messages, options): FilterInletResult` (default: passthrough), `outlet(response: Flow<PipeResponse>): Flow<PipeResponse>` (default: passthrough), `isGlobal: Boolean`
- `ActionFunction extends BaseFunction`: `action(message, chat, options): ActionResult`, `getActionInfo(): ActionInfo`, `isGlobal: Boolean`

---

### 5. YouTube Transcript Ingestion Algorithm (Sub-Task 10)

YouTube provides no public transcript API without OAuth. Use the unofficial embedded captions approach:

```
1. Extract video ID from URL:
   - youtube.com/watch?v=VIDEO_ID
   - youtu.be/VIDEO_ID
   - youtube.com/shorts/VIDEO_ID

2. Fetch the watch page HTML:
   GET https://www.youtube.com/watch?v={videoId}
   User-Agent: Mozilla/5.0 (compatible)

3. Find the captionTracks JSON in the HTML:
   Search for "captionTracks":[
   Extract the array via regex until matching ]
   Parse the first track's "baseUrl" field

4. Fetch the transcript XML:
   GET {baseUrl}  (the timedtext URL, already URL-encoded in the page)

5. Parse the XML:
   <text start="0.5" dur="2.3">Hello world</text>
   Strip all XML tags, decode HTML entities (&amp; → &, &#39; → ')
   Join all text nodes with spaces

6. Use the video title (from <title> tag or "videoTitle" in page JSON) as the document name
7. Chunk and embed as normal (same pipeline as URL ingestion)
```

If the page has no `captionTracks` (no captions available), show an error: *"No captions available for this video."*

---

### 6. DataStore Key Structure — Provider Configs vs User Settings (Sub-Tasks 3, 5, 8)

There are three separate DataStore namespaces that must not be mixed:

| DataStore Key | Type | Description |
|---|---|---|
| `user_settings_json` | `String` (JSON) | Mirrors `user.settings` from Open WebUI — theme, language, interface prefs, chat params. Written on import/sync from `user.settings`. |
| `provider_configs_json` | `String` (JSON array) | `List<ProviderConfig>` — Android-only. Device-local API keys and URLs. **Never exported or synced.** |
| `app_lock_config_json` | `String` (JSON) | PIN hash, biometric enabled flag. **Never exported or synced.** |
| `onboarding_complete` | `Boolean` | First-launch flag. |
| `local_user_id` | `String` | The `userId` used for all local Room records (a UUID generated at first launch). |
| `last_sync_at` | `Long` | Epoch ms of last successful sync. |
| `sync_server_url` | `String` | Base URL of the Open WebUI server for sync. |
| `sync_api_key` | `String` | Open WebUI API key for sync auth (stored via `EncryptedSharedPreferences`, not plain DataStore). |

`ProviderConfig` fields: `id: String (UUID), type: ProviderType (OLLAMA/OPENAI/ANTHROPIC/GEMINI/CUSTOM), name: String, baseUrl: String, apiKey: String (stored in EncryptedSharedPreferences keyed by id), enabled: Boolean, isDefault: Boolean, supportsImageGen: Boolean, supportsEmbeddings: Boolean, supportsSTT: Boolean, supportsTTS: Boolean`

API keys for providers are stored in `EncryptedSharedPreferences` (from `security-crypto`), NOT in plain DataStore. Only the key reference ID (provider UUID) is stored in DataStore.

---

### 7. AI Background Tasks — Owning Sub-Task and Implementation (Sub-Task 9)

These tasks are mentioned in Architecture Decisions but have no implementing sub-task. They belong in **Sub-Task 9** as part of `ChatViewModel`. Add these to Sub-Task 9's Todo List:

9. Create `BackgroundTaskRunner.kt`: a helper that fires lightweight model calls after a message is complete — uses a separate low-priority coroutine scope, never blocks the chat UX:
   - **Title generation**: if `chat.title == "New Chat"` and message count == 2 → call model with prompt `"Generate a short 3-5 word title for this conversation. Reply with only the title, no punctuation."` → write to `ChatEntity.title`
   - **Tag generation**: after 4+ messages → call model with prompt `"Generate 1-3 lowercase tags for this conversation. Reply with only the tags separated by commas."` → parse → write to `TagEntity` + update `ChatEntity.metaJson.tags`
   - **Follow-up suggestions**: after every assistant message → call model with `"Suggest 3 short follow-up questions the user might ask. Reply with only the questions, one per line."` → parse 3 lines → store in `Message.statusHistory` as `{ "suggestions": [...] }` → render as pills below message bubble
   - **Emoji reaction**: after assistant message → call model with `"React to this message with a single emoji."` → store as `Message.statusHistory` entry `{ "emoji": "🎉" }` → display next to action strip
   - All use `ModelRouter` with the **same model** as the conversation (or a smaller designated task model if configured in Settings)

---

### 8. Tag-to-Chat Linkage (Sub-Task 5, `TagRepositoryImpl`)

The `tag` table has a composite PK `(id, user_id)`. The linkage to chats is **not** via a separate cross-ref table. It is:
- `ChatEntity.metaJson` contains a `tags: List<String>` field — these are the tag **name strings**
- `TagEntity` stores the canonical tag record for each `(tagName, userId)` pair
- To get chats by tag: query `ChatDao` with `WHERE metaJson LIKE '%"tagName"%'` or deserialize the meta JSON and filter
- To get all tags for a user: query `TagDao.getByUserId(userId)`
- When a chat is deleted: check if any remaining chats use the same tag names; if not, delete the orphan `TagEntity` rows (`TagDao.deleteOrphanTags(userId, usedTagNames)`)
- Sub-Task 5 references `ChatTagCrossRef` — this does NOT exist in the new schema. Remove it.

---

### 9. Name Consistency: `Feedback` vs `Evaluation` (Sub-Tasks 3, 4, 5)

The Open WebUI schema uses the name **`feedback`** (table, entity, DAO). In the new project:

- Room entity: `FeedbackEntity` (table `feedback`)
- DAO: `FeedbackDao`
- Domain model: `Feedback`
- Repository: `FeedbackRepository` / `FeedbackRepositoryImpl`

Sub-Task 5 item 16 says `EvaluationRepositoryImpl` and `EvaluationDao` — these must be renamed to `FeedbackRepositoryImpl` and `FeedbackDao`. The Elo leaderboard in Sub-Task 15 reads from `FeedbackDao`.

---

### 10. Functions: Workspace vs App Management (Sub-Tasks 13 and 15)

In Open WebUI:
- `/workspace/functions` returns a **404** — functions are admin-only
- Functions appear only in the Admin panel

In the Android app (single-user, no server):
- **All functions** live in the **Workspace tab** under a "Functions" tab (alongside Models, Knowledge, Prompts, Skills, Tools)
- The `GlobalFunctionsScreen` in App Management (Sub-Task 15) is a **secondary view** that filters the same `FunctionEntity` list to `isGlobal = true`, allowing the user to promote/demote a function to global scope
- There is **no duplication** — both screens read from the same `FunctionDao`; only the filter differs

The Workspace horizontal tab bar (observed in live UI as "Models / Knowledge / Prompts / Skills / Tools") should be extended to: **Models / Knowledge / Prompts / Skills / Tools / Functions** in the Android app.

---

### 11. `MarkdownText` Composable Is Built From Scratch (Sub-Task 19)

Sub-Task 19 step 1 says "Audit `MarkdownTextEnhanced.kt`" — this is incorrect for a new project. The correct step is:

1. Create `MarkdownText.kt` composable from scratch using `compose-richtext:richtext-commonmark`
2. The `RichText` composable from `com.halilibo.compose-richtext` handles bold, italic, code, lists, headers, and links natively
3. For inline math (`$...$`) and block math (`$$...$$`): pre-process the string to extract math spans → render each as a `jlatexmath-android` `LaTeXMathView` inside an `AndroidView`; re-join surrounding text as `RichText`
4. For `<think>...</think>` blocks: pre-process → strip from main content string → render as a separate collapsible `ExpandableCard` above the message body
5. For code blocks: intercept fenced code blocks before passing to RichText → render as a `CodeBlock` composable (monospace Card with language label + copy button)
6. For tables: `compose-richtext` renders tables natively — wrap in `HorizontalScrollContainer` to handle overflow

---

### 12. Channel Soft-Delete Handling (Sub-Task 12)

The `channel` table has `deleted_at` and `archived_at` fields. The UI and queries must respect them:

- **Active channels**: `WHERE deleted_at IS NULL AND archived_at IS NULL`
- **Archived channels** (hidden from main list, accessible via "Archived" filter): `WHERE archived_at IS NOT NULL AND deleted_at IS NULL`
- **"Delete" in UI** sets `deleted_at = now()`, does **not** call `DELETE` — this is a soft delete
- **Physical delete** (garbage collection): delete channels where `deleted_at < (now() - 30 days)` — run on app start via a background coroutine in `ChannelRepositoryImpl`
- `ChannelDao.getActiveChannels(userId)`: `SELECT * FROM channel WHERE user_id = :userId AND deleted_at IS NULL AND archived_at IS NULL ORDER BY updated_at DESC`

---

### 13. Streaming Text UI Behaviour — Full Specification (Sub-Tasks 2, 5, 9)

This is the most critical UX detail in the entire app and is not specified anywhere else in the plan. Every layer — `ModelRouter`, `ChatViewModel`, `MessageBubble` — has a specific role.

---

#### Data Flow: Token → Screen

```
ModelRouter.stream()          → Flow<String>  (one token per emission)
        ↓
ChatRepositoryImpl            collects Flow, appends to in-memory buffer,
                              emits ChatStreamState via its own Flow
        ↓
ChatViewModel                 collects ChatStreamState, updates UiState.messages StateFlow
        ↓
ChatScreen (LazyColumn)       recomposes each token emission
        ↓
MessageBubble                 renders the partial string live
```

---

#### `ModelRouter` contract

`ModelRouter.stream()` returns `Flow<String>` where each emission is **one raw token string** (may be a single word, a partial word, or punctuation). It does **not** batch tokens. The flow completes normally when the provider signals done (`done:true` / `[DONE]` / `message_stop`). On provider error it emits via `flow { throw ... }` so the collector can catch it.

---

#### `ChatViewModel` state machine

```kotlin
sealed class ChatUiState {
    object Idle : ChatUiState()
    data class Active(
        val messages: List<Message>,      // all completed + the in-progress one
        val isStreaming: Boolean,          // true while Flow is active
        val streamingMessageId: String?,   // ID of the message currently being written
        val error: String? = null
    ) : ChatUiState()
}
```

Streaming lifecycle inside `ChatViewModel.sendMessage()`:

1. Append the user `Message` (role=USER, done=true) to `messages` list → update `StateFlow`
2. Create a placeholder assistant `Message` with `id = UUID`, `role = ASSISTANT`, `content = ""`, `done = false` → append to list → update `StateFlow` → set `streamingMessageId = id`
3. Set `isStreaming = true`
4. Collect `ModelRouter.stream()` token by token:
   - On each token: **mutate the in-progress message's `content` by appending the token** → emit updated `StateFlow`
   - Do **not** write to Room on every token — only write the final complete message
5. On flow completion: mark message `done = true`, `isStreaming = false`, `streamingMessageId = null` → write the complete `ChatMessageEntity` to Room
6. On error: set `error` field on the assistant message → set `done = true`, `isStreaming = false`

The `StateFlow<ChatUiState>` emits on **every token**. The `LazyColumn` recomposes on every emission, but because only the last message's `content` string changes, only the last `MessageBubble` recomposes (the others are stable).

**Stop button**: while `isStreaming == true`, show a `■ Stop` button in the input bar. Tapping it calls `currentStreamingJob.cancel()` — the coroutine collecting `ModelRouter.stream()` is cancelled. The message is saved to Room with whatever content was accumulated so far, marked `done = true`.

---

#### `MessageBubble` rendering during streaming

```
┌──────────────────────────────────┐
│ Hello! Here is how list          │  ← rendered text so far
│ comprehensions work in Python▌   │  ← blinking cursor at end
└──────────────────────────────────┘
```

- While `message.done == false` (streaming): show a **blinking cursor** after the last character — implement as an `AnimatedVisibility` with `Crossfade` on a `│` character, toggling every 530ms via `LaunchedEffect + delay`
- The cursor disappears the moment `message.done == true`
- The message bubble **height animates** as content grows: use `animateContentSize()` Compose modifier on the bubble container so the list smoothly expands rather than jumping

**Fade Effect for Streaming Text** (the "Fade Effect for Streaming Text" toggle in Settings → Interface):
- When enabled: newly appended tokens appear with a brief opacity animation from `0f → 1f` over `150ms`
- Implementation: maintain a `lastTokenIndex: Int` in `MessageBubble` state; use `AnnotatedString` with spans, or split the string at `lastTokenIndex` and apply `animateFloatAsState` to the alpha of the new suffix
- When disabled: tokens appear instantly at full opacity

---

#### Room write timing

- **User message**: written to `ChatMessageEntity` immediately when `sendMessage()` is called (before streaming starts)
- **Assistant message**: written to `ChatMessageEntity` only **once**, when `done == true` (streaming complete or cancelled) — never written mid-stream
- **`ChatEntity.updatedAt`**: updated once when the assistant message is finalised
- **`ChatEntity.chatJson`** (the `chat.chat` blob): reconstructed and written once after streaming completes — not updated incrementally

---

#### `LazyColumn` scroll behaviour

- While streaming: auto-scroll to bottom on every token emission — use `LaunchedEffect(messages.size, lastMessageContent)` → `listState.animateScrollToItem(messages.lastIndex)`
- After user manually scrolls up: **stop auto-scrolling** — detect this by comparing `listState.firstVisibleItemIndex` with `messages.lastIndex`; resume auto-scroll only when user scrolls back to bottom
- New chat / first message: scroll to bottom instantly (`scrollToItem`, not `animateScrollToItem`)

---

### 14. Multi-Model Chat — Full Specification (Sub-Tasks 2, 3, 4, 5, 9)

This feature is mentioned in the plan's scope ("multi-model, arena/comparison mode") but is **completely unspecified** in every sub-task. An agent would have no idea how the data model, streaming, or UI work without this note.

---

#### What Multi-Model Mode Is

When the user selects **more than one model**, sending a message fires **one parallel stream per model simultaneously**. Each model produces its own independent streaming response. The user reads responses by **swiping horizontally** — each swipe card shows one model's response. All streams run at the same time; each card fills in live as its tokens arrive.

This is the **default multi-model UI**. Arena mode is a variant (see below).

---

#### Model Selector UI — Selecting Multiple Models

The `ModelSelectorChip` in the input bar supports multiple models:

- Tapping the chip opens the `ModelPickerBottomSheet`
- Inside the sheet: each selected model shows as a chip row with a `−` remove button
- A `+ Add model` row at the bottom adds a second/third/Nth model slot (each slot picks from the full model list)
- There is no hard limit on the number of models
- The active selection is `List<String>` (model IDs), stored in `ChatViewModel` as `selectedModelIds: List<String>`
- The chip in the input bar shows: if 1 model → `"ModelName ▾"`, if N models → `"N models ▾"` (e.g. `"3 models ▾"`)

---

#### Message Data Structure With Multiple Models

In the `chat.chat` JSON blob (and mirrored in `ChatMessageEntity` rows), a single user message fans out to **N separate assistant messages** — one per model — all sharing the same `parentId`:

```
UserMessage (id: "u1", role: user, parentId: null)
    └── childrenIds: ["a1", "a2", "a3"]

AssistantMessage (id: "a1", role: assistant, parentId: "u1", modelId: "gpt-4",    modelIdx: 0, done: false)
AssistantMessage (id: "a2", role: assistant, parentId: "u1", modelId: "llama3",   modelIdx: 1, done: false)
AssistantMessage (id: "a3", role: assistant, parentId: "u1", modelId: "claude-3", modelIdx: 2, done: false)
```

Key fields added to `ChatMessage` domain model (and `ChatMessageEntity`):
- `modelIdx: Int` — position of this model in the `selectedModels` list at the time of sending (0-based)
- `models: List<String>?` — only set on the **user message**: the full list of selected model IDs at send time (e.g. `["gpt-4", "llama3", "claude-3"]`)

`history.currentId` in the JSON blob points to one of the assistant message IDs — the one currently visible in the pager.

---

#### Parallel Streaming — `ChatViewModel` Changes

When `selectedModelIds.size > 1`, `sendMessage()` launches one coroutine per model using `coroutineScope { launch { ... } }` — all coroutines start simultaneously:

```kotlin
// Inside ChatViewModel.sendMessage()
val userMsg = Message(role = USER, content = input, models = selectedModelIds, done = true)
saveUserMessage(userMsg)

// Create N placeholder assistant messages upfront
val assistantMsgIds = selectedModelIds.mapIndexed { idx, modelId ->
    val msg = Message(role = ASSISTANT, content = "", modelId = modelId, modelIdx = idx,
                      parentId = userMsg.id, done = false)
    addMessageToState(msg)
    msg.id
}

// Stream all N models in parallel — one coroutine each
coroutineScope {
    selectedModelIds.forEachIndexed { idx, modelId ->
        launch {
            modelRouter.stream(modelId, messages).collect { token ->
                appendTokenToMessage(assistantMsgIds[idx], token)   // updates StateFlow
            }
            markMessageDone(assistantMsgIds[idx])
            saveMessageToRoom(assistantMsgIds[idx])
        }
    }
}
// All N coroutines complete before sendMessage() returns
```

`ChatUiState.Active` needs an additional field:
```kotlin
data class Active(
    val messages: List<Message>,
    val isStreaming: Boolean,
    val streamingMessageIds: List<String>,  // replaces single streamingMessageId — one per model
    val visibleModelIndex: Int = 0,          // which swipe page the user is on
    val error: String? = null
)
```

`isStreaming` is `true` until **all** coroutines complete. The Stop button cancels all parallel jobs.

---

#### Multi-Model Response UI — `HorizontalPager`

When a user message has more than one assistant response (i.e. `childrenIds.size > 1`), the response area renders as a **horizontal swipeable pager** instead of a single `MessageBubble`.

**Component**: `MultiModelResponsePager` composable (new, to be created in Sub-Task 9)

```
┌─────────────────────────────────────────┐
│  GPT-4    llama3    claude-3             │  ← tab indicator row (model names)
│  ──────                                  │    active tab = current page, underline
├─────────────────────────────────────────┤
│                                         │
│  [Page 0 — GPT-4 response streaming]   │  ← HorizontalPager page
│                                         │
│  Hello! Here is how list comprehensions │
│  work in Python▌                        │
│                                         │
└─────────────────────────────────────────┘
         ●  ○  ○    ← page dot indicator
```

Implementation:
- Use `androidx.compose.foundation.pager.HorizontalPager` (available in Compose BOM 2024.04.00)
- `pageCount = childrenIds.size`
- `PagerState.currentPage` drives `visibleModelIndex` in `ChatUiState` (and vice versa — synced bidirectionally)
- Each page renders a standard `MessageBubble` for that model's message, with its own streaming cursor if `done == false`
- **All pages stream simultaneously** — the user can swipe to page 2 while pages 0 and 1 are still streaming
- Tab row above the pager: model name chips, tapping a chip animates the pager to that page
- Page dot indicator below the pager (only shown when 2–5 models; hidden for 6+ to save space)
- The pager height = `max(height of all pages)` — use `SubcomposeLayout` to measure all pages and size the container to the tallest, so swiping does not cause layout jumps

**Gesture**: standard horizontal swipe (Compose `HorizontalPager` handles this natively — no custom gesture needed).

---

#### Single-Model Mode (1 model selected)

When exactly 1 model is selected, the message tree is unchanged from the original streaming spec (Implementation Note 13). There is no pager — just a single `MessageBubble`. The `MultiModelResponsePager` is only rendered when `childrenIds.size > 1`.

---

#### Arena Mode

Arena mode is a **variant** of multi-model mode used specifically for blind evaluation. The differences from regular multi-model:

| | Regular Multi-Model | Arena Mode |
|---|---|---|
| Model names shown | Yes — tabs show model names | No — tabs show "Model A", "Model B" until user rates |
| Purpose | Compare models directly | Blind evaluation to avoid bias |
| `Message.arena` flag | `false` | `true` (set on both assistant messages) |
| Post-rating | Nothing special | After thumbs up/down, reveals which model was which + updates Elo in `FeedbackDao` |
| Entry point | Select 2+ models normally | Separate "Arena" toggle in `ModelPickerBottomSheet` |

Arena mode reuses the same `HorizontalPager` layout — the only difference is the tab labels and the post-rating reveal behaviour. `FeedbackEntity` records: `type = "arena"`, `data = { rating: "won"/"lost", modelId, siblingModelIds }`.

---

#### Navigation Tree Update

The Sub-Task 6 `Screen.kt` sealed class needs one additional destination:
- `Screen.Chat(chatId: String)` already covers the chat screen
- No new screen needed — multi-model is an in-chat UI state, not a separate destination

The `ModelPickerBottomSheet` (Sub-Task 9 step 7) needs to be updated to support:
1. Multiple model selections (a list, not a single selection)
2. An "Arena mode" toggle

---

#### Feature Coverage Matrix — Update

Add these two rows to the Feature Coverage Matrix:

| Open WebUI Feature | Android Implementation | Source of Truth | Sub-Task |
|---|---|---|---|
| Multi-model parallel streaming | Parallel coroutines in ChatViewModel + HorizontalPager | Memory + Room | 9 |
| Arena mode (blind evaluation) | Arena flag on messages + Elo update in FeedbackDao | Room | 9, 15 |




