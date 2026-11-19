package com.onemoreyou.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

internal val UnlockPrayers = listOf(
    "I will reduce my screen time. I ask for the strength to put the phone down, keep the limits I chose, and open this lock only when I truly need to. Amen.",
    "This is my prayer. I will reduce my screen time today, stay away from the apps I locked, and type these words before the lock opens. Amen.",
    "I promise myself I will reduce my screen time. I will not undo this lock on a whim. I type this prayer, word for word, and then I may unlock. Amen.",
)

@Composable
internal fun LockConfirmDialog(
    title: String,
    explanation: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(explanation) },
        confirmButton = { TextButton(onClick = onConfirmed) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
