package com.androidwebui.android.ui.workspace

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.androidwebui.android.R

/** Workspace areas from the plan: [phase] is where each one is built. */
enum class WorkspaceSection(val route: String, @StringRes val labelRes: Int, val phase: Int) {
    Models("models", R.string.workspace_models, 12),
    Knowledge("knowledge", R.string.workspace_knowledge, 8),
    Prompts("prompts", R.string.workspace_prompts, 6),
    Tools("tools", R.string.workspace_tools, 11),
    Skills("skills", R.string.workspace_skills, 12),
    Functions("functions", R.string.workspace_functions, 11),
    ;

    companion object {
        fun fromRoute(route: String?): WorkspaceSection? = entries.firstOrNull { it.route == route }
    }
}

@Composable
fun WorkspaceScreen(onOpen: (WorkspaceSection) -> Unit, onBack: () -> Unit) {
    SecondaryScaffold(stringResource(R.string.menu_workspace), onBack) {
        Column(Modifier.fillMaxWidth()) {
            WorkspaceSection.entries.forEach { section ->
                Text(
                    stringResource(section.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { onOpen(section) }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
    }
}
