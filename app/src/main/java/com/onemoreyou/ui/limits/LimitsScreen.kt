package com.onemoreyou.ui.limits

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoreyou.OneMoreYouApp
import com.onemoreyou.domain.AppUsageLimit
import com.onemoreyou.domain.MonitoredApp
import com.onemoreyou.domain.Weekday
import com.onemoreyou.ui.AppIcon
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val SaveParagraphs = listOf(
    "I am raising this limit on purpose. Extra minutes let me stay longer, so I type every word, comma, and number before it is saved. Confirm mark: 19 Nov, code 4K-27.",
    "A higher limit is a real choice, not a quick tap. I copy this full line, including the capital letters and the semicolon; the saved time must wait until it matches. Check 08:45.",
    "Before the longer time is stored, I type this paragraph exactly. Spaces count, the hyphen counts, and the end mark counts. One More You, limit raise, token R-316.",
)

data class AddedLimit(
    val app: MonitoredApp,
    val summary: String,
)

data class LimitsUiState(
    val installed: List<MonitoredApp> = emptyList(),
    val added: List<AddedLimit> = emptyList(),
    val editing: MonitoredApp? = null,
    val limit: AppUsageLimit? = null,
)

class LimitsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as OneMoreYouApp).container
    private val installed = MutableStateFlow<List<MonitoredApp>>(emptyList())
    private val selected = MutableStateFlow<String?>(null)

    val ui = combine(installed, container.repository.configFlow, selected) { apps, config, picked ->
        val added = config.limits.filter { it.uniformMinutes != null || it.minutesByDay.isNotEmpty() }.map { limit ->
            val app = apps.firstOrNull { it.packageName == limit.packageName }
                ?: MonitoredApp(limit.packageName, config.labelOf(limit.packageName))
            AddedLimit(app, summary(limit))
        }
        val editing = picked?.let { id ->
            apps.firstOrNull { it.packageName == id } ?: MonitoredApp(id, config.labelOf(id))
        }
        LimitsUiState(
            installed = apps,
            added = added,
            editing = editing,
            limit = editing?.let { config.limitFor(it.packageName) ?: AppUsageLimit(it.packageName) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LimitsUiState())

    init {
        viewModelScope.launch {
            installed.value = withContext(Dispatchers.IO) { container.installedApps.launchableApps() }
        }
    }

    fun open(app: MonitoredApp) {
        selected.value = app.packageName
    }

    fun close() {
        selected.value = null
    }

    fun commit(limit: AppUsageLimit) {
        viewModelScope.launch {
            val app = ui.value.editing ?: return@launch
            container.repository.saveLimit(limit.copy(packageName = app.packageName), app.label)
        }
    }

    fun remove(packageName: String) {
        viewModelScope.launch { container.repository.removeLimit(packageName) }
    }
}

@Composable
fun LimitsRoute(model: LimitsViewModel = viewModel()) {
    val state by model.ui.collectAsState()
    if (state.editing != null) {
        LimitEditor(state, model)
    } else {
        LimitList(state, model)
    }
}

@Composable
private fun LimitList(state: LimitsUiState, model: LimitsViewModel) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var paragraph by rememberSaveable { mutableStateOf(SaveParagraphs.first()) }
    var typed by rememberSaveable { mutableStateOf("") }
    fun open(app: MonitoredApp) {
        searching = false
        query = ""
        model.open(app)
    }
    val addedIds = state.added.map { it.app.packageName }.toSet()
    val matches = state.installed.filter {
        it.packageName !in addedIds && (query.isBlank() || it.label.contains(query, ignoreCase = true))
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(20.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Limits", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Lowering a limit saves immediately. Raising it asks you to type a paragraph.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { searching = !searching },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(if (searching) "Close" else "+ Add app")
        }
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search") },
                singleLine = true,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            if (searching) {
                items(matches, key = { "pick-${it.packageName}" }) { app ->
                    TextButton(
                        onClick = { open(app) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                    ) {
                        AppIcon(app.packageName, app.label)
                        Text(
                            app.label,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            textAlign = TextAlign.Start,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            } else {
                items(state.added, key = { it.app.packageName }) { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { open(item.app) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                        ) {
                            AppIcon(item.app.packageName, item.app.label)
                            Text(
                                item.app.label,
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                                textAlign = TextAlign.Start,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(item.summary, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            paragraph = SaveParagraphs.random()
                            typed = ""
                            pendingDelete = item.app.packageName
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${item.app.label}", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    val deleting = pendingDelete
    if (deleting != null) {
        RaiseLimitDialog(
            title = "Remove this limit",
            confirmLabel = "Delete",
            paragraph = paragraph,
            typed = typed,
            onTyped = { typed = if (typedByHand(typed, it)) it else typed },
            onDismiss = { pendingDelete = null },
            onSave = {
                model.remove(deleting)
                pendingDelete = null
            },
        )
    }
}

@Composable
private fun LimitEditor(state: LimitsUiState, model: LimitsViewModel) {
    val app = state.editing ?: return
    val limit = state.limit
    var perDay by rememberSaveable(app.packageName) { mutableStateOf(limit?.perDay == true) }
    var uniformRaw by rememberSaveable(app.packageName) { mutableStateOf(limit?.uniformMinutes?.toString().orEmpty()) }
    var dayRaw by rememberSaveable(app.packageName) { mutableStateOf(encodeDays(limit?.minutesByDay.orEmpty())) }
    var dayName by rememberSaveable(app.packageName) { mutableStateOf(Weekday.MONDAY.name) }
    var askRaise by rememberSaveable(app.packageName) { mutableStateOf(false) }
    var paragraph by rememberSaveable(app.packageName) { mutableStateOf(SaveParagraphs.first()) }
    var typed by rememberSaveable(app.packageName) { mutableStateOf("") }
    var zeroError by rememberSaveable(app.packageName) { mutableStateOf("") }
    var dayClear by rememberSaveable(app.packageName) { mutableStateOf(0) }
    val day = Weekday.entries.firstOrNull { it.name == dayName } ?: Weekday.MONDAY
    val dayMinutes = decodeDays(dayRaw)
    val shownMinutes = if (perDay) dayMinutes[day] else uniformRaw.toIntOrNull()
    val draft = AppUsageLimit(
        packageName = app.packageName,
        enabled = if (perDay) dayMinutes.isNotEmpty() else uniformRaw.toIntOrNull() != null,
        minutesByDay = dayMinutes,
        uniformMinutes = uniformRaw.toIntOrNull(),
        perDay = perDay,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(20.dp)),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = model::close) { Text("Back") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, app.label)
            Text(app.label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            if (perDay && day !in dayMinutes) "No limit" else shortTime(shownMinutes),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ModeButton("One limit", !perDay, Modifier.weight(1f)) { perDay = false }
            ModeButton("Per day", perDay, Modifier.weight(1f)) { perDay = true }
        }
        if (perDay) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Weekday.displayOrder.forEach { item ->
                    DayChip(
                        label = item.name.take(3),
                        on = item == day,
                        modifier = Modifier.weight(1f),
                    ) { dayName = item.name }
                }
            }
            Text(
                "Leave a day empty when that day should have no limit. 0 is not a limit.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                val updated = dayMinutes.toMutableMap()
                updated.remove(day)
                dayRaw = encodeDays(updated)
                dayClear += 1
            }) { Text("No limit on ${day.name.lowercase().replaceFirstChar { it.titlecase() }}") }
            DurationChoice("${day.name}-$dayClear", dayMinutes[day]?.toString().orEmpty()) { raw ->
                val updated = dayMinutes.toMutableMap()
                val minutes = raw.toIntOrNull()
                if (minutes == null) updated.remove(day) else updated[day] = minutes
                dayRaw = encodeDays(updated)
            }
        } else {
            DurationChoice("uniform-${app.packageName}", uniformRaw) { uniformRaw = it }
        }
        if (zeroError.isNotBlank()) Text(zeroError, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                if (isZeroLimit(perDay, uniformRaw, dayMinutes)) {
                    zeroError = "A limit cannot be 0. Set at least 1 minute."
                    return@Button
                }
                zeroError = ""
                if (raisesAllowance(limit, draft)) {
                    paragraph = SaveParagraphs.random()
                    typed = ""
                    askRaise = true
                } else {
                    model.commit(draft)
                    model.close()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) { Text("Save") }
    }
    if (askRaise) {
        RaiseLimitDialog(
            title = "Raise this limit",
            confirmLabel = "Save",
            paragraph = paragraph,
            typed = typed,
            onTyped = { typed = if (typedByHand(typed, it)) it else typed },
            onDismiss = { askRaise = false },
            onSave = {
                model.commit(draft)
                askRaise = false
                model.close()
            },
        )
    }
}

@Composable
internal fun RaiseLimitDialog(
    title: String,
    confirmLabel: String,
    paragraph: String,
    typed: String,
    onTyped: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    hint: String = "Type the paragraph below. Save opens when it matches.",
) {
    val matches = typed == paragraph
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC12141C))
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    paragraph,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    if (matches) "Matched" else "${typed.length} of ${paragraph.length} letters",
                    color = if (matches) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = onTyped,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text("Type it here") },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Text),
                )
                Button(
                    onClick = onSave,
                    enabled = matches,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) { Text(confirmLabel) }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) { Text(label) }
}

