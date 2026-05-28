package com.notmysni.vpn

sealed interface VpnState {
    data object Disconnected : VpnState
    data object PermissionRequired : VpnState
    data object Connecting : VpnState
    data object Connected : VpnState
    data class Error(val message: String) : VpnState
}
