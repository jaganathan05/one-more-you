package com.onemoreyou.ui.schedule

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.onemoreyou.domain.MonitoredApp
import com.onemoreyou.domain.Weekday
import com.onemoreyou.ui.WideButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScheduleUiState(
    val apps: List<MonitoredApp> = emptyList(),
    val selectedPackage: String? = null,
    val schedule: AppSchedule? = null,
)

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as OneMoreYouApp).container.repository
    private val selected = MutableStateFlow<String?>(null)

    val ui = combine(repository.configFlow, selected) { config, selectedPackage ->
        val pkg = selectedPackage?.takeIf { id -> config.monitored.any { it.packageName == id } }
            ?: config.monitored.firstOrNull()?.packageName
        ScheduleUiState(
            apps = config.monitored,
            selectedPackage = pkg,
            schedule = pkg?.let { config.scheduleFor(it) ?: AppSchedule(it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    fun select(packageName: String) {
        selected.value = packageName
    }

    init {
        viewModelScope.launch {
            repository.snapshot().schedules
                .filter { it.enabled && it.days.values.none { day -> day.enabled } }
                .forEach { schedule ->
                    repository.saveSchedule(schedule.copy(enabled = true, days = defaultDays(schedule)))
                }
        }
    }

    fun setEnabled(enabled: Boolean) = edit { schedule ->
        if (!enabled) {
            schedule.copy(enabled = false)
        } else {
            schedule.copy(enabled = true, days = defaultDays(schedule))
        }
    }

    fun setDay(day: Weekday, window: DayWindow) = edit { schedule ->
        schedule.copy(days = schedule.days + (day to window))
    }

    private fun defaultDays(schedule: AppSchedule): Map<Weekday, DayWindow> {
        if (schedule.days.values.any { it.enabled }) return schedule.days
        return Weekday.entries.associateWith { day ->
            (schedule.days[day] ?: DayWindow()).copy(enabled = true)
        }
    }

    private fun edit(transform: (AppSchedule) -> AppSchedule) {
        viewModelScope.launch {
            val state = ui.value
            val pkg = state.selectedPackage ?: return@launch
            val current = state.schedule ?: AppSchedule(pkg)
            repository.saveSchedule(transform(current))
        }
    }
}

@Composable
fun ScheduleRoute(model: ScheduleViewModel = viewModel()) {
    val state by model.ui.collectAsState()
    ScheduleScreen(state, model::select, model::setEnabled, model::setDay)
}

@Composable
private fun ScheduleScreen(
    state: ScheduleUiState,
    onSelect: (String) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onDay: (Weekday, DayWindow) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(20.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Schedule", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Each day has its own window. If the end time is earlier than the start time, the block continues overnight.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.apps.isEmpty()) {
            Text("Select apps in App Controls before setting a schedule.")
            return@Column
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.apps.forEach { app ->
                FilterChip(
                    selected = app.packageName == state.selectedPackage,
                    onClick = { onSelect(app.packageName) },
                    label = { Text(app.label) },
                )
            }
        }
        val schedule = state.schedule ?: return@Column
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Schedule enabled", modifier = Modifier.weight(1f))
            Switch(checked = schedule.enabled, onCheckedChange = onEnabled)
        }
        Weekday.displayOrder.forEach { day ->
            val window = schedule.days[day] ?: DayWindow()
            DayEditor(day, window) { onDay(day, it) }
        }
    }
}

@Composable
private fun DayEditor(day: Weekday, window: DayWindow, onChange: (DayWindow) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(day.label(), modifier = Modifier.weight(1f))
            Switch(checked = window.enabled, onCheckedChange = { onChange(window.copy(enabled = it)) })
        }
        TimeButton("Start ${BlockText.minuteOfDay(window.startMinuteOfDay)}", window.startMinuteOfDay) {
            onChange(window.copy(startMinuteOfDay = it))
        }
        TimeButton("End ${BlockText.minuteOfDay(window.endMinuteOfDay)}", window.endMinuteOfDay) {
            onChange(window.copy(endMinuteOfDay = it))
        }
    }
}

private fun Weekday.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun TimeButton(label: String, minute: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    WideButton(label, onClick = { open = true })
    if (open) {
        MinutePickerDialog(
            initialMinute = minute,
            onDismiss = { open = false },
            onConfirm = {
                open = false
                onPick(it)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinutePickerDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val picker = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(picker.hour * 60 + picker.minute) }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = picker) },
    )
}
