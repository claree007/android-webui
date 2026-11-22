# Screenshot map

Maps each file in `screenshots/` to a screen, its components and states, the target phase and the plan decision that applies. Files with identical bytes (checked with `md5`) are grouped on one row. The references show the Open WebUI web app at mobile width, dark theme.

**Verification:** rows marked **viewed** were opened and inspected. Rows marked **by name** are mapped from the filename and the web app's component structure only. Inspect them in the phase that builds the screen, then update the row.

## Chat and shell

| Files | Screen | Components and states | Phase | Notes | Verified |
|---|---|---|---|---|---|
| `01-new-chat-clean` | New chat (empty) | Top bar (sidebar toggle, temporary-chat toggle, controls toggle). Centered model avatar and name. Composer with placeholder "How can I help you today?", `+` attach button, tools button, model chip with dropdown, mic, voice button. "Suggested" list with title and subtitle rows | 3 | Remove the "You're now logged in" toast and the "new version available" banner (D10, D15). Suggestions come from the model's `suggestion_prompts` (D19) | viewed |
| `01-new-chat` | New chat (empty), second capture | Same screen as above | 3 | Distinct file, not inspected | by name |
| `33-attach-picker` | Intended: attach picker | The file shows the plain new-chat screen with suggestions, **not** the attach picker | 3 | **Missing visual reference** for the attach menu (files, camera, photos, web page, knowledge). Design from the web app's `MessageInput/InputMenu` | viewed |
| `attach-picker` | Intended: attach picker | Not inspected, and not the same file as `33-attach-picker` | 3 | Check before use | by name |
| `03-sidebar-open` | Sidebar drawer | Header (logo, collapse), New Chat, Search, Models list, Folders (+), Chats (⋯) grouped Today / Yesterday / Previous 7 days / Previous 30 days with relative-time labels, user row at the bottom | 3 | Presence dot and the "Open WebUI" name are removed (D10, D20). Use the app's own name and mark | viewed |
| `02-sidebar-open` | Sidebar drawer, earlier capture | Presumably the same layout | 3 | Distinct file, not inspected | by name |
| `03-sidebar`, `04-user-menu` | One file: sidebar with chat list | Presumably the sidebar without the menu open | 3 | Not inspected. `04-user-menu` does not show a menu if this is right | by name |
| `04-user-menu-popover` | User menu | Items: User header with presence count, Update your status, Workspace, Calendar, Automations, Playground, Admin Panel, Settings, Sign Out. The orange box is an annotation | 3 | **Keep:** Workspace, Playground, Settings, Notes, Memories. **Remove:** status, presence, Admin Panel, Sign Out, Channels. **Calendar and Automations stay hidden until built** (D10, Phase 12) | viewed |
| `user-menu-full`, `channels-page` | One file, content unknown | | 3 | Not inspected | by name |
| `32-model-selector-sheet`, `model-selector` | Model selector (one file) | Search field, filter chip ("All"), models with avatar, name, tag and connection icons, check on the selected row, "Set as default" footer | 3, 4 | The "Arena Model" row is excluded (D19). The selector lists the merged model list (D19) | viewed |
| `29-chat-thread`, `30-chat-thread-bottom`, `chat-thread-input`, `chat-thread-top` | Chat transcript (one file under four names) | Navbar with chat title and ⋯ menu, new-chat and controls buttons. Assistant Markdown (block quote, bold, link), source chip "6 Sources", action row (edit, copy, read aloud, info, continue, regenerate, branch), "Follow up" suggestions, composer pinned to the bottom | 4, 6 | Source chip and follow-ups are Phase 6/7. Markdown while streaming is Phase 4 (D22). The update banner covers the composer, so that state is not visible. No separate input-focused or scrolled-to-top capture exists despite the names | viewed |
| `17-chat-thread` | Chat transcript, second capture | | 4, 6 | Distinct file, not inspected | by name |
| `31-chat-controls-panel`, `chat-controls-panel` | Chat controls (one file) | Tabs Controls / Overview, collapsible Valves, System Prompt, Advanced Params with a "Default" value on each row (stream, chunk size, compaction threshold, function calling, reasoning tags, seed, stop, temperature, reasoning effort, logit_bias, max_tokens, top_k, top_p, min_p, penalties, mirostat, mirostat_eta, mirostat_tau, repeat_last_n) | 4 | Valves section appears only with Phase 11. Overview tab is Phase 6. Each row has a Default state plus a custom value. Unsupported options are omitted or explained (Phase 4 acceptance) | viewed |

## Settings

