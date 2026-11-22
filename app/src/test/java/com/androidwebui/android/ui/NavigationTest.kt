package com.androidwebui.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.androidwebui.android.core.database.LocalProfile
import com.androidwebui.android.core.prefs.DevicePrefs
import com.androidwebui.android.core.prefs.InMemoryStorage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationTest {
    @get:Rule val compose = createComposeRule()

    private fun start() = compose.setContent {
        AppTheme(true) { AppNavigation(DevicePrefs(InMemoryStorage()), LocalProfile("1", "User")) }
    }

    private fun openUserMenuItem(label: String) {
        compose.onNodeWithContentDescription("Open sidebar").performClick()
        compose.onNodeWithContentDescription("User menu: User").performClick()
        compose.onNodeWithText(label).performClick()
    }

    @Test
    fun settingsOpensAndGoesBack() {
        start()
        openUserMenuItem("Settings")
        compose.onNodeWithText("Theme").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("How can I help you today?").assertIsDisplayed()
    }

    @Test
    fun everySettingsTabIsReachableAndExplainsItsPhase() {
        start()
        openUserMenuItem("Settings")
        compose.onNodeWithText("Connections").performClick()
        compose.onNodeWithText("Planned for Phase 4 of the implementation plan.").assertIsDisplayed()
        compose.onNodeWithText("Notifications").performClick()
        compose.onNodeWithText("Planned for Phase 5 of the implementation plan.").assertIsDisplayed()
    }

    @Test
    fun workspaceListsSectionsAndOpensOne() {
        start()
        openUserMenuItem("Workspace")
        compose.onNodeWithText("Knowledge").assertIsDisplayed()
        compose.onNodeWithText("Knowledge").performClick()
        compose.onNodeWithText("Planned for Phase 8 of the implementation plan.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Prompts").assertIsDisplayed()
    }

    private fun assertPlannedScreen(label: String) {
        start()
        openUserMenuItem(label)
        compose.onNodeWithText("Planned for Phase 12 of the implementation plan.").assertIsDisplayed()
    }

    @Test
    fun playgroundOpensItsScreen() = assertPlannedScreen("Playground")

    @Test
    fun notesOpensItsScreen() = assertPlannedScreen("Notes")

    @Test
    fun memoriesOpensItsScreen() = assertPlannedScreen("Memories")
}
