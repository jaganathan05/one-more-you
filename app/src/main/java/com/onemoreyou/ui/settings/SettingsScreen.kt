package com.onemoreyou.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoreyou.OneMoreYouApp
import com.onemoreyou.settings.UninstallLock
import com.onemoreyou.ui.StatusLine
import com.onemoreyou.ui.WideButton
import com.onemoreyou.ui.limits.RaiseLimitDialog
import com.onemoreyou.ui.limits.SaveParagraphs
import com.onemoreyou.ui.limits.typedByHand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accessibilityOn: Boolean = false,
    val usageOn: Boolean = false,
    val exactAlarmOn: Boolean = false,
    val uninstallLocked: Boolean = false,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val permissions = (app as OneMoreYouApp).container.permissions
    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = SettingsUiState(
                accessibilityOn = permissions.overlayGranted(),
                usageOn = permissions.usageAccessGranted(),
                exactAlarmOn = permissions.exactAlarmsAllowed(),
                uninstallLocked = UninstallLock.isActive(getApplication()),
            )
        }
    }
}

@Composable
fun SettingsRoute(
    onAccessibility: () -> Unit,
    onUsage: () -> Unit,
    onExactAlarm: () -> Unit,
    onAppDetails: () -> Unit,
    model: SettingsViewModel = viewModel(),
) {
    val state by model.ui.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    SettingsScreen(
        state,
        onAccessibility,
        onUsage,
        onExactAlarm,
        onAppDetails,
        onUninstallLock = { context.startActivity(UninstallLock.enableIntent(context)) },
        onDisableUninstallLock = {
            UninstallLock.disable(context)
            model.refresh()
        },
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onAccessibility: () -> Unit,
    onUsage: () -> Unit,
    onExactAlarm: () -> Unit,
    onAppDetails: () -> Unit,
    onUninstallLock: () -> Unit,
    onDisableUninstallLock: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(20.dp)),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        var setup by remember { mutableStateOf(false) }
        var askOff by remember { mutableStateOf(false) }
        var paragraph by remember { mutableStateOf(SaveParagraphs.first()) }
        var typed by remember { mutableStateOf("") }
        Row {
            TextButton(onClick = { setup = false }) {
                Text("Permissions", color = if (!setup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { setup = true }) {
                Text("Setup", color = if (setup) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!setup) {
            StatusLine("Display over apps", if (state.accessibilityOn) "On" else "Off", state.accessibilityOn)
            StatusLine("Usage access", if (state.usageOn) "On" else "Off", state.usageOn)
            StatusLine("Exact alarms", if (state.exactAlarmOn) "Allowed" else "Not allowed", state.exactAlarmOn)
            if (!state.accessibilityOn) WideButton("Allow display over apps", onAccessibility)
            if (!state.usageOn) WideButton("Allow usage access", onUsage)
            if (!state.exactAlarmOn) WideButton("Allow exact alarms", onExactAlarm)
            StatusLine("Uninstall lock", if (state.uninstallLocked) "On" else "Off", state.uninstallLocked)
            Text(
                "While this is on, the phone blocks uninstall. Turning it off needs the paragraph.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WideButton(
                if (state.uninstallLocked) "Turn off uninstall lock" else "Turn on uninstall lock",
                onClick = {
                    if (state.uninstallLocked) {
                        paragraph = SaveParagraphs.random()
                        typed = ""
                        askOff = true
                    } else {
                        onUninstallLock()
                    }
                },
            )
            WideButton("App info", onAppDetails)
            if (askOff) {
                RaiseLimitDialog(
                    title = "Turn off uninstall lock",
                    confirmLabel = "Turn off",
                    paragraph = paragraph,
                    typed = typed,
                    onTyped = { typed = if (typedByHand(typed, it)) it else typed },
                    onDismiss = { askOff = false },
                    onSave = {
                        onDisableUninstallLock()
                        askOff = false
                    },
                )
            }
        } else {
            Text("1. Allow display over other apps and usage access.")
            Text("2. Add apps on the Apps tab. One schedule closes all of them on the days you turn on.")
            Text("3. Pick any installed app on Limits and set one daily limit, or a limit for each weekday.")
            Text("4. On iQOO, allow autostart and high background power, then lock ONE MORE YOU in recent apps.")
            Text("5. Open the app once after a reboot. Turn on the uninstall lock here if the app should stay installed.")
        }
    }
}
