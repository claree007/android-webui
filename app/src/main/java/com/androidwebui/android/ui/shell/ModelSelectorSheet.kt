package com.androidwebui.android.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidwebui.android.R
import com.androidwebui.android.ui.components.EmptyState
import com.androidwebui.android.ui.components.ErrorState
import com.androidwebui.android.ui.components.LoadingState

/** Searchable model list with a filter chip, a check on the selected row and a "Set as default" action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    state: ModelListState,
    selectedId: String?,
    defaultId: String?,
    onSelect: (ModelOption) -> Unit,
    onSetDefault: (ModelOption) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            when (state) {
                ModelListState.Loading -> LoadingState()
                is ModelListState.Error -> ErrorState(state.message, onRetry = onRetry)
                is ModelListState.Loaded ->
                    if (state.models.isEmpty()) {
                        EmptyState(
                            stringResource(R.string.model_selector_title),
                            hint = stringResource(R.string.model_selector_empty),
                        )
                    } else {
                        ModelList(state.models, selectedId, defaultId, onSelect, onSetDefault)
                    }
            }
        }
    }
}

@Composable
private fun ColumnScope.ModelList(
    models: List<ModelOption>,
    selectedId: String?,
    defaultId: String?,
    onSelect: (ModelOption) -> Unit,
    onSetDefault: (ModelOption) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = models.mapNotNull { it.group }.distinct()
    val visible = models.filterModels(query, group)
    val selected = models.firstOrNull { it.id == selectedId }

    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, Modifier.padding(end = 12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.model_selector_search),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "search" },
            )
        }
        if (groups.isNotEmpty()) GroupFilter(groups, group) { group = it }
    }

    if (visible.isEmpty()) {
        EmptyState(stringResource(R.string.model_selector_no_match))
    } else {
        LazyColumn(Modifier.weight(1f, fill = false).selectableGroup()) {
            items(visible, key = { it.id }) { model ->
                ModelRow(model, isSelected = model.id == selectedId, isDefault = model.id == defaultId, onSelect = { onSelect(model) })
            }
        }
    }

    TextButton(
        onClick = { selected?.let(onSetDefault) },
        enabled = selected != null && selected.id != defaultId,
        modifier = Modifier.align(Alignment.End),
    ) {
        Text(
            stringResource(if (selected != null && selected.id == defaultId) R.string.model_selector_default_set else R.string.model_selector_set_default),
        )
    }
}

@Composable
private fun GroupFilter(groups: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected ?: stringResource(R.string.model_selector_filter_all)) },
            modifier = Modifier.semantics { contentDescription = "filter" },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.model_selector_filter_all)) }, onClick = { open = false; onSelect(null) })
            groups.forEach { g -> DropdownMenuItem(text = { Text(g) }, onClick = { open = false; onSelect(g) }) }
        }
    }
}

@Composable
private fun ModelRow(model: ModelOption, isSelected: Boolean, isDefault: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(model.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(model.group, if (isDefault) stringResource(R.string.model_selector_default_set) else null)
            if (sub.isNotEmpty()) {
                Text(
                    sub.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isSelected) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.model_selector_selected))
        }
    }
}
