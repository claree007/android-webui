package com.androidwebui.android.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.androidwebui.android.R
import com.androidwebui.android.core.database.LocalProfile
import com.androidwebui.android.core.prefs.DevicePrefs
import com.androidwebui.android.ui.settings.SettingsScreen
import com.androidwebui.android.ui.shell.Feature
import com.androidwebui.android.ui.shell.HomeScreen
import com.androidwebui.android.ui.workspace.PlannedScreen
import com.androidwebui.android.ui.workspace.WorkspaceScreen
import com.androidwebui.android.ui.workspace.WorkspaceSection

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val WORKSPACE = "workspace"
    const val WORKSPACE_SECTION = "workspace/{section}"
    const val PLAYGROUND = "playground"
    const val NOTES = "notes"
    const val MEMORIES = "memories"
}

@Composable
fun AppNavigation(prefs: DevicePrefs, profile: LocalProfile?) {
    val nav = rememberNavController()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.safeDrawingPadding()) {
            composable(Routes.HOME) {
                HomeScreen(
                    profile?.name ?: stringResource(R.string.profile_loading),
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenDestination = { feature ->
                        when (feature) {
                            Feature.Workspace -> nav.navigate(Routes.WORKSPACE)
                            Feature.Playground -> nav.navigate(Routes.PLAYGROUND)
                            Feature.Notes -> nav.navigate(Routes.NOTES)
                            Feature.Memories -> nav.navigate(Routes.MEMORIES)
                            else -> Unit
                        }
                    },
                )
            }
            composable(Routes.WORKSPACE) {
                WorkspaceScreen(
                    onOpen = { nav.navigate("workspace/${it.route}") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.WORKSPACE_SECTION) { entry ->
                WorkspaceSection.fromRoute(entry.arguments?.getString("section"))?.let { section ->
                    PlannedScreen(stringResource(section.labelRes), section.phase, onBack = { nav.popBackStack() })
                }
            }
            composable(Routes.PLAYGROUND) {
                PlannedScreen(stringResource(R.string.menu_playground), 12, onBack = { nav.popBackStack() })
            }
            composable(Routes.NOTES) {
                PlannedScreen(stringResource(R.string.menu_notes), 12, onBack = { nav.popBackStack() })
            }
            composable(Routes.MEMORIES) {
                PlannedScreen(stringResource(R.string.menu_memories), 12, onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(prefs, onBack = { nav.popBackStack() })
            }
        }
    }
}
