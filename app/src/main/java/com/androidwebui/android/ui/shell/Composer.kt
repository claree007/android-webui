package com.androidwebui.android.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.androidwebui.android.R

/** Pill-shaped message composer. The draft is owned by the caller so it survives rotation. */
@Composable
fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    modelLabel: String,
    onOpenModels: () -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val placeholder = stringResource(R.string.composer_placeholder)
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, MaterialTheme.shapes.large)
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.large)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
            if (draft.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.onSurface),
                maxLines = 8,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onAttach) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.composer_attach))
            }
            Box(Modifier.weight(1f))
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onOpenModels)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(modelLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = stringResource(R.string.model_selector_open))
            }
            IconButton(
                onClick = onSend,
                modifier = Modifier.size(48.dp).background(colors.primary, CircleShape),
            ) {
                Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.composer_send), tint = colors.onPrimary)
            }
        }
    }
}
