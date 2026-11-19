package com.onemoreyou

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.onemoreyou.navigation.OneMoreYouRoot
import com.onemoreyou.service.BlockerWatchService
import com.onemoreyou.ui.OneMoreYouTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        pinHomeIcon()
        lifecycleScope.launch {
            (application as OneMoreYouApp).container.repository.migrateLegacyNightHours()
        }
        startForegroundService(Intent(this, BlockerWatchService::class.java))
        armIfRequested(intent)
        enableEdgeToEdge()
        setContent {
            val accent by (application as OneMoreYouApp).container.repository.themeFlow.collectAsState(initial = "sky")
            OneMoreYouTheme(accent) {
                OneMoreYouRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        armIfRequested(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    private fun pinHomeIcon() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val prefs = getSharedPreferences("one_more_you_ui", MODE_PRIVATE)
        if (prefs.getBoolean("home_pin_requested", false)) return
        val shortcuts = getSystemService(ShortcutManager::class.java) ?: return
        if (!shortcuts.isRequestPinShortcutSupported) return
        val info = ShortcutInfo.Builder(this, "home")
            .setShortLabel(getString(R.string.app_name))
            .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(componentName),
            )
            .build()
        if (shortcuts.requestPinShortcut(info, null)) {
            prefs.edit().putBoolean("home_pin_requested", true).apply()
        }
    }

    private fun armIfRequested(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ARM_SOCIAL, false) != true) return
        lifecycleScope.launch {
            SocialArm.armInstalled(application as OneMoreYouApp)
        }
    }

    companion object {
        const val EXTRA_ARM_SOCIAL = "arm_social"
        private const val REQUEST_NOTIFICATIONS = 41
    }
}
