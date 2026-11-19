package com.onemoreyou.ui.apps

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.onemoreyou.ui.AppIcon
import com.onemoreyou.ui.WideButton
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
    val loading: Boolean = true,
)

class AppsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as OneMoreYouApp).container
    private val installed = MutableStateFlow<List<MonitoredApp>>(emptyList())

    val ui = combine(installed, container.repository.configFlow) { apps, config ->
        AppsUiState(
            installed = apps,
            selected = config.monitored,
            schedule = config.sharedSchedule,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    init {
        viewModelScope.launch {
            installed.value = withContext(Dispatchers.IO) { container.installedApps.launchableApps() }
        }
    }

    fun add(app: MonitoredApp) = viewModelScope.launch {
        val current = container.repository.snapshot().monitored
        if (current.any { it.packageName == app.packageName }) return@launch
        container.repository.setMonitored(current + app)
    }

    fun remove(packageName: String) = viewModelScope.launch {
        val current = container.repository.snapshot().monitored
        container.repository.setMonitored(current.filter { it.packageName != packageName })
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

    private fun edit(transform: (AppSchedule) -> AppSchedule) {
        viewModelScope.launch {
            val current = container.repository.snapshot().sharedSchedule
            container.repository.saveSharedSchedule(transform(current))
        }
    }

    private fun hours(schedule: AppSchedule): Pair<Int, Int> {
        val sample = schedule.days.values.firstOrNull { it.enabled } ?: schedule.days.values.firstOrNull()
        return (sample?.startMinuteOfDay ?: 21 * 60) to (sample?.endMinuteOfDay ?: 7 * 60)
    }
}

@Composable
fun AppControlsRoute(model: AppsViewModel = viewModel()) {
    val state by model.ui.collectAsState()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val hours = state.schedule.days.values.firstOrNull { it.enabled } ?: state.schedule.days.values.firstOrNull()
    val start = hours?.startMinuteOfDay ?: 21 * 60
    val end = hours?.endMinuteOfDay ?: 7 * 60
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(20.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Apps", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Text("One schedule", style = MaterialTheme.typography.headlineSmall)
        Text(
            "These hours close every app you add. Saturday and Sunday start off.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Weekday.displayOrder.forEach { day ->
                val on = state.schedule.days[day]?.enabled == true
                WideDay(
                    label = day.name.take(3),
                    on = on,
                    modifier = Modifier.weight(1f),
                    onClick = { model.toggleDay(day) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeField("Start", start, Modifier.weight(1f), model::setStart)
            TimeField("End", end, Modifier.weight(1f), model::setEnd)
        }
        state.selected.forEach { app ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AppIcon(app.packageName, app.label)
                Text(app.label, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                TextButton(onClick = { model.remove(app.packageName) }) { Text("Remove") }
            }
        }
        TextButton(onClick = { searching = true }) { Text("Add app") }
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search") },
                singleLine = true,
            )
            state.installed.filter { app ->
                app.packageName !in state.selected.map { it.packageName } &&
                    (query.isBlank() || app.label.contains(query, ignoreCase = true))
            }.take(8).forEach { app ->
                TextButton(onClick = {
                    model.add(app)
                    searching = false
                    query = ""
                }) {
                    AppIcon(app.packageName, app.label)
                    Text(app.label, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun WideDay(
    label: String,
    on: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (on) Color(0xFFFF6B1A) else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (on) Color(0xFF2A1206) else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TimeField(label: String, minute: Int, modifier: Modifier, onPick: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WideButton(BlockText.minuteOfDay(minute), onClick = { open = true })
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
