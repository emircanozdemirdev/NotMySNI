package com.notmysni.ui.main

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private enum class ConnectionStatus {
    Disconnected,
    Connecting,
    Connected
}

@Composable
fun MainScreen() {
    var vpnEnabled by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf(ConnectionStatus.Disconnected) }
    val activeSniHost = "v.whatsapp.net"

    LaunchedEffect(vpnEnabled) {
        if (vpnEnabled) {
            connectionStatus = ConnectionStatus.Connecting
            delay(900)
            connectionStatus = ConnectionStatus.Connected
        } else {
            connectionStatus = ConnectionStatus.Disconnected
        }
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
                    label = { Text(text = connectionStatus.name) }
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { vpnEnabled = !vpnEnabled },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (vpnEnabled) "Stop VPN" else "Start VPN")
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
