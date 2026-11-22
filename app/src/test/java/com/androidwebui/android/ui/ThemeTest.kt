package com.androidwebui.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.androidwebui.android.core.prefs.ThemeMode
import com.androidwebui.android.ui.shell.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pixel screenshots could not be captured in the Docker aarch64 build (Robolectric's native graphics never
 * finishes a redraw there), so these tests check the colours the real screen resolves in each theme.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeTest {
    @get:Rule val compose = createComposeRule()

    private fun resolvedColors(dark: Boolean): Pair<Color, Color> {
        var background = Color.Unspecified
        var onBackground = Color.Unspecified
        compose.setContent {
            AppTheme(dark) {
                Probe { b, o -> background = b; onBackground = o }
                HomeScreen("User", onOpenSettings = {}, onOpenDestination = {})
            }
        }
        compose.waitForIdle()
        return background to onBackground
    }

    @Composable
    private fun Probe(report: (Color, Color) -> Unit) {
        report(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.onBackground)
    }

    @Test
    fun darkThemeUsesDarkSurfaceWithLightText() {
        val (bg, fg) = resolvedColors(dark = true)
        assertEquals(Color(0xFF171717), bg)
        assertEquals(Color(0xFFECECEC), fg)
    }

    @Test
    fun lightThemeUsesLightSurfaceWithDarkText() {
        val (bg, fg) = resolvedColors(dark = false)
        assertEquals(Color(0xFFFFFFFF), bg)
        assertEquals(Color(0xFF171717), fg)
    }

    @Test
    fun textAndBackgroundDifferInDarkTheme() {
        val (bg, fg) = resolvedColors(dark = true)
        assertNotEquals(bg, fg)
    }

    @Test
    fun explicitThemeModesIgnoreTheSystemSetting() {
        compose.setContent {
            assertEquals(false, isDarkFor(ThemeMode.Light))
            assertEquals(true, isDarkFor(ThemeMode.Dark))
        }
        compose.waitForIdle()
    }
}
