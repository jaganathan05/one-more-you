package com.onemoreyou.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoreyou.OneMoreYouApp
import com.onemoreyou.domain.LearningPassAllowance
import com.onemoreyou.settings.UninstallLock
import com.onemoreyou.ui.AppLooks
import com.onemoreyou.ui.LocalAppLook
import com.onemoreyou.ui.StatusLine
import com.onemoreyou.ui.LockConfirmDialog
import com.onemoreyou.ui.UnlockPrayers
import com.onemoreyou.ui.WideButton
import com.onemoreyou.ui.limits.RaiseLimitDialog
import com.onemoreyou.ui.limits.SaveParagraphs
import com.onemoreyou.ui.limits.typedByHand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accessibilityOn: Boolean = false,
    val usageOn: Boolean = false,
    val exactAlarmOn: Boolean = false,
    val uninstallLocked: Boolean = false,
    val scheduleLocked: Boolean = false,
    val scheduleReady: Boolean = false,
    val passesPerDay: Int = 1,
    val passMinutes: Int = 10,
    val accent: String = AppLooks.Midnight.id,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as OneMoreYouApp).container
    private val permissions = container.permissions
    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                container.repository.configFlow,
                container.repository.themeFlow,
                container.repository.scheduleLockFlow,
            ) { config, theme, scheduleLocked ->
                Triple(config, theme, scheduleLocked)
            }.collect { (config, theme, scheduleLocked) ->
                _ui.update {
                    it.copy(
                        passesPerDay = config.learningPassesPerDay.coerceIn(0, LearningPassAllowance.MAX_PASSES_PER_DAY),
                        passMinutes = config.learningPassMinutes.coerceIn(1, LearningPassAllowance.MAX_PASS_MINUTES),
                        accent = theme,
                        scheduleLocked = scheduleLocked,
                        scheduleReady = config.monitored.isNotEmpty() && config.sharedSchedule.days.values.any { day ->
                            day.enabled && day.startMinuteOfDay != day.endMinuteOfDay
                        },
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    accessibilityOn = permissions.overlayGranted(),
                    usageOn = permissions.usageAccessGranted(),
                    exactAlarmOn = permissions.exactAlarmsAllowed(),
                    uninstallLocked = UninstallLock.isActive(getApplication()),
                )
            }
        }
    }

    fun saveLearningPass(passesPerDay: Int, minutes: Int) {
        viewModelScope.launch { container.repository.saveLearningPass(passesPerDay, minutes) }
    }

    fun saveTheme(id: String) {
        viewModelScope.launch { container.repository.saveTheme(id) }
    }

    fun setScheduleLocked(locked: Boolean) {
        viewModelScope.launch { container.repository.setScheduleLocked(locked) }
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
        onTheme = model::saveTheme,
        onScheduleLock = model::setScheduleLocked,
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
    onTheme: (String) -> Unit,
    onScheduleLock: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(20.dp)),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Palette, contentDescription = "Theme", tint = MaterialTheme.colorScheme.primary)
            Text("Theme", style = MaterialTheme.typography.titleMedium)
        }
        Text("Color inside the app", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ThemeIcons(selectedId = state.accent, onTheme = onTheme)
        var setup by remember { mutableStateOf(false) }
        var askUninstall by remember { mutableStateOf<Boolean?>(null) }
        var askSchedule by remember { mutableStateOf(false) }
        var unlockPrayer by remember { mutableStateOf(UnlockPrayers.first()) }
        var unlockTyped by remember { mutableStateOf("") }
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
            Text("Stops uninstall", color = MaterialTheme.colorScheme.onSurfaceVariant)
            WideButton(
                if (state.uninstallLocked) "Turn off uninstall lock" else "Turn on uninstall lock",
                onClick = {
                    if (state.uninstallLocked) {
                        unlockPrayer = UnlockPrayers.random()
                        unlockTyped = ""
                    }
                    askUninstall = !state.uninstallLocked
                },
            )
            StatusLine("Schedule lock", if (state.scheduleLocked) "On" else "Off", state.scheduleLocked)
            Text(
                if (state.scheduleReady || state.scheduleLocked) "Stops changes to the schedule" else "Add an app and a day on Schedule first",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WideButton(
                if (state.scheduleLocked) "Unlock schedule" else "Lock schedule",
                enabled = state.scheduleLocked || state.scheduleReady,
                onClick = {
                    if (!state.scheduleLocked && !state.scheduleReady) return@WideButton
                    if (state.scheduleLocked) {
                        unlockPrayer = UnlockPrayers.random()
                        unlockTyped = ""
                    }
                    askSchedule = true
                },
            )
            WideButton("App info", onAppDetails)
            val turningUninstallOn = askUninstall
            if (turningUninstallOn == true) {
                LockConfirmDialog(
                    title = "Turn on uninstall lock",
                    explanation = "The phone will not let this app be removed.",
                    confirmLabel = "Turn on",
                    onDismiss = { askUninstall = null },
                    onConfirmed = {
                        onUninstallLock()
                        askUninstall = null
                    },
                )
            } else if (turningUninstallOn == false) {
                RaiseLimitDialog(
                    title = "Turn off uninstall lock",
                    confirmLabel = "Turn off",
                    paragraph = unlockPrayer,
                    typed = unlockTyped,
                    hint = "Type this prayer below. Turn off opens when it matches.",
                    onTyped = { unlockTyped = if (typedByHand(unlockTyped, it)) it else unlockTyped },
                    onDismiss = { askUninstall = null },
                    onSave = {
                        onDisableUninstallLock()
                        askUninstall = null
                    },
                )
            }
            if (askSchedule && !state.scheduleLocked) {
                LockConfirmDialog(
                    title = "Lock the schedule",
                    explanation = "Days, times, and apps stay as they are.",
                    confirmLabel = "Lock",
                    onDismiss = { askSchedule = false },
                    onConfirmed = {
                        onScheduleLock(true)
                        askSchedule = false
                    },
                )
            } else if (askSchedule && state.scheduleLocked) {
                RaiseLimitDialog(
                    title = "Unlock the schedule",
                    confirmLabel = "Unlock",
                    paragraph = unlockPrayer,
                    typed = unlockTyped,
                    hint = "Type this prayer below. Unlock opens when it matches.",
                    onTyped = { unlockTyped = if (typedByHand(unlockTyped, it)) it else unlockTyped },
                    onDismiss = { askSchedule = false },
                    onSave = {
                        onScheduleLock(false)
                        askSchedule = false
                    },
                )
            }
        } else {
            Text("1. Turn on the three permissions above.")
            Text("2. On Schedule, pick the days, the hours, and the apps.")
            Text("3. On Limits, set how long each app can stay open today.")
            Text("4. Lock the schedule when you are done.")
        }
    }
}

