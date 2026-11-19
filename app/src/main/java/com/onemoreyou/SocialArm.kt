package com.onemoreyou

import com.onemoreyou.domain.LearningPassAllowance
import com.onemoreyou.domain.MonitoredApp

object SocialArm {
    private val known = listOf(
        "com.instagram.android" to "Instagram",
        "com.facebook.katana" to "Facebook",
        "com.facebook.lite" to "Facebook Lite",
        "com.google.android.youtube" to "YouTube",
        "com.snapchat.android" to "Snapchat",
        "com.twitter.android" to "X",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
        "com.instagram.barcelona" to "Threads",
        "com.reddit.frontpage" to "Reddit",
    )

    suspend fun armInstalled(app: OneMoreYouApp): List<String> {
        val installed = known.filter { (packageName, _) ->
            runCatching { app.packageManager.getPackageInfo(packageName, 0) }.isSuccess
        }
        if (installed.isEmpty()) return emptyList()
        val repository = app.container.repository
        val current = repository.snapshot().monitored
        val selected = installed.map { (packageName, label) -> MonitoredApp(packageName, label) }
        repository.setMonitored((current + selected).distinctBy { it.packageName })
        repository.saveSharedSchedule(com.onemoreyou.domain.AppSchedule.sharedDefault())
        installed.forEach { (packageName, _) ->
            val existing = repository.snapshot().allowanceFor(packageName)
            if (existing == null || existing.passesPerDay <= 0) {
                repository.saveAllowance(LearningPassAllowance(packageName, passesPerDay = 1, durationMinutes = 10))
            }
        }
        return installed.map { it.second }
    }
}
