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
import com.onemoreyou.OneMoreYouApp
import com.onemoreyou.R
import com.onemoreyou.blocking.BlockingCoordinator
import com.onemoreyou.blocking.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlockerWatchService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)
    private var coordinator: BlockingCoordinator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notice = notification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notice, serviceType())
        } else {
            startForeground(NOTIFICATION_ID, notice)
        }
        if (coordinator == null) startCoordinator()
        return START_STICKY
    }

    override fun onDestroy() {
        coordinator?.let { BlockerHub.release(it) }
        coordinator = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startCoordinator() {
        val container = (application as OneMoreYouApp).container
        val created = BlockerHub.retain { coordinatorScope ->
            BlockingCoordinator(
                appContext = this,
                ownPackage = packageName,
                repository = container.repository,
                usageTracker = container.usageTracker,
                overlay = OverlayController(
                    context = this,
                    onRequestPass = { coordinator?.onConfirmPass() },
                    onLeave = { goHome() },
                ),
                leaveApp = { goHome() },
                scope = coordinatorScope,
            )
        }
        coordinator = created
        scope.launch(Dispatchers.Default) {
            while (job.isActive) {
                val foreground = container.usageTracker.currentForeground()
                withContext(Dispatchers.Main) {
                    created.onUsageHint(foreground)
                }
                delay(1_000)
            }
        }
    }

    private fun goHome() {
        coordinator?.clearTracked()
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.deleteNotificationChannel("blocker_watch")
        if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Blocking", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Kept so blocking can run in the background"
                    setSound(null, null)
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                },
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pass_notification)
            .setContentTitle("ONE MORE YOU")
            .setContentText("Blocking is on")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
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
        const val CHANNEL_ID = "blocker_watch_min"
        const val NOTIFICATION_ID = 4104
    }
}
