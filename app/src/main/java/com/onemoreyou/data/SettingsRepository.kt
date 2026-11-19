package com.onemoreyou.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.onemoreyou.domain.AppSchedule
import com.onemoreyou.domain.AppUsageLimit
import com.onemoreyou.domain.BlockerConfig
import com.onemoreyou.domain.ConfigCodec
import com.onemoreyou.domain.LearningPassAllowance
import com.onemoreyou.domain.LearningPassSnapshot
import com.onemoreyou.domain.MonitoredApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.blockerStore by preferencesDataStore(name = "one_more_you")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.blockerStore
    private val mutex = Mutex()

    val configFlow: Flow<BlockerConfig> = store.data.map { prefs ->
        decode(prefs[CONFIG_KEY])
    }

    suspend fun snapshot(): BlockerConfig = configFlow.first()

    suspend fun setMonitored(apps: List<MonitoredApp>) = update { config ->
        config.withMonitored(apps)
    }

    suspend fun saveSchedule(schedule: AppSchedule) = update { it.withSchedule(schedule) }

    suspend fun saveSharedSchedule(schedule: AppSchedule) = update { it.withSharedSchedule(schedule) }

    suspend fun saveLimit(limit: AppUsageLimit, label: String) = update {
        it.withLimit(limit).withName(MonitoredApp(limit.packageName, label))
    }

    suspend fun removeLimit(packageName: String) = update { it.withoutLimit(packageName) }

    suspend fun saveAllowance(allowance: LearningPassAllowance) = update { it.withAllowance(allowance) }

    suspend fun savePassState(state: LearningPassSnapshot) = update { it.withPassState(state) }

    private suspend fun update(transform: (BlockerConfig) -> BlockerConfig) {
        mutex.withLock {
            store.edit { prefs ->
                val current = decode(prefs[CONFIG_KEY])
                prefs[CONFIG_KEY] = ConfigCodec.encode(transform(current))
            }
        }
    }

    private fun decode(raw: String?): BlockerConfig {
        if (raw.isNullOrBlank()) return BlockerConfig()
        return runCatching { ConfigCodec.decode(raw) }.getOrElse { BlockerConfig() }
    }

    private companion object {
        val CONFIG_KEY = stringPreferencesKey("blocker_config")
    }
}
