package com.androidwebui.android.ui.shell

import androidx.annotation.StringRes
import com.androidwebui.android.R

/** Features reachable from the shell that are not built yet. Selecting one explains that instead of doing nothing. */
enum class Feature(@StringRes val labelRes: Int) {
    Search(R.string.search),
    Folders(R.string.sidebar_folders),
    Attachments(R.string.composer_attach),
    Sending(R.string.composer_send),
    ChatControls(R.string.chat_controls),
    Workspace(R.string.menu_workspace),
    Playground(R.string.menu_playground),
    Notes(R.string.menu_notes),
    Memories(R.string.menu_memories),
}