| Files | Screen | Components and states | Phase | Notes | Verified |
|---|---|---|---|---|---|
| `05-settings-general` | Settings → General | Full-height sheet, Back chip and scrolling tabs (General, Interface, Notifications ...). Theme dropdown (System), Language dropdown, default System Prompt, Advanced Parameters Show/Hide, Save button | 3, 4 | Per D15 theme and language follow D7 and the system locale. Save is explicit | viewed |
| `06-settings-interface-top`, `07-settings-interface-mid`, `08-settings-interface-bottom`, `settings-interface-scroll1` | Settings → Interface (one file) | Long list of toggles | 3, 6 | Keep list per D15; drop web-only items. The three "scrolled" names are one image, so scrolling was not captured | by name |
| `settings-interface-scroll2`, `settings-interface-scroll3` | Settings → Interface, two distinct files | Further toggles | 3, 6 | | by name |
| `09-settings-notifications`, `settings-notifications` | Settings → Notifications (one file) | Notification toggles | 5 | Reuse for generation notification and sound settings | by name |
| `10-settings-connections`, `settings-connections` | Settings → Connections (one file) | Connection list (OpenAI, Ollama) | 4 | D16. Direct Connections section is dropped. Add the D13 notice | by name |
| `settings-integrations`, `settings-personalization` | One file; may be either tab | | 12 | Check before use. Terminal servers are excluded (F058) | by name |
| `11-settings-personalization` | Settings → Personalization | Memory toggle and manage memories | 12 | D10 | by name |
| `12-settings-audio` | Settings → Audio | Speech-to-text and text-to-speech settings | 9 | Remote options require explicit config | by name |
| `13-settings-data`, `settings-data`, `settings-audio` | One file under three names, probably Settings → Data controls (so `settings-audio` is misnamed) | Import, export, archive, delete chats | 12 | Replaced by merge import and export (D2) | by name |
| `settings-keyboard`, `settings-keyboard-tab`, `settings-audio-tab` | Keyboard shortcuts. `settings-keyboard-tab` and `settings-audio-tab` are one file and `settings-keyboard` is a separate file | Shortcut list | – | **Dropped** (D15) | by name |

## Workspace and other areas

| Files | Screen | Phase | Notes |
|---|---|---|---|
| `06-workspace-models`, `14-workspace-models` | Workspace → Models list | 12 | D19 merged list |
| `18-workspace-create-model`, `workspace-create-model`, `workspace-create-model-bottom` | Model editor (one file; the "bottom" view was not captured separately) | 12 | Access controls removed (D19) |
| `07-workspace-knowledge`, `15-workspace-knowledge` | Workspace → Knowledge | 8, 12 | |
| `08-workspace-prompts`, `16-workspace-prompts` | Workspace → Prompts | 6, 12 | |
| `09-workspace-tools`, `17-workspace-tools` | Workspace → Tools | 11, 12 | |
| `workspace-skills` | Workspace → Skills | 12 | |
| `10-workspace-functions`, `14-channels`, `15-memories`, `25-channels-list`, `28-memories`, `channels` | One identical file under six names, so it cannot be Functions, Channels and Memories at once. Probably a placeholder or error page | 11, 12 | **Missing references** for Workspace → Functions, Channels (excluded anyway) and the Memories list |
| `13-notes` | Notes list | 12 | |
| `notes-list` | Notes list, second capture | 12 | |
| `23-notes-list`, `24-note-editor` | One file under two names | 12 | Check whether it is the list or the editor |
| `notes-editor` | Note editor | 12 | |
| `memories-page` | Memories | 12 | The only distinct memories capture |
| `16-playground`, `26-playground`, `27-playground-scrolled` | Playground (one file) | 12 | |
| `11-admin-users`, `19-admin-users` | Admin → Users (two distinct files) | – | **Excluded** |
| `20-admin-evaluations`, `admin-evaluations` | Admin → Evaluations | – | **Excluded** (D10) |
| `21-admin-functions`, `admin-functions` | Admin → Functions | 11, 12 | Moves to Workspace (D10) |
| `12-admin-settings`, `22-admin-settings-general`, `admin-settings-top`, `admin-settings-bottom` (one file), `admin-settings-scrolled`, `admin-settings-auth`, `admin-settings-connections` (separate files) | Admin → Settings | 4, 7, 8, 9, 10 | Source for equivalent local Settings screens (connections, web search, documents, audio, images, code execution). The Auth tab is excluded |

## Missing references

Recorded without blocking implementation:

- Attach picker (`33-attach-picker` shows the new-chat screen; `attach-picker` was not inspected).
- Workspace → Functions and the Memories list: the shared file above looks like a placeholder.
- Light theme: none of the inspected references is light. Derive the light palette from the dark one.
- Landscape and tablet layouts.
- Message edit mode, branch navigation, multi-model view, code block, table, streaming, error and loading states.
- Search modal, folders, archived chats, bulk selection.
- Tool, web search and document-attachment states.
- Notification and permission dialogs (Android-native, no reference).

## Design tokens observed (dark)

From the inspected screenshots, to be refined in Phase 3 `designsystem`: near-black background, raised surfaces slightly lighter, borders one step lighter, white primary buttons with black text, pill-shaped composer with a large radius, rounded sheets, muted gray secondary text, a large title on the new-chat screen, and thin outline icons. Exact colour values have not been sampled.
