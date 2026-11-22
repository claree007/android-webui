package com.androidwebui.android.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.androidwebui.android.core.database.LocalProfile
import com.androidwebui.android.core.prefs.DevicePrefs
import com.androidwebui.android.core.prefs.InMemoryStorage
import com.androidwebui.android.ui.shell.HomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LifecycleAndLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun home() = @androidx.compose.runtime.Composable {
        AppTheme(true) { HomeScreen("User", onOpenSettings = {}, onOpenDestination = {}) }
    }

    @Test
    fun draftSurvivesRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent(home())
        compose.onNode(hasSetTextAction()).performTextInput("half-written message")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("half-written message").assertIsDisplayed()
    }

    @Test
    fun settingsTabSurvivesRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AppTheme(true) { AppNavigation(DevicePrefs(InMemoryStorage()), LocalProfile("1", "User")) }
        }
        compose.onNodeWithContentDescription("Open sidebar").performClick()
        compose.onNodeWithContentDescription("User menu: User").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Connections").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Planned for Phase 4 of the implementation plan.").assertIsDisplayed()
    }

    @Test
    fun largestFontScaleKeepsComposerControlsReachable() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) { home()() }
        }
        compose.onNodeWithText("How can I help you today?").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send").assertIsDisplayed()
        compose.onNodeWithContentDescription("Attach").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land")
    fun landscapeKeepsComposerReachable() {
        compose.setContent(home())
        compose.onNodeWithText("How can I help you today?").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h480dp")
    fun smallScreenKeepsComposerReachable() {
        compose.setContent(home())
        compose.onNodeWithContentDescription("Send").assertIsDisplayed()
    }
}
