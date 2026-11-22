package com.androidwebui.android.ui.shell

/** Everything the drawer lists. Loading and error are explicit so the drawer never shows a blank list silently. */
data class SidebarState(
    val models: List<ModelOption> = emptyList(),
    val folders: List<FolderSummary> = emptyList(),
    val chats: List<ChatSummary> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)
