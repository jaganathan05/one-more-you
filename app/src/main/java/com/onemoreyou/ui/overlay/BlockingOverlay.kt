package com.onemoreyou.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onemoreyou.blocking.BlockScreenState
import com.onemoreyou.blocking.BlockText
import com.onemoreyou.ui.QuietButton
import com.onemoreyou.ui.WideButton

@Composable
fun BlockingOverlay(
    state: BlockScreenState,
    onConfirmPass: () -> Unit,
    onLeave: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "PAUSED",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                state.appLabel,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            state.reasons.forEach { reason ->
                Text(reason, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(6.dp))
            state.details.forEach { detail ->
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                passLine(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            if (state.canRequestPass) {
                WideButton(
                    text = "Start ${state.passDurationMinutes} min pass",
                    onClick = onConfirmPass,
                )
                Spacer(Modifier.height(10.dp))
            } else if (state.passesConfigured && state.remainingPasses == 0 && !state.limitBlocksPass) {
                WideButton(
                    text = "No learning passes left today",
                    onClick = onConfirmPass,
                    enabled = false,
                )
                Spacer(Modifier.height(10.dp))
            }
            QuietButton("Leave app", onLeave)
            Spacer(Modifier.height(28.dp))
            Text(
                "Leave this app. A learning pass can open it during the schedule. After today's time for this app is used up, the pass does not open it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun CountdownBanner(appLabel: String, remainingMillis: Long) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
    ) {
        Text(
            "$appLabel only · ${BlockText.clock(remainingMillis)}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private fun passLine(state: BlockScreenState): String = when {
    !state.passesConfigured -> "Learning pass is off."
    state.limitBlocksPass -> "Today's time for this app is used up, so a pass cannot open it."
    state.remainingPasses <= 0 -> "No learning passes left today."
    else -> "${state.remainingPasses} ${if (state.remainingPasses == 1) "pass" else "passes"} left today, ${state.passDurationMinutes} min each. A pass opens only this app during the schedule."
}
