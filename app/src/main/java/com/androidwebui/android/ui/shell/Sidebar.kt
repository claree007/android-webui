package com.androidwebui.android.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.androidwebui.android.R

/** Native drawer equivalent of the web app's sidebar. Sections that need later phases say so. */
@Composable
fun Sidebar(
    profileName: String,
    onNewChat: () -> Unit,
    onUnavailable: (Feature) -> Unit,
    onOpenFeature: (Feature) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        SidebarRow(R.drawable.ic_add, stringResource(R.string.new_chat), onNewChat)
        SidebarRow(R.drawable.ic_search, stringResource(R.string.search)) { onUnavailable(Feature.Search) }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.sidebar_models))
            SectionNote(stringResource(R.string.sidebar_models_empty))
            SectionHeader(stringResource(R.string.sidebar_folders))
            SectionNote(stringResource(R.string.sidebar_folders_unavailable))
            SectionHeader(stringResource(R.string.sidebar_chats))
            SectionNote(stringResource(R.string.sidebar_chats_empty))
        }

        UserRow(profileName, onOpenFeature, onOpenSettings)
    }
}

@Composable
private fun SidebarRow(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(iconRes), contentDescription = null, Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun UserRow(profileName: String, onOpenFeature: (Feature) -> Unit, onOpenSettings: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.user_menu)
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button) { expanded = true }
                .semantics { contentDescription = "$menuLabel: $profileName" }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(profileName.take(1).uppercase(), style = MaterialTheme.typography.labelLarge) }
            Text(profileName, style = MaterialTheme.typography.bodyLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                Feature.Workspace to R.string.menu_workspace,
                Feature.Playground to R.string.menu_playground,
                Feature.Notes to R.string.menu_notes,
                Feature.Memories to R.string.menu_memories,
            ).forEach { (feature, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                    expanded = false
                    onOpenFeature(feature)
                })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_title)) }, onClick = {
                expanded = false
                onOpenSettings()
            })
        }
    }
}
