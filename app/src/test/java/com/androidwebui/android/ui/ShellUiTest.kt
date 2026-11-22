package com.androidwebui.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.androidwebui.android.ui.shell.HomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShellUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun newChatScreenShowsComposer() {
        compose.setContent { AppTheme(dark = true) { HomeScreen("User", onOpenSettings = {}, onOpenDestination = {}) } }
        compose.onNodeWithText("How can I help you today?").assertIsDisplayed()
    }

    @Test
    fun typedDraftIsShown() {
        compose.setContent { AppTheme(dark = false) { HomeScreen("User", onOpenSettings = {}, onOpenDestination = {}) } }
        compose.onNode(hasSetTextAction()).performTextInput("hello")
        compose.onNodeWithText("hello").assertIsDisplayed()
    }

    @Test
    fun sendWithoutModelExplainsInsteadOfSilentlyDoingNothing() {
        compose.setContent { AppTheme(dark = true) { HomeScreen("User", onOpenSettings = {}, onOpenDestination = {}) } }
        compose.onNodeWithContentDescription("Send").performClick()
        compose.onNodeWithText("Not available yet").assertIsDisplayed()
    }
}
