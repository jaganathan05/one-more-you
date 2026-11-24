package com.onemoreyou.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings

object PaymentPause {
    private const val PREFS = "payment_pause"
    private const val PAUSED_KEY = "paused_for_payment"
    private const val OPTION_KEY = "pause_while_google_pay"
    private var pausedAtElapsed = 0L

    fun serviceId(context: Context): String =
        "${context.packageName}/com.onemoreyou.service.BlockerAccessibilityService"

    fun wantsPause(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(OPTION_KEY, false)

    fun setWantsPause(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(OPTION_KEY, enabled).apply()
    }

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
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(PAUSED_KEY, true).apply()
        runCatching {
            appContext.startForegroundService(Intent(appContext, PaymentResumeService::class.java))
        }
        service.disableSelf()
    }

    fun resumeAfterPayment(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PAUSED_KEY, false)) return
        if (ourServiceEnabled(context)) {
            prefs.edit().putBoolean(PAUSED_KEY, false).apply()
            return
        }
        val restored = runCatching {
            val resolver = context.contentResolver
            val current = Settings.Secure.getString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            val id = serviceId(context)
            val parts = current.split(':').filter { it.isNotBlank() }.toMutableList()
            if (parts.none { it.equals(id, ignoreCase = true) }) parts.add(id)
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, parts.joinToString(":"))
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }.isSuccess
        if (restored && ourServiceEnabled(context)) {
            prefs.edit().putBoolean(PAUSED_KEY, false).apply()
        }
    }
}
