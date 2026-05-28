package com.notmysni.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SniPreferences(
    val selectedHostname: String,
    val customHosts: Set<String>
)

class SniRepository(
    private val context: Context
) {
    val preferencesFlow: Flow<SniPreferences> = context.appPreferencesDataStore.data.map { prefs ->
        SniPreferences(
            selectedHostname = prefs[KEY_SELECTED_HOSTNAME] ?: DEFAULT_HOSTNAME,
            customHosts = prefs[KEY_CUSTOM_HOSTS] ?: emptySet()
        )
    }

    suspend fun setSelectedHostname(hostname: String) {
        val normalized = hostname.trim()
        if (normalized.isEmpty()) return
        context.appPreferencesDataStore.edit { prefs ->
            prefs[KEY_SELECTED_HOSTNAME] = normalized
            if (normalized !in PRESET_HOSTS) {
                val existing = prefs[KEY_CUSTOM_HOSTS] ?: emptySet()
                prefs[KEY_CUSTOM_HOSTS] = existing + normalized
            }
        }
    }

    companion object {
        private const val DEFAULT_HOSTNAME = "v.whatsapp.net"
        private val PRESET_HOSTS = setOf(
            "v.whatsapp.net",
            "instagram.com",
            "youtube.com",
            "twitter.com",
            "www.google.com",
            "cloudfront.net"
        )

        private val KEY_SELECTED_HOSTNAME = stringPreferencesKey("selected_hostname")
        private val KEY_CUSTOM_HOSTS = stringSetPreferencesKey("custom_hosts")
    }
}
