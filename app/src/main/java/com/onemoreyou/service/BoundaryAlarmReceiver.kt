package com.onemoreyou.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onemoreyou.OneMoreYouApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BoundaryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext as OneMoreYouApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = app.container.repository.snapshot()
                WakeScheduler.schedule(app, config)
                ReevaluateBus.ping()
            } finally {
                pending.finish()
            }
        }
    }
}
