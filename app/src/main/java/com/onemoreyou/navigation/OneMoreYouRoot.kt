package com.onemoreyou.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.onemoreyou.OneMoreYouApp
import com.onemoreyou.permissions.PermissionGateway
import com.onemoreyou.ui.LocalAppLook
import com.onemoreyou.ui.setup.SetupDone
import com.onemoreyou.ui.apps.AppControlsRoute
import com.onemoreyou.ui.home.HomeRoute
import com.onemoreyou.ui.limits.LimitsRoute
import com.onemoreyou.ui.settings.SettingsRoute
import com.onemoreyou.ui.setup.SetupRoute
import androidx.compose.material3.MaterialTheme

private data class Destination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Destination("home", "Home", Icons.Filled.Home),
    Destination("apps", "Schedule", Icons.AutoMirrored.Filled.ViewList),
    Destination("limits", "Limits", Icons.Filled.HourglassBottom),
    Destination("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun OneMoreYouRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val context = LocalContext.current
    val permissions = remember { PermissionGateway(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var ready by remember { mutableStateOf(permissions.readyToBlock()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) ready = permissions.readyToBlock()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val app = context.applicationContext as OneMoreYouApp
    val scope = rememberCoroutineScope()
    var introSeen by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        app.container.repository.introSeenFlow.collect { introSeen = it }
    }
    if (!ready) {
        SetupRoute(permissions) { ready = permissions.readyToBlock() }
        return
    }
    if (introSeen == false) {
        SetupDone(
            onSchedule = {
                scope.launch { app.container.repository.markIntroSeen() }
                introSeen = true
                nav.navigate("apps") { launchSingleTop = true }
            },
            onLater = {
                scope.launch { app.container.repository.markIntroSeen() }
                introSeen = true
            },
        )
        return
    }
    val look = LocalAppLook.current
    Scaffold(
        containerColor = look.background,
        bottomBar = {
            NavigationBar(
                containerColor = look.tabBar,
                tonalElevation = 0.dp,
            ) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = current == destination.route,
                        onClick = {
                            nav.navigate(destination.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.White.copy(alpha = 0.08f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            composable("home") {
                HomeRoute(
                    onAccessibility = { context.startActivity(permissions.overlayIntent()) },
                    onUsage = { context.startActivity(permissions.usageAccessIntent()) },
                    onExactAlarm = { permissions.exactAlarmIntent()?.let(context::startActivity) },
                )
            }
            composable("apps") { AppControlsRoute() }
            composable("limits") { LimitsRoute() }
            composable("settings") {
                SettingsRoute(
                    onAccessibility = { context.startActivity(permissions.overlayIntent()) },
                    onUsage = { context.startActivity(permissions.usageAccessIntent()) },
                    onExactAlarm = { permissions.exactAlarmIntent()?.let(context::startActivity) },
                    onAppDetails = { context.startActivity(permissions.appDetailsIntent()) },
                )
            }
        }
    }
}
