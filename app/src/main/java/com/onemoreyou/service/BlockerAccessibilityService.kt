package com.onemoreyou.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.onemoreyou.OneMoreYouApp
import com.onemoreyou.blocking.BlockingCoordinator
import com.onemoreyou.blocking.PaymentApps
import com.onemoreyou.blocking.OverlayController
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
        val overlay = OverlayController(
            context = this,
            onRequestPass = { coordinator?.onConfirmPass() },
            onLeave = {
                coordinator?.clearTracked()
                performGlobalAction(GLOBAL_ACTION_HOME)
            },
        )
        val created = BlockingCoordinator(
            appContext = this,
            ownPackage = packageName,
            repository = container.repository,
            usageTracker = container.usageTracker,
            overlay = overlay,
            leaveApp = { performGlobalAction(GLOBAL_ACTION_HOME) },
            scope = scope,
        )
        coordinator = created
        created.start()
        ReevaluateBus.setListener { created.reevaluate() }
        created.reevaluate()
        scope.launch {
            while (isActive) {
                delay(2_000)
                created.reevaluate()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        Log.i("OMY", "window $packageName ${event.className}")
        if (isChrome(packageName)) return
        if (PaymentApps.isPayment(packageName)) {
            coordinator?.onPaymentApp()
            runCatching { PaymentPause.pause(this) }
            return
        }
        coordinator?.onWindow(packageName, event.className?.toString())
    }

    override fun onInterrupt() = Unit

    private fun isChrome(packageName: String): Boolean {
        return packageName == "android" ||
            packageName == "com.android.systemui" ||
            packageName == "com.android.launcher3" ||
            packageName.startsWith("com.bbk.launcher") ||
            packageName == "com.vivo.upslide" ||
            packageName == "com.vivo.systemuiplugin" ||
            packageName.contains("inputmethod")
    }

    override fun onDestroy() {
        ReevaluateBus.setListener(null)
        coordinator?.stop()
        coordinator = null
        scope.cancel()
        super.onDestroy()
    }
}
