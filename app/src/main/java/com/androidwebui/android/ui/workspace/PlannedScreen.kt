package com.androidwebui.android.ui.workspace

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.androidwebui.android.R
import com.androidwebui.android.ui.components.EmptyState

/** Top bar with a Back button and a title; shared by every secondary screen. */
@Composable
fun SecondaryScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        content()
    }
}

/** A destination whose feature is scheduled for a later phase. It states which phase, so nothing looks broken or silent. */
@Composable
fun PlannedScreen(title: String, phase: Int, onBack: () -> Unit) {
    SecondaryScaffold(title, onBack) {
        EmptyState(
            message = stringResource(R.string.unavailable_body, title),
            hint = stringResource(R.string.planned_in_phase, phase),
            modifier = Modifier.padding(top = 48.dp),
        )
    }
}
