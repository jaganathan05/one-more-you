package com.onemoreyou.ui.apps

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoreyou.OneMoreYouApp
import com.onemoreyou.blocking.BlockText
import com.onemoreyou.domain.AppSchedule
import com.onemoreyou.domain.DayWindow
import com.onemoreyou.domain.LearningPassAllowance
import com.onemoreyou.domain.MonitoredApp
import com.onemoreyou.domain.Weekday
import com.onemoreyou.ui.AppIcon
import com.onemoreyou.ui.LocalAppLook
import com.onemoreyou.ui.LockConfirmDialog
import com.onemoreyou.ui.UnlockPrayers
import com.onemoreyou.ui.QuietButton
import com.onemoreyou.ui.WideButton
import com.onemoreyou.ui.limits.RaiseLimitDialog
import com.onemoreyou.ui.limits.SaveParagraphs
import com.onemoreyou.ui.limits.typedByHand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppsUiState(
    val installed: List<MonitoredApp> = emptyList(),
    val selected: List<MonitoredApp> = emptyList(),
    val schedule: AppSchedule = AppSchedule.sharedDefault(),
    val allowances: List<LearningPassAllowance> = emptyList(),
    val locked: Boolean = false,
    val loading: Boolean = true,
)

class AppsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as OneMoreYouApp).container
    private val installed = MutableStateFlow<List<MonitoredApp>>(emptyList())

    val ui = combine(installed, container.repository.configFlow, container.repository.scheduleLockFlow) { apps, config, locked ->
        AppsUiState(
            installed = apps,
            selected = config.monitored,
            schedule = config.sharedSchedule,
            allowances = config.allowances,
            locked = locked,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    init {
        viewModelScope.launch {
            installed.value = withContext(Dispatchers.IO) { container.installedApps.launchableApps() }
        }
    }

    fun setLocked(locked: Boolean) {
        viewModelScope.launch { container.repository.setScheduleLocked(locked) }
    }

    fun add(app: MonitoredApp) = viewModelScope.launch {
        if (container.repository.isScheduleLocked()) return@launch
        val current = container.repository.snapshot().monitored
        if (current.any { it.packageName == app.packageName }) return@launch
        container.repository.setMonitored(current + app)
    }

    fun remove(packageName: String) = viewModelScope.launch {
        if (container.repository.isScheduleLocked()) return@launch
        val current = container.repository.snapshot().monitored
        container.repository.setMonitored(current.filter { it.packageName != packageName })
    }

    fun saveAppPass(packageName: String, passesPerDay: Int, minutes: Int) = viewModelScope.launch {
        if (container.repository.isScheduleLocked()) return@launch
        container.repository.saveAllowance(
            LearningPassAllowance(
                packageName = packageName,
                passesPerDay = passesPerDay.coerceIn(0, LearningPassAllowance.MAX_PASSES_PER_DAY),
                durationMinutes = minutes.coerceIn(1, LearningPassAllowance.MAX_PASS_MINUTES),
            ),
        )
    }

    fun toggleDay(day: Weekday) = edit { schedule ->
        val window = schedule.days[day] ?: DayWindow(startMinuteOfDay = hours(schedule).first, endMinuteOfDay = hours(schedule).second)
        val (start, end) = hours(schedule)
        val next = schedule.days.toMutableMap()
        next[day] = window.copy(enabled = !window.enabled, startMinuteOfDay = start, endMinuteOfDay = end)
        schedule.copy(days = next)
    }

    fun setStart(minute: Int) = edit { schedule ->
        schedule.copy(days = schedule.days.mapValues { (_, window) -> window.copy(startMinuteOfDay = minute) })
    }

    fun setEnd(minute: Int) = edit { schedule ->
        schedule.copy(days = schedule.days.mapValues { (_, window) -> window.copy(endMinuteOfDay = minute) })
    }

    fun setDays(on: Set<Weekday>) = edit { schedule ->
        val (start, end) = hours(schedule)
        schedule.copy(days = Weekday.entries.associateWith { day ->
            DayWindow(enabled = day in on, startMinuteOfDay = start, endMinuteOfDay = end)
        })
    }

    private fun edit(transform: (AppSchedule) -> AppSchedule) {
        viewModelScope.launch {
            if (container.repository.isScheduleLocked()) return@launch
            val current = container.repository.snapshot().sharedSchedule
            container.repository.saveSharedSchedule(transform(current))
        }
    }

    private fun hours(schedule: AppSchedule): Pair<Int, Int> {
        val sample = schedule.days.values.firstOrNull { it.enabled } ?: schedule.days.values.firstOrNull()
        return (sample?.startMinuteOfDay ?: 9 * 60) to (sample?.endMinuteOfDay ?: 19 * 60)
    }
}

@Composable
fun AppControlsRoute(model: AppsViewModel = viewModel()) {
    val state by model.ui.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var passApp by rememberSaveable { mutableStateOf<String?>(null) }
    var askLock by rememberSaveable { mutableStateOf(false) }
    var unlockPrayer by rememberSaveable { mutableStateOf(UnlockPrayers.first()) }
    var unlockTyped by rememberSaveable { mutableStateOf("") }
    val locked = state.locked
    val canLock = state.selected.isNotEmpty() &&
        state.schedule.days.values.any { it.enabled && it.startMinuteOfDay != it.endMinuteOfDay }
    val hours = state.schedule.days.values.firstOrNull { it.enabled } ?: state.schedule.days.values.firstOrNull()
    val start = hours?.startMinuteOfDay ?: 9 * 60
    val end = hours?.endMinuteOfDay ?: 19 * 60
    val selectedIds = state.selected.map { it.packageName }.toSet()
    val available = state.installed
        .filter { it.packageName !in selectedIds && (query.isBlank() || it.label.contains(query, ignoreCase = true)) }
        .sortedBy { it.label.lowercase() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val look = LocalAppLook.current
        Text("Schedule", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (locked) "Locked. Stay with it." else "Pick when apps close. Then lock it.",
            color = look.muted,
        )
        if (!locked) {
            val daysOn = state.schedule.days.filterValues { it.enabled }.keys
            val dayText = if (daysOn.isEmpty()) "no days" else daysOn.joinToString(", ") { it.name.take(3) }
            val names = state.selected.map { it.label }
            val who = when (names.size) {
                0 -> "Your schedule is empty. Follow steps 1, 2 and 3 below."
                else -> "${names.joinToString(" and ")} close on $dayText, ${BlockText.minuteOfDay(start)} – ${BlockText.minuteOfDay(end)}."
            }
            Column(Modifier.fillMaxWidth().background(look.accentSoft, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("YOUR SCHEDULE", color = look.muted, style = MaterialTheme.typography.labelLarge)
                Text(who)
                Text(
                    when {
                        state.selected.isEmpty() -> "Add an app to lock this"
                        daysOn.isEmpty() -> "Turn on a day to lock this"
                        start == end -> "Start and end can't be the same"
                        else -> "Ready to lock"
                    },
                    color = if (canLock) look.good else look.warning,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1 Which days?", style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Weekday.displayOrder.forEach { day ->
                WideDay(
                    label = day.name.take(3),
                    on = state.schedule.days[day]?.enabled == true,
                    enabled = !locked,
                    modifier = Modifier.weight(1f),
                    onClick = { model.toggleDay(day) },
                )
            }
        }
        if (!locked) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                QuietButton("Weekdays", onClick = { model.setDays(setOf(Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY, Weekday.THURSDAY, Weekday.FRIDAY)) })
                QuietButton("Every day", onClick = { model.setDays(Weekday.entries.toSet()) })
                QuietButton("Weekend", onClick = { model.setDays(setOf(Weekday.SATURDAY, Weekday.SUNDAY)) })
            }
        }
        Text("2 Which hours?", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeField("Starts", start, Modifier.weight(1f), !locked, model::setStart)
            TimeField("Ends", end, Modifier.weight(1f), !locked, model::setEnd)
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("3 Which apps?", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        onClick = {
                            query = ""
                            adding = true
                        },
                        enabled = !locked,
                    ) { Text("Add app") }
                }
            }
            if (state.selected.isEmpty()) {
                item {
                    Text(
                        "No apps yet. Tap Add app.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.selected, key = { "on-${it.packageName}" }) { app ->
                val allowance = state.allowances.find { it.packageName == app.packageName }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    AppIcon(app.packageName, app.label)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                        Text(passLabel(allowance), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { passApp = app.packageName }, enabled = !locked) { Text("Pass") }
                    IconButton(onClick = { model.remove(app.packageName) }, enabled = !locked) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove ${app.label}",
                            tint = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item {
                WideButton(
                    if (locked) "Unlock schedule" else "Lock schedule",
                    enabled = locked || canLock,
                    onClick = {
                        if (!locked && !canLock) return@WideButton
                        if (locked) {
                            unlockPrayer = UnlockPrayers.random()
                            unlockTyped = ""
                        }
                        askLock = true
                    },
                )
                Text(
                    if (locked) "Unlocking asks you to type a prayer." else "Locking is quick. To unlock later you will type a prayer.",
                    color = LocalAppLook.current.faint,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    val editing = state.selected.find { it.packageName == passApp }
    if (editing != null && !locked) {
        PassPopup(
            app = editing,
            allowance = state.allowances.find { it.packageName == editing.packageName },
            onDismiss = { passApp = null },
            onSave = { passes, minutes ->
                model.saveAppPass(editing.packageName, passes, minutes)
                passApp = null
            },
        )
    }
    if (askLock && locked) {
        RaiseLimitDialog(
            title = "Unlock the schedule",
            confirmLabel = "Unlock",
            paragraph = unlockPrayer,
            typed = unlockTyped,
            hint = "Type this prayer below. Unlock opens when it matches.",
            onTyped = { unlockTyped = if (typedByHand(unlockTyped, it)) it else unlockTyped },
            onDismiss = { askLock = false },
            onSave = {
                model.setLocked(false)
                askLock = false
            },
        )
    } else if (askLock) {
        LockConfirmDialog(
            title = "Lock the schedule",
            explanation = "Days, times, and apps stay as they are.",
            confirmLabel = "Lock",
            onDismiss = { askLock = false },
            onConfirmed = {
                model.setLocked(true)
                askLock = false
            },
        )
    }
    if (adding && !locked) {
        AddAppPopup(
            apps = available,
            query = query,
            onQuery = { query = it },
            onAdd = { model.add(it) },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun PassPopup(
    app: MonitoredApp,
    allowance: LearningPassAllowance?,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit,
) {
    val savedPasses = allowance?.passesPerDay?.coerceIn(0, LearningPassAllowance.MAX_PASSES_PER_DAY) ?: 0
    val savedMinutes = allowance?.durationMinutes?.coerceIn(5, LearningPassAllowance.MAX_PASS_MINUTES) ?: 10
    var passes by rememberSaveable(app.packageName) { mutableStateOf(savedPasses) }
    var minutes by rememberSaveable(app.packageName) { mutableStateOf(if (savedMinutes in listOf(5, 10, 15)) savedMinutes else 10) }
    var ask by rememberSaveable(app.packageName) { mutableStateOf(false) }
    var paragraph by rememberSaveable(app.packageName) { mutableStateOf(SaveParagraphs.first()) }
    var typed by rememberSaveable(app.packageName) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Learning pass") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    "A short open during these hours. Off means no extra time.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(0 to "Off", 1 to "1", 2 to "2").forEach { (count, label) ->
                        PassChip(label, passes == count, Modifier.weight(1f)) { passes = count }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(5, 10, 15).forEach { option ->
                        PassChip("${option}m", minutes == option, Modifier.weight(1f)) { minutes = option }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (passes == savedPasses && (passes == 0 || minutes == savedMinutes)) {
                    onDismiss()
                } else {
                    paragraph = SaveParagraphs.random()
                    typed = ""
                    ask = true
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (ask) {
        RaiseLimitDialog(
            title = "Change the learning pass",
            confirmLabel = "Save",
            paragraph = paragraph,
            typed = typed,
            onTyped = { typed = if (typedByHand(typed, it)) it else typed },
            onDismiss = { ask = false },
            onSave = { onSave(passes, minutes) },
        )
    }
}

@Composable
private fun PassChip(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) { Text(label, style = MaterialTheme.typography.bodySmall) }
}

private fun passLabel(allowance: LearningPassAllowance?): String {
    val passes = allowance?.passesPerDay ?: 0
    if (passes <= 0) return "Pass off"
    return "$passes × ${allowance?.durationMinutes ?: 10} min"
}

@Composable
private fun AddAppPopup(
    apps: List<MonitoredApp>,
    query: String,
    onQuery: (String) -> Unit,
    onAdd: (MonitoredApp) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Add an app", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Scroll the list and tap an app. It joins the schedule.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search") },
                    singleLine = true,
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 320.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        TextButton(
                            onClick = { onAdd(app) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp),
                        ) {
                            AppIcon(app.packageName, app.label)
                            Text(
                                app.label,
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}

@Composable
private fun WideDay(
    label: String,
    on: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TimeField(label: String, minute: Int, modifier: Modifier, enabled: Boolean, onPick: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WideButton(BlockText.minuteOfDay(minute), onClick = { if (enabled) open = true }, enabled = enabled)
    }
    if (open) {
        ClockDialog(minute, { open = false }) {
            open = false
            onPick(it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockDialog(minute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val picker = rememberTimePickerState(minute / 60, minute % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(picker.hour * 60 + picker.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = picker) },
    )
}
