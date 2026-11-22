package com.androidwebui.android.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidwebui.android.R
import com.androidwebui.android.ui.shell.Composer
import com.androidwebui.android.ui.shell.Feature

/** Chat navbar (title and options menu), transcript and composer. State is hoisted so the conversation layer owns it. */
@Composable
fun ChatScreen(
    state: ChatUiState,
    draft: String,
    modelLabel: String,
    onDraftChange: (String) -> Unit,
    onOpenSidebar: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenControls: () -> Unit,
    onSend: () -> Unit,
    onUnavailable: (Feature) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ChatNavbar(state.title, onOpenSidebar, onOpenControls, onUnavailable)
        TranscriptList(state.messages, Modifier.weight(1f))
        Composer(
            draft = draft,
            onDraftChange = onDraftChange,
            modelLabel = modelLabel,
            onOpenModels = onOpenModels,
            onAttach = { onUnavailable(Feature.Attachments) },
            onSend = onSend,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ChatNavbar(
    title: String,
    onOpenSidebar: () -> Unit,
    onOpenControls: () -> Unit,
    onUnavailable: (Feature) -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOpenSidebar) {
            Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.open_sidebar))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Column {
            IconButton(onClick = { menuOpen = true }) {
                Icon(painterResource(R.drawable.ic_more), stringResource(R.string.chat_menu))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                listOf(
                    Feature.ChatShare to R.string.chat_menu_share,
                    Feature.ChatDownload to R.string.chat_menu_download,
                    Feature.ChatRename to R.string.chat_menu_rename,
                    Feature.ChatArchive to R.string.chat_menu_archive,
                    Feature.ChatDelete to R.string.chat_menu_delete,
                ).forEach { (feature, label) ->
                    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                        menuOpen = false
                        onUnavailable(feature)
                    })
                }
            }
        }
        IconButton(onClick = onOpenControls) {
            Icon(painterResource(R.drawable.ic_tune), stringResource(R.string.chat_controls))
        }
    }
}
