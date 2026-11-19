package com.onemoreyou

import android.app.Application
import com.onemoreyou.data.InstalledApps
import com.onemoreyou.data.SettingsRepository
import com.onemoreyou.permissions.PermissionGateway
import com.onemoreyou.service.WakeScheduler
import com.onemoreyou.usage.UsageTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: android.content.Context) {
    val repository = SettingsRepository(context)
    val usageTracker = UsageTracker(context)
    val permissions = PermissionGateway(context)
    val installedApps = InstalledApps(context)
}

class OneMoreYouApp : Application() {
    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scope.launch {
            container.repository.configFlow.collect { config ->
                WakeScheduler.schedule(this@OneMoreYouApp, config)
            }
        }
    }
}
