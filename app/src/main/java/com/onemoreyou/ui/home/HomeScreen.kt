package com.onemoreyou.ui.home

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoreyou.OneMoreYouApp
import com.onemoreyou.blocking.BlockText
import com.onemoreyou.domain.Weekday
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onemoreyou.ui.AppIcon
import com.onemoreyou.ui.LocalAppLook
import com.onemoreyou.ui.BarColors
import com.onemoreyou.ui.SectionCard
import com.onemoreyou.ui.StatusLine
import com.onemoreyou.ui.WideButton
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeLimitRow(
    val packageName: String,
    val label: String,
    val usedMillis: Long,
    val limitMillis: Long,
)

data class HomeUiState(
    val greeting: String = "Good morning",
    val dayLine: String = "",
    val locked: Boolean = false,
    val empty: Boolean = true,
    val scheduleOn: Boolean = false,
    val phase: String = "none",
    val focusTitle: String = "No schedule today",
    val focusLine: String = "Your limits still count.",
    val windowProgress: Float = 0f,
    val startLabel: String = "",
    val endLabel: String = "",
    val appsLine: String = "",
    val tomorrowTitle: String = "",
    val tomorrowLine: String = "",
    val limits: List<HomeLimitRow> = emptyList(),
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as OneMoreYouApp).container
    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            container.repository.configFlow.collect {
                refresh()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val now = ZonedDateTime.now()
            val config = container.repository.snapshot()
            val overlay = container.permissions.overlayGranted()
            val usage = container.permissions.usageAccessGranted()
            val exact = container.permissions.exactAlarmsAllowed()
            val day = Weekday.from(now.dayOfWeek)
            val window = config.sharedSchedule.days[day]
            val scheduleOn = config.sharedSchedule.enabled && window?.enabled == true &&
                window.startMinuteOfDay != window.endMinuteOfDay
            val limits = withContext(Dispatchers.IO) {
                config.limits.mapNotNull { limit ->
                    val minutes = limit.limitMinutes(day) ?: return@mapNotNull null
                    HomeLimitRow(
                        packageName = limit.packageName,
                        label = config.labelOf(limit.packageName),
                        usedMillis = if (!usage) 0L else container.usageTracker.foregroundMillisToday(limit.packageName, now),
                        limitMillis = minutes * 60_000L,
                    )
                }
            }
            val names = config.monitored.map { it.label }
            val appsLine = when (names.size) {
                0 -> ""
                1 -> "${names[0]} is closed"
                2 -> "${names[0]} and ${names[1]} are closed"
                else -> "${names[0]}, ${names[1]} and ${names.size - 2} more are closed"
            }
            val minute = now.hour * 60 + now.minute
            val start = window?.startMinuteOfDay ?: 0
            val end = window?.endMinuteOfDay ?: 0
            val phase = when {
                !scheduleOn -> "none"
                start < end && minute in start until end -> "during"
                start > end && (minute >= start || minute < end) -> "during"
                start < end && minute < start -> "before"
                start > end && minute in end until start -> "before"
                else -> "after"
            }
            val span = if (start == end) 1 else if (start < end) end - start else (24 * 60 - start) + end
            val elapsed = when {
                start < end -> (minute - start).coerceIn(0, span)
                minute >= start -> minute - start
                else -> (24 * 60 - start) + minute
            }
            val tomorrow = Weekday.from(now.plusDays(1).dayOfWeek)
            val next = config.sharedSchedule.days[tomorrow]
            val tomorrowOn = config.sharedSchedule.enabled && next?.enabled == true && next.startMinuteOfDay != next.endMinuteOfDay
            val hour = now.hour
            _ui.value = HomeUiState(
                greeting = when {
                    hour < 12 -> "Good morning"
                    hour < 17 -> "Good afternoon"
                    else -> "Good evening"
                },
                dayLine = "${day.name.lowercase().replaceFirstChar { it.uppercase() }} · Here is your day",
                locked = container.repository.isScheduleLocked(),
                empty = config.monitored.isEmpty() && limits.isEmpty(),
                scheduleOn = scheduleOn,
                phase = phase,
                focusTitle = when (phase) {
                    "during" -> "Apps closed until ${BlockText.minuteOfDay(end)}"
                    "before" -> "Apps close at ${BlockText.minuteOfDay(start)}"
                    "after" -> "Apps opened at ${BlockText.minuteOfDay(end)}"
                    else -> "No schedule today"
                },
                focusLine = when (phase) {
                    "during" -> "${BlockText.duration(((span - elapsed).coerceAtLeast(0)) * 60_000L)} left · started ${BlockText.minuteOfDay(start)}"
                    else -> "Your limits still count."
                },
                windowProgress = if (phase == "during") elapsed.toFloat() / span else 0f,
                startLabel = BlockText.minuteOfDay(start),
                endLabel = BlockText.minuteOfDay(end),
                appsLine = appsLine,
                tomorrowTitle = "Tomorrow · ${tomorrow.name.lowercase().replaceFirstChar { it.uppercase() }}",
                tomorrowLine = if (tomorrowOn) {
                    "${BlockText.minuteOfDay(next!!.startMinuteOfDay)} – ${BlockText.minuteOfDay(next.endMinuteOfDay)}"
                } else {
                    "No schedule. Your limits still count."
                },
                limits = limits,
            )
        }
    }
}

