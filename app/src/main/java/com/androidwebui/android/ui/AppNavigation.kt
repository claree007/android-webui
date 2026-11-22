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
import com.androidwebui.android.ui.shell.HomeScreen

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(prefs: DevicePrefs, profile: LocalProfile?) {
    val nav = rememberNavController()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.safeDrawingPadding()) {
            composable(Routes.HOME) {
                HomeScreen(profile?.name ?: stringResource(R.string.profile_loading), onOpenSettings = { nav.navigate(Routes.SETTINGS) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(prefs, onBack = { nav.popBackStack() })
            }
        }
    }
}