@Composable
internal fun LearningPassChoice(passes: Int, minutes: Int, onSave: (Int, Int) -> Unit) {
    var ask by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(passes to minutes) }
    var paragraph by remember { mutableStateOf(SaveParagraphs.first()) }
    var typed by remember { mutableStateOf("") }
    fun choose(nextPasses: Int, nextMinutes: Int) {
        if (nextPasses == passes && nextMinutes == minutes) return
        pending = nextPasses to nextMinutes
        paragraph = SaveParagraphs.random()
        typed = ""
        ask = true
    }
    Text("Learning pass", style = MaterialTheme.typography.titleMedium)
    Text(
        "During the schedule hours, one pass can open an app. After that app's daily time is used up, the pass does not open it. The same pass applies to every app in the schedule. Changing it asks you to type a paragraph.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(0 to "Off", 1 to "1", 2 to "2").forEach { (count, label) ->
            ChoiceChip(label, passes == count, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                choose(count, minutes)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(5, 10, 15).forEach { option ->
            ChoiceChip("${option}m", minutes == option, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                choose(passes, option)
            }
        }
    }
    if (ask) {
        RaiseLimitDialog(
            title = "Change the learning pass",
            confirmLabel = "Save",
            paragraph = paragraph,
            typed = typed,
            onTyped = { typed = if (typedByHand(typed, it)) it else typed },
            onDismiss = { ask = false },
            onSave = {
                onSave(pending.first, pending.second)
                ask = false
            },
        )
    }
}

@Composable
private fun ThemeIcons(selectedId: String, onTheme: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppLooks.all.forEach { theme ->
            val selected = AppLooks.fromId(selectedId).id == theme.id
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTheme(theme.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(theme.accent)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.28f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "${theme.name} selected",
                            tint = theme.onAccent,
                        )
                    }
                }
                Text(
                    theme.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) theme.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, on: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (on) color else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (on) Color(0xFF1A1208) else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
