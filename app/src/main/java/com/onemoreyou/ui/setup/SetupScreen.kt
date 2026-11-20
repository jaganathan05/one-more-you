package com.onemoreyou.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.onemoreyou.permissions.PermissionGateway
import com.onemoreyou.ui.LocalAppLook
import com.onemoreyou.ui.WideButton

private data class SetupStep(val name: String, val reason: String, val how: String, val button: String, val done: Boolean, val open: () -> Unit)

@Composable
fun SetupRoute(permissions: PermissionGateway, onChanged: () -> Unit) {
    val context = LocalContext.current
    val look = LocalAppLook.current
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                tick++
                onChanged()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        tick++
        onChanged()
    }
    val showNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val steps = remember(tick, showNotifications) {
        buildList {
            add(SetupStep("Show over other apps", "shows the block screen", "Tap Allow. Turn on ONE MORE YOU, then press Back.", "Allow display over apps", permissions.overlayGranted()) { context.startActivity(permissions.overlayIntent()) })
            add(SetupStep("See app usage", "counts your time in each app", "Tap Allow. Turn on ONE MORE YOU, then press Back.", "Allow usage access", permissions.usageAccessGranted()) { context.startActivity(permissions.usageAccessIntent()) })
            add(SetupStep("Alarms", "lets the schedule start and end on time", "Tap Allow. In phone settings, turn on Allow setting alarms, then press Back. We check it for you.", "Allow alarms", permissions.exactAlarmsAllowed()) { permissions.exactAlarmIntent()?.let(context::startActivity) })
            add(SetupStep("Watch the open app", "knows which app is open", "In the list, tap ONE MORE YOU and turn it on.", "Turn on ONE MORE YOU", permissions.accessibilityEnabled()) { context.startActivity(permissions.accessibilityIntent()) })
            if (showNotifications) {
                add(SetupStep("Notifications", "keeps the blocker running", "Tap Allow on the phone prompt.", "Allow notifications", permissions.notificationsGranted()) { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) })
            }
        }
    }
    val total = steps.size
    val doneCount = steps.count { it.done }
    val current = steps.indexOfFirst { !it.done }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ONE MORE YOU", color = look.accent, style = MaterialTheme.typography.labelLarge)
            Text("Step ${doneCount.coerceAtMost(total - 1) + 1} of $total", color = look.muted, style = MaterialTheme.typography.bodySmall)
        }
        Text("Let's get you set up", style = MaterialTheme.typography.headlineSmall)
        Text("The app needs $total permissions to close apps for you. Blocking starts when all $total are on.", color = look.muted)
        LinearProgressIndicator(
            progress = { doneCount / total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)),
            color = look.accent,
            trackColor = look.raised,
        )
        steps.forEachIndexed { index, step ->
            val open = index == current
            val shape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (open) look.card else look.background)
                    .border(if (open) 1.5.dp else 1.dp, if (open) look.accent else look.line, shape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepMark(index + 1, step.done, open)
                    Column {
                        Text(step.name, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                step.done -> "Done · ${step.reason}"
                                open -> step.reason
                                else -> "Next · ${step.reason}"
                            },
                            color = look.muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (open) {
                    Text("How: ${step.how}", color = look.faint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().background(look.raised, RoundedCornerShape(12.dp)).padding(12.dp))
                    WideButton(step.button, onClick = step.open)
                }
            }
        }
        Text("Everything stays on your phone. If you turn one off later, this screen comes back.", color = look.faint, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StepMark(number: Int, done: Boolean, open: Boolean) {
    val look = LocalAppLook.current
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).background(if (done) look.good else if (open) look.accent else look.background).border(1.dp, if (done) look.good else if (open) look.accent else look.strongBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = look.onAccent, modifier = Modifier.size(18.dp))
        else Text("$number", color = if (open) look.onAccent else look.faint, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SetupDone(onSchedule: () -> Unit, onLater: () -> Unit) {
    val look = LocalAppLook.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(look.accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = look.onAccent, modifier = Modifier.size(36.dp))
        }
        Text("You're all set", style = MaterialTheme.typography.headlineSmall)
        Text("All permissions are on. Now tell the app which apps to close, and when.", color = look.muted)
        Text("3 STEPS TO START", color = look.muted, style = MaterialTheme.typography.labelLarge)
        DoneCard(Icons.Filled.Schedule, "Make a schedule", "Pick days, hours and apps. Those apps stay closed in that time.")
        DoneCard(Icons.Filled.Timer, "Set daily limits", "Choose how long an app can stay open each day.")
        DoneCard(Icons.Filled.Lock, "Lock it", "Once locked, changing it needs a typed prayer. That pause helps you stay strong.")
        WideButton("Make my schedule", onClick = onSchedule)
        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) { Text("I'll do it later") }
    }
}

@Composable
private fun DoneCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, line: String) {
    val look = LocalAppLook.current
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(look.card).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = look.accent)
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(line, color = look.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
