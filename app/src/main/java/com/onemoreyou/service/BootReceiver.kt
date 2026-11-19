package com.onemoreyou.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onemoreyou.OneMoreYouApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != QUICK_BOOT) return
        val pending = goAsync()
        val app = context.applicationContext as OneMoreYouApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WakeScheduler.schedule(app, app.container.repository.snapshot())
                app.startForegroundService(Intent(app, BlockerWatchService::class.java))
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val QUICK_BOOT = "android.intent.action.QUICKBOOT_POWERON"
    }
}
