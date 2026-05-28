package com.notmysni.ui.main

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notmysni.vpn.VpnState

@Composable
fun MainScreen(
    activeSniHost: String,
    viewModel: MainViewModel = hiltViewModel()
) {
    val vpnState by viewModel.vpnState.collectAsState()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "NotMySNI",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Connection Status",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(text = vpnState.toStatusLabel()) }
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (vpnState == VpnState.Connected || vpnState == VpnState.Connecting) {
                            viewModel.stopVpn()
                        } else {
                            viewModel.requestVpnPermissionOrStart(vpnPermissionLauncher::launch)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (vpnState == VpnState.Connected || vpnState == VpnState.Connecting) {
                            "Stop VPN"
                        } else {
                            "Start VPN"
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Active SNI",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(text = activeSniHost) }
                )
            }
        }
    }
}

private fun VpnState.toStatusLabel(): String = when (this) {
    VpnState.Disconnected -> "Disconnected"
    VpnState.PermissionRequired -> "Permission Required"
    VpnState.Connecting -> "Connecting"
    VpnState.Connected -> "Connected"
    is VpnState.Error -> "Error"
}
