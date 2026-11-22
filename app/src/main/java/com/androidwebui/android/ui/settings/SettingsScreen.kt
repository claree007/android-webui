package com.androidwebui.android.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.androidwebui.android.R
import com.androidwebui.android.core.prefs.DevicePrefs
import com.androidwebui.android.core.prefs.ThemeMode

private enum class SettingsTab(@StringRes val labelRes: Int, val phase: Int = 0) {
    General(R.string.settings_tab_general),
    Interface(R.string.settings_tab_interface, phase = 6),
    Notifications(R.string.settings_tab_notifications, phase = 5),
    Connections(R.string.settings_tab_connections, phase = 4),
    Integrations(R.string.settings_tab_integrations, phase = 7),
    Audio(R.string.settings_tab_audio, phase = 9),
    Personalization(R.string.settings_tab_personalization, phase = 12),
    Data(R.string.settings_tab_data, phase = 12),
}

/** Full-height settings with a Back chip and horizontally scrolling tabs (D15). Only General is built so far. */
@Composable
fun SettingsScreen(prefs: DevicePrefs, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(SettingsTab.General) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = onBack,
                label = { Text(stringResource(R.string.back)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_back), contentDescription = null) },
            )
            SettingsTab.entries.forEach { entry ->
                FilterChip(
                    selected = entry == tab,
                    onClick = { tab = entry },
                    label = { Text(stringResource(entry.labelRes)) },
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(stringResource(tab.labelRes), style = MaterialTheme.typography.headlineSmall)
            if (tab == SettingsTab.General) {
                ThemeSetting(prefs)
            } else {
                Text(
                    stringResource(R.string.unavailable_body, stringResource(tab.labelRes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    stringResource(R.string.planned_in_phase, tab.phase),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThemeSetting(prefs: DevicePrefs) {
    val mode by prefs.themeMode.collectAsState()
    Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    Text(
        stringResource(R.string.theme_summary),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(Modifier.selectableGroup()) {
        ThemeMode.entries.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == mode, role = Role.RadioButton, onClick = { prefs.setThemeMode(option) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == mode, onClick = null)
                Text(stringResource(option.labelRes), Modifier.padding(start = 12.dp, top = 14.dp, bottom = 14.dp))
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
