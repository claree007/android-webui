package com.androidwebui.android.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.androidwebui.android.R
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    profileName: String,
    onOpenSettings: () -> Unit,
    sidebar: SidebarState = SidebarState(),
    today: LocalDate = LocalDate.now(),
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf("") }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var unavailable by rememberSaveable { mutableStateOf<Feature?>(null) }

    fun closeDrawer() = scope.launch { drawer.close() }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Sidebar(
                    profileName = profileName,
                    state = sidebar,
                    today = today,
                    onNewChat = { draft = ""; closeDrawer() },
                    onOpenChat = { unavailable = Feature.Chats; closeDrawer() },
                    onSelectModel = { unavailable = Feature.Sending; closeDrawer() },
                    onUnavailable = { if (it == Feature.Search) showSearch = true else unavailable = it; closeDrawer() },
                    onOpenFeature = { unavailable = it; closeDrawer() },
                    onOpenSettings = { closeDrawer(); onOpenSettings() },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                IconButton(onClick = { scope.launch { drawer.open() } }) {
                    Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.open_sidebar))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { unavailable = Feature.ChatControls }) {
                    Icon(painterResource(R.drawable.ic_tune), stringResource(R.string.chat_controls))
                }
            }
            NewChatScreen(
                draft = draft,
                onDraftChange = { draft = it },
                onOpenModels = { showModels = true },
                onUnavailable = { unavailable = it },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showModels) {
        // Model discovery arrives with connections (Phase 4); until then there is nothing to list.
        ModelSelectorSheet(
            state = ModelListState.Loaded(emptyList()),
            selectedId = null,
            defaultId = null,
            onSelect = { showModels = false },
            onSetDefault = {},
            onRetry = {},
            onDismiss = { showModels = false },
        )
    }
    if (showSearch) {
        ChatSearchSheet(sidebar.chats, onOpen = { showSearch = false; unavailable = Feature.Chats }, onDismiss = { showSearch = false })
    }
    unavailable?.let { UnavailableDialog(it, onDismiss = { unavailable = null }) }
}
