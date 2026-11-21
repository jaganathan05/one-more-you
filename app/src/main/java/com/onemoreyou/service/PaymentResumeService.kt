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
 * Google Play and Google Pay keep installs, purchases, and payments blocked until
 * this accessibility service stays off. This service only tells the user. It does
 * not turn the blocker back on.
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
                    "Play and Pay",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Shown when blocking is turned off so Play or a payment app can run"
                },
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pass_notification)
            .setContentTitle("Blocking is off")
            .setContentText("Google Play needs Accessibility off. Turn it back on after you install.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Google Play blocks installs and purchases while ONE MORE YOU's Accessibility service is on. " +
                        "Google Pay does the same for payments. The service stays off until you turn it on in Settings. " +
                        "Open Play or Pay again after it turns off. " +
                        "If Play still says the product is blocked, restart the phone and leave Accessibility off.",
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
