package com.androidwebui.android.ui.shell

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.androidwebui.android.R

@Composable
fun UnavailableDialog(feature: Feature, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unavailable_title)) },
        text = { Text(stringResource(R.string.unavailable_body, stringResource(feature.labelRes))) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
