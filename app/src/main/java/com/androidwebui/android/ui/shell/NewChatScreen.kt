package com.androidwebui.android.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.androidwebui.android.R

/** Empty state: title and composer, vertically centered. Scrolls so landscape and large text stay reachable. */
@Composable
fun NewChatScreen(
    draft: String,
    onDraftChange: (String) -> Unit,
    onOpenModels: () -> Unit,
    onUnavailable: (Feature) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.model_selector_none),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        Composer(
            draft = draft,
            onDraftChange = onDraftChange,
            modelLabel = stringResource(R.string.model_selector_none),
            onOpenModels = onOpenModels,
            onAttach = { onUnavailable(Feature.Attachments) },
            onSend = { onUnavailable(Feature.Sending) },
        )
    }
}
