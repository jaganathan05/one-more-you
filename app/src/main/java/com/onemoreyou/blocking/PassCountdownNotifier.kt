package com.onemoreyou.blocking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class PassCountdownNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val notifications = NotificationManagerCompat.from(appContext)

    fun show(appLabel: String, endsAtEpochMillis: Long) {
        if (!canPost()) return
        ensureChannel()
        val remaining = (endsAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(com.onemoreyou.R.drawable.ic_pass_notification)
            .setContentTitle("$appLabel only")
            .setContentText("Learning pass ${BlockText.clock(remaining)}")
            .setWhen(endsAtEpochMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
        notifications.notify(NOTIFICATION_ID, notification)
    }

    fun hide() {
        notifications.cancel(NOTIFICATION_ID)
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        manager.deleteNotificationChannel("learning_pass")
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Learning pass",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Countdown while a learning pass is open"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 180)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "learning_pass_alert"
        const val NOTIFICATION_ID = 4102
    }
}
