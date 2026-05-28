package com.notmysni.ui.navigation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notmysni.data.SettingsPreferences
import com.notmysni.data.SettingsRepository
import com.notmysni.data.SniPreferences
import com.notmysni.data.SniRepository
import com.notmysni.engine.DpiEngineConfig
import com.notmysni.engine.EngineSettingsHolder
import com.notmysni.engine.fragment.TtlDesyncConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltViewModel
class NotMySniViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {
    private val sniRepository = SniRepository(application.applicationContext)
    private val settingsRepository = SettingsRepository(application.applicationContext)

    private val _sniState = MutableStateFlow(SniPreferences(selectedHostname = "v.whatsapp.net", customHosts = emptySet()))
    val sniState: StateFlow<SniPreferences> = _sniState.asStateFlow()

    private val _settingsState = MutableStateFlow(SettingsPreferences())
    val settingsState: StateFlow<SettingsPreferences> = _settingsState.asStateFlow()

    init {
        viewModelScope.launch {
            sniRepository.preferencesFlow.collectLatest { prefs ->
                _sniState.value = prefs
            }
        }
        viewModelScope.launch {
            settingsRepository.preferencesFlow.collectLatest { prefs ->
                _settingsState.value = prefs
                EngineSettingsHolder.config = DpiEngineConfig(
                    dohProvider = prefs.dohProvider,
                    fragmentStrategy = prefs.fragmentStrategy,
                    ttlDesync = TtlDesyncConfig(enabled = prefs.ttlDesyncEnabled)
                )
            }
        }
    }

    fun onSelectedHostnameChange(hostname: String) {
        viewModelScope.launch {
            sniRepository.setSelectedHostname(hostname)
        }
    }

    fun onSettingsChange(settings: SettingsPreferences) {
        viewModelScope.launch {
            settingsRepository.update(settings)
        }
    }
}
