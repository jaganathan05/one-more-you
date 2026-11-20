package com.onemoreyou.blocking

import android.content.Context
import android.util.Log
import com.onemoreyou.data.SettingsRepository
import com.onemoreyou.domain.BlockerConfig
import com.onemoreyou.usage.UsageTracker
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BlockingCoordinator(
    private val appContext: Context,
    private val ownPackage: String,
    private val repository: SettingsRepository,
    private val usageTracker: UsageTracker,
    private val overlay: OverlayController,
    private val countdown: PassCountdownNotifier = PassCountdownNotifier(appContext),
    private val leaveApp: () -> Unit,
    private val scope: CoroutineScope,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    private val mutex = Mutex()
    private var config = BlockerConfig()
    private var trackedPackage: String? = null
    private var shownPackage: String? = null
    private var ticker: Job? = null
    private var tickerInterval = 0L
    private var pending: Job? = null
    private var granting = false
    private var paymentOpen = false
    private var started = false
    private var accessibilityAuthoritative = false
    private var trustedPackage: String? = null
    private var livePackage: String? = null
    private var liveSinceMillis: Long = 0L
    private var liveBaseUsed: Long = 0L

    fun start() {
        if (started) return
        started = true
        scope.launch {
            repository.configFlow.collect { updated ->
                config = updated
                evaluate()
            }
        }
    }

    fun onPaymentApp() {
        paymentOpen = true
        trackedPackage = null
        shownPackage = null
        pending?.cancel()
        scope.launch { evaluate() }
    }

    fun onAccessibilityWindow(packageName: String, className: String?) {
        accessibilityAuthoritative = true
        trustedPackage = packageName
        onWindow(packageName, className, authoritative = true)
    }

    fun onAccessibilityHome() {
        accessibilityAuthoritative = true
        trustedPackage = null
        clearTracked()
    }

    fun accessibilityStopped() {
        accessibilityAuthoritative = false
        trustedPackage = null
    }

    fun onUsageHint(packageName: String?) {
        if (packageName != null && PaymentApps.isPayment(packageName)) {
            onPaymentApp()
            return
        }
        if (packageName.isNullOrBlank()) {
            scope.launch { evaluate() }
            return
        }
        if (accessibilityAuthoritative && trustedPackage != null && trustedPackage != packageName) {
            scope.launch { evaluate() }
            return
        }
        val className = if (packageName == ownPackage) MAIN_ACTIVITY else null
        onWindow(packageName, className, authoritative = false)
    }

    fun onWindow(packageName: String?, className: String?, authoritative: Boolean = false) {
        if (packageName != null && PaymentApps.isPayment(packageName)) {
            onPaymentApp()
            return
        }
        paymentOpen = false
        if (packageName == ownPackage) {
            if (className == MAIN_ACTIVITY) {
                trackedPackage = null
                shownPackage = null
                pending?.cancel()
                scope.launch { evaluate() }
            }
            return
        }
        if (!packageName.isNullOrBlank()) trackedPackage = packageName
        if (packageName.isNullOrBlank() || packageName == shownPackage) {
            scope.launch { evaluate() }
            return
        }
        trackedPackage = packageName
        pending?.cancel()
        if (authoritative) {
            scope.launch { evaluate() }
        } else {
            pending = scope.launch {
                delay(200)
                evaluate()
            }
        }
    }

    fun clearTracked() {
        trackedPackage = null
        pending?.cancel()
        scope.launch { evaluate() }
    }

    fun reevaluate() {
        scope.launch {
            if (paymentOpen) {
                evaluate()
                return@launch
            }
            if (accessibilityAuthoritative && !trustedPackage.isNullOrBlank()) {
                trackedPackage = trustedPackage
            } else {
                val detected = withContext(Dispatchers.IO) { usageTracker.currentForeground(clock()) }
                if (!detected.isNullOrBlank() && detected != ownPackage) trackedPackage = detected
            }
            evaluate()
        }
    }

    fun onConfirmPass() {
        if (granting) return
        val packageName = trackedPackage ?: return
        granting = true
        scope.launch {
            try {
                val now = clock()
                val current = repository.snapshot().also { config = it }
                val allowance = current.allowanceFor(packageName) ?: return@launch
                if (allowance.passesPerDay <= 0) return@launch
                val used = withContext(Dispatchers.IO) {
                    usageTracker.foregroundMillisToday(packageName, now)
                }.let { rememberLiveUsage(packageName, it, now.toInstant().toEpochMilli()) }
                val decision = BlockingEngine.evaluatePackage(current, packageName, used, now)
                if (!decision.scheduleActive || decision.limitReached) return@launch
                val outcome = PassRules.grant(allowance, current.passStateFor(packageName), now)
                Log.i(TAG, "confirm $packageName ${allowance.durationMinutes}m -> $outcome")
                if (outcome is GrantOutcome.Granted) {
                    repository.savePassState(outcome.state)
                }
            } finally {
                granting = false
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        hideBlock()
    }

    private suspend fun evaluate() {
        mutex.withLock {
            val now = clock()
            val packageName = trackedPackage
            if (packageName.isNullOrBlank() || packageName == ownPackage || packageName == SETTINGS_PACKAGE) {
                hideBlock()
                stopTicker()
                return
            }
            val current = config
            if (!current.isMonitored(packageName) && current.limitFor(packageName)?.enabled != true) {
                hideBlock()
                stopTicker()
                return
            }
            val usedStats = withContext(Dispatchers.IO) {
                usageTracker.foregroundMillisToday(packageName, now)
            }
            val used = rememberLiveUsage(packageName, usedStats, now.toInstant().toEpochMilli())
            val decision = BlockingEngine.evaluatePackage(current, packageName, used, now)
            Log.i(
                TAG,
                "eval $packageName blocked=${decision.blocked} pass=${decision.passActive} " +
                    "left=${decision.passEndsAtEpochMillis?.let { it - now.toInstant().toEpochMilli() }} " +
                    "minutes=${decision.passDurationMinutes} passes=${decision.remainingPasses}",
            )
            val label = current.labelOf(packageName)
            when {
                decision.blocked -> {
                    shownPackage = packageName
                    BlockScreens.dismiss()
                    countdown.hide()
                    overlay.render(OverlayContent.Blocking(screenState(decision, label, now)))
                    startTicker(PASS_TICK_MS)
                }
                decision.passActive -> {
                    shownPackage = packageName
                    BlockScreens.dismiss()
                    overlay.hide()
                    val endsAt = decision.passEndsAtEpochMillis
                    if (endsAt != null) countdown.show(label, endsAt) else countdown.hide()
                    startTicker(PASS_TICK_MS)
                }
                else -> {
                    hideBlock()
                    startTicker(PASS_TICK_MS)
                }
            }
        }
    }

    private fun screenState(decision: BlockDecision, label: String, now: ZonedDateTime): BlockScreenState {
        val reasons = buildList {
            if (decision.scheduleActive) add("Scheduled block")
            if (decision.limitReached) add("Daily limit reached")
        }
        val details = buildList {
            decision.scheduleEndsAt?.let { add("Blocked until ${BlockText.whenLabel(it, now)}") }
            decision.limitMillis?.let { limit ->
                add("Used ${BlockText.duration(decision.usedMillis)} of ${BlockText.duration(limit)} today")
            }
        }
        val offerPass = decision.scheduleActive && !decision.limitReached && decision.passesConfigured
        return BlockScreenState(
            appLabel = label,
            reasons = reasons.ifEmpty { listOf("Blocked") },
            details = details,
            remainingPasses = decision.remainingPasses,
            passDurationMinutes = decision.passDurationMinutes,
            passesConfigured = decision.passesConfigured,
            canRequestPass = offerPass && decision.remainingPasses > 0,
            limitBlocksPass = decision.limitReached && decision.passesConfigured,
        )
    }

    private fun rememberLiveUsage(packageName: String, statsUsed: Long, nowMillis: Long): Long {
        if (livePackage != packageName || statsUsed < liveBaseUsed) {
            livePackage = packageName
            liveSinceMillis = nowMillis
            liveBaseUsed = statsUsed
        }
        return maxOf(statsUsed, liveBaseUsed + (nowMillis - liveSinceMillis))
    }

    private fun startTicker(intervalMs: Long) {
        if (ticker?.isActive == true && tickerInterval == intervalMs) return
        ticker?.cancel()
        tickerInterval = intervalMs
        ticker = scope.launch {
            while (isActive) {
                delay(intervalMs)
                evaluate()
            }
        }
    }

    private fun hideBlock() {
        shownPackage = null
        BlockScreens.dismiss()
        countdown.hide()
        overlay.render(OverlayContent.Hidden)
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
        tickerInterval = 0L
    }

    private companion object {
        const val TAG = "OMY"
        const val PASS_TICK_MS = 1_000L
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val MAIN_ACTIVITY = "com.onemoreyou.MainActivity"
    }
}
