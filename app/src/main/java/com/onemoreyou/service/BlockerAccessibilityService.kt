package com.onemoreyou.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.onemoreyou.OneMoreYouApp
import com.onemoreyou.blocking.BlockingCoordinator
import com.onemoreyou.blocking.OverlayController
import com.onemoreyou.blocking.PaymentApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BlockerAccessibilityService : AccessibilityService() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)
    private var coordinator: BlockingCoordinator? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = (application as OneMoreYouApp).container
        val created = BlockerHub.retain { coordinatorScope ->
            val overlay = OverlayController(
                context = this,
                onRequestPass = { coordinator?.onConfirmPass() },
                onLeave = {
                    coordinator?.onAccessibilityHome()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                },
            )
            BlockingCoordinator(
                appContext = this,
                ownPackage = packageName,
                repository = container.repository,
                usageTracker = container.usageTracker,
                overlay = overlay,
                leaveApp = { performGlobalAction(GLOBAL_ACTION_HOME) },
                scope = coordinatorScope,
            )
        }
        coordinator = created
        created.reevaluate()
        scope.launch {
            while (isActive) {
                delay(1_000)
                reportForeground(activePackage())
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        val windowChanged = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (!windowChanged) return
        val windowPackage = event.packageName?.toString() ?: activePackage() ?: return
        Log.i("OMY", "window $windowPackage ${event.className}")
        reportForeground(windowPackage, event.className?.toString())
    }

    override fun onInterrupt() = Unit

    private fun reportForeground(windowPackage: String?, className: String? = null) {
        if (windowPackage.isNullOrBlank()) return
        when {
            PaymentApps.mustPause(windowPackage) -> {
                coordinator?.onPaymentApp()
                PaymentPause.pause(this)
            }
            isLauncher(windowPackage) -> coordinator?.onAccessibilityHome()
            isIgnoredSystem(windowPackage) -> Unit
            else -> coordinator?.onAccessibilityWindow(windowPackage, className)
        }
    }

    private fun activePackage(): String? {
        val root = rootInActiveWindow ?: return null
        val name = root.packageName?.toString()
        return name
    }

    private fun isLauncher(packageName: String): Boolean {
        return packageName == "com.android.launcher3" ||
            packageName.startsWith("com.bbk.launcher") ||
            packageName.contains("launcher")
    }

    private fun isIgnoredSystem(packageName: String): Boolean {
        return packageName == "android" ||
            packageName == "com.android.systemui" ||
            packageName == "com.vivo.upslide" ||
            packageName == "com.vivo.systemuiplugin" ||
            packageName.contains("inputmethod")
    }

    override fun onDestroy() {
        coordinator?.accessibilityStopped()
        coordinator?.let { BlockerHub.release(it) }
        coordinator = null
        scope.cancel()
        super.onDestroy()
    }
}
