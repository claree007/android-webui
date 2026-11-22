package com.androidwebui.android.ui.chat

enum class MessageRole { User, Assistant }

enum class MessageStatus { Complete, Streaming, Failed, Interrupted, Stopped }

/** What the transcript needs to draw one message. The conversation layer produces these; the UI never reads storage. */
data class MessageUi(
    val id: String,
    val role: MessageRole,
    val text: String,
    val status: MessageStatus = MessageStatus.Complete,
    val error: String? = null,
)

data class ChatUiState(
    val title: String,
    val messages: List<MessageUi>,
) {
    val isGenerating: Boolean get() = messages.any { it.status == MessageStatus.Streaming }
}