@Composable
private fun DayChip(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
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

@Composable
private fun DurationChoice(key: String, totalMinutes: String, onChange: (String) -> Unit) {
    val total = totalMinutes.toIntOrNull()
    var hours by rememberSaveable(key) { mutableStateOf(total?.div(60)?.toString().orEmpty()) }
    var mins by rememberSaveable(key) { mutableStateOf(total?.rem(60)?.toString().orEmpty()) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = hours,
            onValueChange = { raw ->
                hours = raw.filter { it.isDigit() }.take(2)
                onChange(combinedMinutes(hours, mins))
            },
            modifier = Modifier.weight(1f),
            label = { Text("Hours") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        OutlinedTextField(
            value = mins,
            onValueChange = { raw ->
                mins = raw.filter { it.isDigit() }.take(2)
                onChange(combinedMinutes(hours, mins))
            },
            modifier = Modifier.weight(1f),
            label = { Text("Minutes") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
    }
}

private fun encodeDays(values: Map<Weekday, Int>): String =
    Weekday.displayOrder.mapNotNull { day -> values[day]?.let { "${day.name}=$it" } }.joinToString(",")

private fun decodeDays(raw: String): Map<Weekday, Int> = raw.split(",")
    .mapNotNull { part ->
        val pieces = part.split("=")
        val day = Weekday.entries.firstOrNull { it.name == pieces.getOrNull(0) } ?: return@mapNotNull null
        val minutes = pieces.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        day to minutes
    }
    .toMap()

private fun isZeroLimit(perDay: Boolean, uniformRaw: String, dayMinutes: Map<Weekday, Int>): Boolean =
    if (perDay) dayMinutes.values.any { it == 0 } else uniformRaw.toIntOrNull() == 0

private fun raisesAllowance(previous: AppUsageLimit?, next: AppUsageLimit): Boolean {
    val before = previous ?: AppUsageLimit(next.packageName)
    return Weekday.entries.any { day -> allowance(next, day) > allowance(before, day) }
}

private fun allowance(limit: AppUsageLimit, day: Weekday): Int = limit.limitMinutes(day) ?: Int.MAX_VALUE

internal fun typedByHand(previous: String, next: String): Boolean {
    if (next.length == previous.length + 1) {
        val index = next.indices.firstOrNull { it >= previous.length || next[it] != previous[it] } ?: return true
        return next.removeRange(index, index + 1) == previous
    }
    if (next.length < previous.length) {
        var index = 0
        while (index < next.length && previous[index] == next[index]) index++
        return previous.endsWith(next.substring(index))
    }
    return next == previous
}

private fun combinedMinutes(hours: String, mins: String): String {
    if (hours.isBlank() && mins.isBlank()) return ""
    val hourPart = hours.toIntOrNull()?.coerceIn(0, 23) ?: 0
    val minutePart = mins.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return (hourPart * 60 + minutePart).toString()
}

private fun summary(limit: AppUsageLimit): String {
    if (!limit.perDay) return shortTime(limit.uniformMinutes ?: limit.minutesByDay.values.firstOrNull())
    return "Per day"
}

private fun shortTime(minutes: Int?): String {
    if (minutes == null) return "Not set"
    if (minutes < 60) return "$minutes m"
    return String.format(Locale.US, "%.1f h", minutes / 60.0)
}
