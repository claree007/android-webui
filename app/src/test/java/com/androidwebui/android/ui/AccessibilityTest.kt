package com.androidwebui.android.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.androidwebui.android.ui.shell.HomeScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TalkBack reads the semantics tree, so every control that can be clicked must have a name. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccessibilityTest {
    @get:Rule val compose = createComposeRule()

    private fun unnamedClickables(): List<String> {
        val clickable = SemanticsMatcher("clickable") { it.config.contains(SemanticsActions.OnClick) }
        return compose.onAllNodes(clickable, useUnmergedTree = false).fetchSemanticsNodes().filter { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            text.isNullOrBlank() && desc.isNullOrBlank()
        }.map { "node ${it.id} role=${it.config.getOrNull(SemanticsProperties.Role)}" }
    }

    @Test
    fun homeControlsAreAllNamed() {
        compose.setContent { AppTheme(true) { HomeScreen("User", onOpenSettings = {}, onOpenDestination = {}) } }
        assertTrue("Unnamed clickable controls: ${unnamedClickables()}", unnamedClickables().isEmpty())
    }

    @Test
    fun drawerControlsAreAllNamed() {
        compose.setContent { AppTheme(true) { HomeScreen("User", onOpenSettings = {}, onOpenDestination = {}) } }
        compose.onNodeWithContentDescription("Open sidebar").performClick()
        assertTrue("Unnamed clickable controls: ${unnamedClickables()}", unnamedClickables().isEmpty())
    }
}
