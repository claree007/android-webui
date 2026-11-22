package com.androidwebui.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.androidwebui.android.R
import com.androidwebui.android.core.database.LocalProfile
import com.androidwebui.android.core.prefs.DevicePrefs
import com.androidwebui.android.core.prefs.ThemeMode

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
                HomeScreen(profile, onOpenSettings = { nav.navigate(Routes.SETTINGS) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(prefs, onBack = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun HomeScreen(profile: LocalProfile?, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.home_profile, profile?.name ?: stringResource(R.string.home_profile_loading)),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(stringResource(R.string.home_chat_pending), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.settings_title)) }
    }
}

@Composable
private fun SettingsScreen(prefs: DevicePrefs, onBack: () -> Unit) {
    val mode by prefs.themeMode.collectAsState()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = option == mode, role = Role.RadioButton, onClick = { prefs.setThemeMode(option) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == mode, onClick = null)
                    Text(stringResource(option.labelRes), Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                }
            }
        }
    }
}

private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }
