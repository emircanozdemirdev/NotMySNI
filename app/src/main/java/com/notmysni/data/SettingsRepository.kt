package com.notmysni.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.notmysni.dns.DohProvider
import com.notmysni.engine.fragment.FragmentStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SettingsPreferences(
    val dohProvider: DohProvider = DohProvider.CLOUDFLARE,
    val fragmentStrategy: FragmentStrategy = FragmentStrategy.SplitAtSni,
    val ttlDesyncEnabled: Boolean = false,
    val verboseLogsEnabled: Boolean = false
)

class SettingsRepository(
    private val context: Context
) {
    val preferencesFlow: Flow<SettingsPreferences> = context.appPreferencesDataStore.data.map { prefs ->
        SettingsPreferences(
            dohProvider = prefs[KEY_DOH_PROVIDER]
                ?.let { runCatching { DohProvider.valueOf(it) }.getOrNull() }
                ?: DohProvider.CLOUDFLARE,
            fragmentStrategy = prefs[KEY_FRAGMENT_STRATEGY]
                ?.let { decodeFragmentStrategy(it) }
                ?: FragmentStrategy.SplitAtSni,
            ttlDesyncEnabled = prefs[KEY_TTL_DESYNC_ENABLED] ?: false,
            verboseLogsEnabled = prefs[KEY_VERBOSE_LOGS_ENABLED] ?: false
        )
    }

    suspend fun update(settings: SettingsPreferences) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[KEY_DOH_PROVIDER] = settings.dohProvider.name
            prefs[KEY_FRAGMENT_STRATEGY] = encodeFragmentStrategy(settings.fragmentStrategy)
            prefs[KEY_TTL_DESYNC_ENABLED] = settings.ttlDesyncEnabled
            prefs[KEY_VERBOSE_LOGS_ENABLED] = settings.verboseLogsEnabled
        }
    }

    private fun encodeFragmentStrategy(strategy: FragmentStrategy): String = when (strategy) {
        FragmentStrategy.SplitAtSni -> "split_at_sni"
        is FragmentStrategy.TinyFirst -> "tiny_first:${strategy.firstSegmentSize}"
        is FragmentStrategy.MultiSplit -> "multi_split:${strategy.chunkCount}"
    }

    private fun decodeFragmentStrategy(value: String): FragmentStrategy {
        return when {
            value == "split_at_sni" -> FragmentStrategy.SplitAtSni
            value.startsWith("tiny_first:") -> {
                val size = value.substringAfter(':').toIntOrNull() ?: 1
                FragmentStrategy.TinyFirst(size.coerceIn(1, 5))
            }
            value.startsWith("multi_split:") -> {
                val chunks = value.substringAfter(':').toIntOrNull() ?: 4
                FragmentStrategy.MultiSplit(chunks.coerceAtLeast(2))
            }
            else -> FragmentStrategy.SplitAtSni
        }
    }

    companion object {
        private val KEY_DOH_PROVIDER = stringPreferencesKey("doh_provider")
        private val KEY_FRAGMENT_STRATEGY = stringPreferencesKey("fragment_strategy")
        private val KEY_TTL_DESYNC_ENABLED = booleanPreferencesKey("ttl_desync_enabled")
        private val KEY_VERBOSE_LOGS_ENABLED = booleanPreferencesKey("verbose_logs_enabled")
    }
}
