package com.androidwebui.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.androidwebui.android.ui.chat.ChatScreen
import com.androidwebui.android.ui.chat.ChatUiState
import com.androidwebui.android.ui.chat.MessageRole
import com.androidwebui.android.ui.chat.MessageStatus
import com.androidwebui.android.ui.chat.MessageUi
import com.androidwebui.android.ui.shell.Feature
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun state(vararg messages: MessageUi) = ChatUiState("Trip plan", messages.toList())

    private fun show(state: ChatUiState, onUnavailable: (Feature) -> Unit = {}, onSend: () -> Unit = {}, dark: Boolean = true) {
        compose.setContent {
            AppTheme(dark) {
                ChatScreen(
                    state = state, draft = "", modelLabel = "gpt", onDraftChange = {}, onOpenSidebar = {},
                    onOpenModels = {}, onOpenControls = {}, onSend = onSend, onUnavailable = onUnavailable,
                )
            }
        }
    }

    private fun assertConversationShown(dark: Boolean) {
        show(
            state(
                MessageUi("1", MessageRole.User, "Hello"),
                MessageUi("2", MessageRole.Assistant, "Hi there"),
            ),
            dark = dark,
        )
        compose.onNodeWithText("Trip plan").assertIsDisplayed()
        compose.onNodeWithText("Hello").assertIsDisplayed()
        compose.onNodeWithText("Hi there").assertIsDisplayed()
    }

    @Test
    fun showsTitleAndMessagesInDarkTheme() = assertConversationShown(dark = true)

    @Test
    fun showsTitleAndMessagesInLightTheme() = assertConversationShown(dark = false)

    @Test
    fun failedMessageShowsItsError() {
        show(state(MessageUi("1", MessageRole.Assistant, "partial", MessageStatus.Failed, error = "Quota exceeded")))
        compose.onNodeWithText("Quota exceeded").assertIsDisplayed()
    }

    @Test
    fun interruptedMessageSaysSo() {
        show(state(MessageUi("1", MessageRole.Assistant, "partial", MessageStatus.Interrupted)))
        compose.onNodeWithText("Interrupted. The latest saved part of the reply is shown.").assertIsDisplayed()
    }

    @Test
    fun stoppedMessageSaysSo() {
        show(state(MessageUi("1", MessageRole.Assistant, "partial", MessageStatus.Stopped)))
        compose.onNodeWithText("Stopped.").assertIsDisplayed()
    }

    @Test
    fun menuActionsReportTheFeatureInsteadOfDoingNothing() {
        val reported = mutableListOf<Feature>()
        show(state(MessageUi("1", MessageRole.User, "Hello")), onUnavailable = { reported += it })
        compose.onNodeWithContentDescription("Chat options").performClick()
        compose.onNodeWithText("Rename").performClick()
        assertEquals(listOf(Feature.ChatRename), reported)
    }

    @Test
    fun streamingMessageHasNoCopyButtonUntilDone() {
        show(state(MessageUi("1", MessageRole.Assistant, "so far", MessageStatus.Streaming)))
        compose.onNodeWithContentDescription("Copy message").assertDoesNotExist()
    }
}
