package com.onemoreyou.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings

object PaymentPause {
    private var pausedAtElapsed = 0L

    fun serviceId(context: Context): String =
        "${context.packageName}/com.onemoreyou.service.BlockerAccessibilityService"

    fun ourServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { it.equals(serviceId(context), ignoreCase = true) }
    }

    fun pause(service: AccessibilityService) {
        val now = SystemClock.elapsedRealtime()
        if (now - pausedAtElapsed < 5_000L) return
        if (!ourServiceEnabled(service)) return
        pausedAtElapsed = now
        val appContext = service.applicationContext
        runCatching {
            appContext.startForegroundService(Intent(appContext, PaymentResumeService::class.java))
        }
        service.disableSelf()
    }
}
