package com.onemoreyou.service

import com.onemoreyou.blocking.BlockingCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

object BlockerHub {
    private val gate = Any()
    private var coordinator: BlockingCoordinator? = null
    private var scope: CoroutineScope? = null
    private var holders = 0

    fun retain(factory: (CoroutineScope) -> BlockingCoordinator): BlockingCoordinator = synchronized(gate) {
        val existing = coordinator
        if (existing != null) {
            holders += 1
            return existing
        }
        val createdScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val created = factory(createdScope)
        scope = createdScope
        coordinator = created
        holders = 1
        created.start()
        ReevaluateBus.setListener { coordinator?.reevaluate() }
        created
    }

    fun release(created: BlockingCoordinator) = synchronized(gate) {
        if (coordinator !== created) return
        holders -= 1
        if (holders > 0) return
        ReevaluateBus.setListener(null)
        created.stop()
        scope?.cancel()
        scope = null
        coordinator = null
        holders = 0
    }
}
