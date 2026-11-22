package com.androidwebui.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.androidwebui.android.ui.components.EmptyState
import com.androidwebui.android.ui.components.ErrorState
import com.androidwebui.android.ui.components.LoadingState
import com.androidwebui.android.ui.components.UnsupportedState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StateViewsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun loadingShowsLabel() {
        compose.setContent { AppTheme(true) { LoadingState() } }
        compose.onNodeWithText("Loading…").assertIsDisplayed()
    }

    @Test
    fun emptyShowsMessageAndHint() {
        compose.setContent { AppTheme(false) { EmptyState("No chats yet", hint = "Start one") } }
        compose.onNodeWithText("No chats yet").assertIsDisplayed()
        compose.onNodeWithText("Start one").assertIsDisplayed()
    }

    @Test
    fun errorRetryInvokesCallback() {
        var retries = 0
        compose.setContent { AppTheme(true) { ErrorState("Request failed", onRetry = { retries++ }) } }
        compose.onNodeWithText("Request failed").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun unsupportedStatesTheReason() {
        compose.setContent { AppTheme(true) { UnsupportedState("Needs Android 14") } }
        compose.onNodeWithText("Not supported").assertIsDisplayed()
        compose.onNodeWithText("Needs Android 14").assertIsDisplayed()
    }
}
