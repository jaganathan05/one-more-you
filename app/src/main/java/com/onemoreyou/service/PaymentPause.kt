package com.onemoreyou.service

import android.content.Context
import android.content.Intent
import android.provider.Settings

object PaymentPause {
    fun serviceId(context: Context): String =
        "${context.packageName}/com.onemoreyou.service.BlockerAccessibilityService"

    fun ourServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { it.equals(serviceId(context), ignoreCase = true) }
    }

    fun pause(context: Context) {
        val appContext = context.applicationContext
        appContext.startForegroundService(Intent(appContext, PaymentResumeService::class.java))
        val resolver = appContext.contentResolver
        val current = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val kept = current.split(':').filter { part ->
            part.isNotBlank() && !part.equals(serviceId(appContext), ignoreCase = true)
        }
        Settings.Secure.putString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            kept.joinToString(":"),
        )
        if (kept.isEmpty()) {
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        }
    }
}
