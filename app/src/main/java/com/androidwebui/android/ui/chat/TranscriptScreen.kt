package com.androidwebui.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.androidwebui.android.R
import com.androidwebui.android.ui.components.Spacing
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/** Message list. Text is selectable with the standard handles (D22); the newest message stays in view while it streams. */
@Composable
fun TranscriptList(messages: List<MessageUi>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.section),
    ) {
        items(messages, key = { it.id }) { message ->
            when (message.role) {
                MessageRole.User -> UserMessage(message)
                MessageRole.Assistant -> AssistantMessage(message)
            }
        }
    }
}

@Composable
private fun UserMessage(message: MessageUi) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        SelectionContainer {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun AssistantMessage(message: MessageUi) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SelectionContainer {
            Text(message.text, style = MaterialTheme.typography.bodyLarge)
        }
        when (message.status) {
            MessageStatus.Streaming -> LinearProgressIndicator(Modifier.fillMaxWidth())
            MessageStatus.Failed -> StatusNote(message.error ?: stringResource(R.string.message_failed), isError = true)
            MessageStatus.Interrupted -> StatusNote(stringResource(R.string.message_interrupted), isError = true)
            MessageStatus.Stopped -> StatusNote(stringResource(R.string.message_stopped), isError = false)
            MessageStatus.Complete -> Unit
        }
        if (message.status != MessageStatus.Streaming) {
            Row {
                IconButton(onClick = {
                    scope.launch {
                        clipboard.setClipEntry(
                            android.content.ClipData.newPlainText("message", message.text).toClipEntry(),
                        )
                    }
                }) {
                    Icon(painterResource(R.drawable.ic_copy), contentDescription = stringResource(R.string.message_copy))
                }
            }
        }
    }
}

@Composable
private fun StatusNote(text: String, isError: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
