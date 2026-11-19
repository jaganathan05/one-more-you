package com.onemoreyou.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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

    suspend fun setMonitored(apps: List<MonitoredApp>) {
        if (isScheduleLocked()) return
        update { config -> config.withMonitored(apps) }
    }

    suspend fun saveSchedule(schedule: AppSchedule) = update { it.withSchedule(schedule) }

    suspend fun saveSharedSchedule(schedule: AppSchedule) {
        if (isScheduleLocked()) return
        update { it.withSharedSchedule(schedule) }
    }

    suspend fun saveLimit(limit: AppUsageLimit, label: String) = update {
        it.withLimit(limit).withName(MonitoredApp(limit.packageName, label))
    }

    suspend fun removeLimit(packageName: String) = update { it.withoutLimit(packageName) }

    suspend fun saveAllowance(allowance: LearningPassAllowance) {
        if (isScheduleLocked()) return
        update { it.withAllowance(allowance) }
    }

    suspend fun savePassState(state: LearningPassSnapshot) = update { it.withPassState(state) }

    suspend fun saveLearningPass(passesPerDay: Int, durationMinutes: Int) = update {
        it.withLearningPass(passesPerDay, durationMinutes)
    }

    val scheduleLockFlow: Flow<Boolean> = store.data.map { prefs -> prefs[SCHEDULE_LOCK_KEY] == true }

    suspend fun isScheduleLocked(): Boolean = scheduleLockFlow.first()

    suspend fun setScheduleLocked(locked: Boolean) {
        if (locked) {
            val days = snapshot().sharedSchedule.days.values
            val ready = days.any { it.enabled && it.startMinuteOfDay != it.endMinuteOfDay }
            if (!ready) return
        }
        store.edit { prefs -> prefs[SCHEDULE_LOCK_KEY] = locked }
    }

    suspend fun migrateLegacyNightHours() {
        mutex.withLock {
            store.edit { prefs ->
                if (prefs[HOURS_MIGRATED_KEY] == true) return@edit
                val current = decode(prefs[CONFIG_KEY])
                val days = current.sharedSchedule.days
                val legacy = days.isNotEmpty() && days.values.all { window ->
                    window.startMinuteOfDay == 21 * 60 && window.endMinuteOfDay == 7 * 60
                }
                if (legacy) {
                    val next = current.sharedSchedule.copy(
                        days = days.mapValues { (_, window) ->
                            window.copy(startMinuteOfDay = 9 * 60, endMinuteOfDay = 19 * 60)
                        },
                    )
                    prefs[CONFIG_KEY] = ConfigCodec.encode(current.withSharedSchedule(next))
                }
                prefs[HOURS_MIGRATED_KEY] = true
            }
        }
    }

    val themeFlow: Flow<String> = store.data.map { prefs -> prefs[THEME_KEY] ?: "sky" }

    suspend fun saveTheme(id: String) {
        store.edit { prefs -> prefs[THEME_KEY] = id }
    }

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
        val THEME_KEY = stringPreferencesKey("theme")
        val SCHEDULE_LOCK_KEY = booleanPreferencesKey("schedule_locked")
        val HOURS_MIGRATED_KEY = booleanPreferencesKey("schedule_hours_9_to_19")
    }
}
