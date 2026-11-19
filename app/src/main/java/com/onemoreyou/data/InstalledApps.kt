package com.onemoreyou.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.onemoreyou.domain.MonitoredApp

class InstalledApps(private val context: Context) {
    fun launchableApps(): List<MonitoredApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PackageManager.MATCH_ALL
        return context.packageManager.queryIntentActivities(intent, flags)
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName || packageName == SETTINGS_PACKAGE) return@mapNotNull null
                val label = info.loadLabel(context.packageManager)?.toString()?.trim().orEmpty()
                MonitoredApp(packageName, label.ifBlank { packageName })
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
    }
}
