package com.onemoreyou.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.onemoreyou.R

/**
 * Google Pay keeps its own "payments blocked" screen until accessibility stays off,
 * including across an app restart. This service only tells the user. It does not
 * turn the blocker back on.
 */
class PaymentResumeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notice = notification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notice, serviceType())
        } else {
            startForeground(NOTIFICATION_ID, notice)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        stopSelf()
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Payments",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Shown when blocking is turned off so a payment app can run"
                },
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pass_notification)
            .setContentTitle("Blocking is off")
            .setContentText("Google Pay needs Accessibility off. Turn it back on after you pay.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Google Pay needs ONE MORE YOU's Accessibility service off. " +
                        "It stays off until you turn it on in Settings. " +
                        "If Google Pay still says payments are blocked, restart the phone and leave Accessibility off.",
                ),
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun serviceType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
    }

    private companion object {
        const val CHANNEL_ID = "payment_pause"
        const val NOTIFICATION_ID = 4103
    }
}
