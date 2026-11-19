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
    val headline: String,
    val permissionsReady: Boolean = false,
    val overlayOn: Boolean = false,
    val usageOn: Boolean = false,
    val exactAlarmOn: Boolean = false,
    val scheduleTitle: String = "No schedule today",
    val scheduleDetail: String = "",
    val limits: List<HomeLimitRow> = emptyList(),
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as OneMoreYouApp).container
    private val _ui = MutableStateFlow(HomeUiState(headline = "Checking blocking status"))
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
            val names = config.monitored.joinToString(", ") { it.label }.ifBlank { "No apps added" }
            _ui.value = HomeUiState(
                headline = "Today",
                permissionsReady = overlay && usage && exact,
                overlayOn = overlay,
                usageOn = usage,
                exactAlarmOn = exact,
                scheduleTitle = if (scheduleOn) {
                    "${BlockText.minuteOfDay(window!!.startMinuteOfDay)} – ${BlockText.minuteOfDay(window.endMinuteOfDay)}"
                } else {
                    "No schedule today"
                },
                scheduleDetail = if (scheduleOn) "${day.name.lowercase().replaceFirstChar { it.uppercase() }} · $names" else "You manage this day yourself.",
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
        Text("ONE MORE YOU", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(state.headline, style = MaterialTheme.typography.headlineSmall)
        Text(
            "Selected apps stay closed during today's window.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard(title = "Scheduled time") {
            Text(state.scheduleTitle, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.tertiary)
            Text(state.scheduleDetail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!state.permissionsReady) {
            SectionCard(title = "Permissions") {
                StatusLine("Display over apps", if (state.overlayOn) "On" else "Off", state.overlayOn)
                StatusLine("Usage access", if (state.usageOn) "On" else "Off", state.usageOn)
                StatusLine("Exact alarms", if (state.exactAlarmOn) "Allowed" else "Not allowed", state.exactAlarmOn)
                if (!state.overlayOn) WideButton("Allow display over apps", onAccessibility)
                if (!state.usageOn) WideButton("Open usage access", onUsage)
                if (!state.exactAlarmOn) WideButton("Allow exact alarms", onExactAlarm)
            }
        }
        state.limits.forEachIndexed { index, app ->
            val fraction = when {
                app.limitMillis < 0L -> 0f
                app.limitMillis == 0L -> 1f
                else -> (app.usedMillis.toFloat() / app.limitMillis).coerceIn(0f, 1f)
            }
            val color = BarColors[index % BarColors.size]
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AppIcon(app.packageName, app.label)
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${BlockText.duration(app.usedMillis)} of ${BlockText.duration(app.limitMillis)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(99.dp)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(8.dp)
                                .background(color, RoundedCornerShape(99.dp)),
                        )
                    }
                }
            }
        }
    }
}