@Composable
fun HomeRoute(
    onAccessibility: () -> Unit,
    onUsage: () -> Unit,
    onExactAlarm: () -> Unit,
    model: HomeViewModel = viewModel(),
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
    HomeScreen(state, onAccessibility, onUsage, onExactAlarm)
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    onAccessibility: () -> Unit,
    onUsage: () -> Unit,
    onExactAlarm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(20.dp)),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val look = LocalAppLook.current
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("ONE MORE YOU", style = MaterialTheme.typography.labelLarge, color = look.accent)
            if (state.locked) Text("Locked", color = look.accent, style = MaterialTheme.typography.labelLarge)
        }
        Text(state.greeting, style = MaterialTheme.typography.headlineSmall)
        Text(state.dayLine, color = look.muted)
        if (state.empty) {
            SectionCard("Schedule") {
                Text("Not set", color = look.accent)
                Text("Close apps during fixed hours, like work or study time. Example: Instagram closed Mon–Fri, 9 AM to 7 PM.", color = look.muted)
            }
            SectionCard("Daily limits") {
                Text("None yet", color = look.accent)
                Text("Give an app a daily time budget. When it runs out, the app closes until tomorrow.", color = look.muted)
            }
            Text("You can use both. A schedule closes apps by the clock. A limit closes them by time used.", color = look.faint, style = MaterialTheme.typography.bodySmall)
        } else {
            SectionCard(if (state.phase == "during") "FOCUS TIME IS ON" else if (state.phase == "before") "STARTS LATER" else if (state.phase == "after") "DONE FOR TODAY" else "TODAY") {
                Text(state.focusTitle, style = MaterialTheme.typography.titleMedium)
                Text(state.focusLine, color = look.muted)
                if (state.phase == "during") {
                    Box(Modifier.fillMaxWidth().height(8.dp).background(look.raised, RoundedCornerShape(99.dp))) {
                        Box(Modifier.fillMaxWidth(state.windowProgress.coerceIn(0f, 1f)).height(8.dp).background(look.accent, RoundedCornerShape(99.dp)))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(state.startLabel, color = look.faint, style = MaterialTheme.typography.bodySmall)
                        Text(state.endLabel, color = look.faint, style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.appsLine.isNotBlank()) Text(state.appsLine, color = look.text)
                }
            }
            Text("TODAY'S LIMITS", color = look.muted, style = MaterialTheme.typography.labelLarge)
            state.limits.forEach { app ->
                val usedUp = app.limitMillis > 0 && app.usedMillis >= app.limitMillis
                val left = (app.limitMillis - app.usedMillis).coerceAtLeast(0L)
                val fraction = if (app.limitMillis <= 0L) 0f else (app.usedMillis.toFloat() / app.limitMillis).coerceIn(0f, 1f)
                val bar = if (usedUp) look.timeUp else look.good
                Column(Modifier.fillMaxWidth().background(look.card, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app.packageName, app.label)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.titleMedium)
                            Text("${BlockText.duration(app.usedMillis)} of ${BlockText.duration(app.limitMillis)}", color = look.muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (usedUp) "Time's up" else "${BlockText.duration(left)} left", color = if (usedUp) look.timeUp else look.good, style = MaterialTheme.typography.bodySmall)
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).background(look.raised, RoundedCornerShape(99.dp))) {
                        Box(Modifier.fillMaxWidth(fraction).height(8.dp).background(bar, RoundedCornerShape(99.dp)))
                    }
                }
            }
            SectionCard(state.tomorrowTitle) {
                Text(state.tomorrowLine)
            }
        }
    }
}
