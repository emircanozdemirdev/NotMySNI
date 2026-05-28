package com.notmysni.ui.main

import android.app.Application
import android.net.VpnService
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notmysni.vpn.LocalVpnService
import com.notmysni.vpn.VpnState
import com.notmysni.vpn.VpnStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val vpnStateManager: VpnStateManager
) : AndroidViewModel(application) {

    val vpnState: StateFlow<VpnState> = vpnStateManager.state

    fun requestVpnPermissionOrStart(onPermissionRequired: (android.content.Intent) -> Unit) {
        val context = getApplication<Application>()
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnStateManager.setState(VpnState.PermissionRequired)
            onPermissionRequired(prepareIntent)
            return
        }
        startVpn()
    }

    fun onVpnPermissionResult(granted: Boolean) {
        if (granted) {
            startVpn()
        } else {
            vpnStateManager.setState(VpnState.Disconnected)
        }
    }

    fun stopVpn() {
        val context = getApplication<Application>()
        vpnStateManager.setState(VpnState.Disconnected)
        context.startService(LocalVpnService.buildStopIntent(context))
    }

    private fun startVpn() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            vpnStateManager.setState(VpnState.Connecting)
            ContextCompat.startForegroundService(
                context,
                LocalVpnService.buildStartIntent(context)
            )
        }
    }
}
