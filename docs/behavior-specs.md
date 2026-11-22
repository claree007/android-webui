# Behavior specs

Short behavior specs derived by reading the Open WebUI web app at the pinned revision (see `provenance.md`). They describe observable behavior and stored data formats only. No code or prompt text is copied (D20). Tests for each feature are written against these specs.

**Status:** specs below were written from source I read in this session. Features marked **TODO** still need their spec written before their phase starts. The Phase 0 acceptance item "no feature is marked complete without tests" is tracked in `feature-parity.csv`.

## Settings storage (Phase 1, Phase 4)

- Per-user UI settings are one JSON object, `user.settings`, whose `ui` key holds the settings the web app uses (for example `autoFollowUps`, `autoTags`, `title.auto`, `ui.chatDirection`, `ui.enableMessageQueue`, `ui.highContrastMode`, `ui.copyFormatted`, `ui.textScale`). Defaults apply when a key is absent.
- For auxiliary requests the web app defaults `title.auto`, `autoTags` and `autoFollowUps` to **on** when absent (D13).
- Global configuration is stored as one JSON document in the `config` table, with nested keys such as `openai.api_base_urls`, `openai.api_keys`, `openai.api_configs`, `ollama.base_urls`, `ollama.api_configs`, `openai.enable`, `ollama.enable`.
- Task configuration keys: `task.model.default`, `task.model.external`, `task.title.enable`, `task.title.prompt_template`, `task.tags.enable`, `task.tags.prompt_template`, `task.follow_up.enable`, `task.follow_up.prompt_template`, `task.query.search.enable`, `task.query.retrieval.enable`, `task.query.prompt_template`, `task.autocomplete.*`, `task.image.prompt_template`, `task.voice.prompt_template`, `task.tools.prompt_template`.

## Auxiliary (task) requests (Phase 4 title, Phase 6 tags and follow-ups)

- A task request is a separate, non-streaming chat completion sent to a **task model**.
- Task model choice: use the chat's own model unless a task model is configured. The web app has two settings, one for local (Ollama-type) connections and one for external ones, and picks by the connection type of the chat model. This app uses one `task.model.default` plus the external key for non-Ollama connections, so imports keep both (D13).
- An empty prompt-template setting means "use the built-in default". A non-empty value replaces it.
- Templates are filled with placeholders that insert the last N messages of the chat (the default title template uses the last two), the prompt, and the user's name. This app re-authors its templates (D20) and supports the same placeholder syntax so user-edited templates imported from a web app database keep working.
- Title: the model is asked for a JSON object with a `title` field; the title is stored in `chat.title` and in the chat JSON. App requirement (not derived from the web app): parse the response leniently, and let a failed or unparsable response leave the existing title unchanged without showing a chat error. The web app asks for a title only on the first exchange of a non-temporary chat.
- Tags: same pattern, producing a list of tags. **TODO:** confirm storage in the `tag` and `chatidtag` tables.
- Follow-ups: same pattern, producing a short list shown beneath the assistant message. **TODO:** confirm the stored field name.
- Each toggle can be disabled per user (`title.auto`, `autoTags`, `autoFollowUps` inside `user.settings.ui`) or globally (`task.*.enable`, default true). The client sends the per-user flags with the chat request and the server checks the global one, so either being off means no task request.
- Tests: title parsing with fenced and unfenced JSON, malformed output, disabled toggles, task model selection, request counted in usage.

## Chat message graph (Phase 4, Phase 6)

- `chat.chat` JSON holds `history.messages` (map of id to message) and `history.currentId`. Each message has `id`, `parentId`, `childrenIds`, `role`, `content`, `timestamp`, and for assistants `model`, `models` where relevant, `done`, and optionally `usage`, `sources`, `output`, `error`, `statusHistory`, `followUps`.
- The visible conversation is the path from the root to `currentId`. Editing a user message creates a sibling user message under the same parent. Regenerating creates a sibling assistant message. Nothing is deleted.
- The chat JSON also carries `messages`, a flattened copy of the active path, plus `title`, `models`, `system`, `params`, `tags` and `timestamp`. The web app writes `messages` when it creates a chat. **TODO:** confirm whether every later update rewrites it. This app regenerates it from `history` on every full write (D3).
- Provider context for a request is built from the active path only.
- Tests: branch switching rebuilds the right context, sibling creation on edit and regenerate, `currentId` after deleting a leaf.

## Model list (Phase 4, Phase 12)

Follows D19. Discovered models come from `openai.api_base_urls[i]` (`GET {base}/models`) and `ollama.base_urls[i]` (`GET {base}/api/tags`). Per-connection options in `api_configs[index]`: `enable`, `tags`, `prefix_id` (prepended to model IDs), `model_ids` (allow-list; empty means all). A disabled connection contributes no models.

## Connection test and errors (Phase 4)

A connection test requests the model list. Success shows the number of models. Authentication failures, unreachable hosts, timeouts, TLS errors and malformed bodies are reported separately. Keys are never shown after saving.

## Skills (Phase 12)

A skill is a named instruction block stored in the `skill` table (`id`, `name` unique, `description`, `content`, `meta.tags`, `is_active`). It is applied in two ways: a model lists skill IDs in `meta.skillIds` (the model gets a catalogue of skill names and descriptions and can load a skill's content on demand), or the user selects a skill for one message (its content is inserted into the system context directly). Inactive skills are never applied. Missing IDs are ignored.

## Web search (Phase 7)

The web app supports many search providers behind one interface that takes a query and returns a list of results (link, title, snippet). Results are then fetched, extracted and passed to the model as sources. Provider modules present in the pinned revision: azure, bing, bocha, brave, duckduckgo, exa, external, firecrawl, google_pse, jina_search, kagi, mojeek, ollama, perplexity, perplexity_search, searchapi, searxng, serpapi, serper, serply, serpstack, sougou, tavily, yacy, yandex, ydc. This app picks a first subset in Phase 7 and records the rest in `feature-parity.csv`. **TODO:** result count, query generation, domain filter and fetch behavior need a detailed spec before Phase 7.

## TODO before their phases

| Feature | Phase | What to derive |
|---|---|---|
| Message `output` items, `sources`, citations, status history, `usage` formats | 4, 7 | Exact JSON shapes as stored |
| Folders and pinning, tags tables, chat archive | 6 | Table usage and ordering |
| Prompt variables and slash commands | 6 | Syntax and substitution rules |
| Multi-model responses | 6 | Storage of sibling responses |
| Message queue | 6 | Ordering and cancel rules |
| RAG: chunking parameters, embedding settings, hybrid search | 8 | Stored config keys and defaults |
| Knowledge collections and files | 8 | Tables `knowledge`, `knowledge_file`, `file` |
| Audio and image settings | 9 | Config keys per provider |
| Code execution | 10 | Settings and result format |
| Plugins (tools, functions, valves) | 11 | API surface of the pinned revision |
| Notes, memories, playground | 12 | Tables and UI behavior |
| Import/export | 12 | Table-by-table merge rules |
