package com.notmysni.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.notmysni.data.SettingsPreferences
import com.notmysni.dns.DohProvider
import com.notmysni.engine.fragment.FragmentStrategy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class FragmentStrategyOption(val label: String) {
    SPLIT_AT_SNI("Split at SNI"),
    TINY_FIRST("Tiny first (1–5 B)"),
    MULTI_SPLIT("Multi-split (N chunks)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsPreferences,
    onSettingsChange: (SettingsPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    var dohProviderIndex by rememberSaveable {
        mutableStateOf(settings.dohProvider.ordinal)
    }
    val dohProvider = DohProvider.entries[dohProviderIndex.coerceIn(0, DohProvider.entries.lastIndex)]
    var dohMenuExpanded by remember { mutableStateOf(false) }

    var fragmentStrategyIndex by rememberSaveable {
        mutableStateOf(settings.fragmentStrategy.toOption().ordinal)
    }
    val fragmentStrategy =
        FragmentStrategyOption.entries[fragmentStrategyIndex.coerceIn(0, FragmentStrategyOption.entries.lastIndex)]

    var verboseLogsEnabled by rememberSaveable { mutableStateOf(settings.verboseLogsEnabled) }

    var ttlDesyncEnabled by rememberSaveable {
        mutableStateOf(settings.ttlDesyncEnabled)
    }

    LaunchedEffect(settings) {
        dohProviderIndex = settings.dohProvider.ordinal
        fragmentStrategyIndex = settings.fragmentStrategy.toOption().ordinal
        ttlDesyncEnabled = settings.ttlDesyncEnabled
        verboseLogsEnabled = settings.verboseLogsEnabled
    }

    LaunchedEffect(dohProviderIndex, fragmentStrategyIndex, ttlDesyncEnabled, verboseLogsEnabled) {
        onSettingsChange(
            SettingsPreferences(
                dohProvider = dohProvider,
                fragmentStrategy = fragmentStrategy.toEngineStrategy(),
                ttlDesyncEnabled = ttlDesyncEnabled,
                verboseLogsEnabled = verboseLogsEnabled
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "DNS-over-HTTPS provider",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = dohMenuExpanded,
            onExpandedChange = { dohMenuExpanded = !dohMenuExpanded }
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                readOnly = true,
                value = dohProvider.toLabel(),
                onValueChange = {},
                label = { Text("DoH provider") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dohMenuExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            DropdownMenu(
                expanded = dohMenuExpanded,
                onDismissRequest = { dohMenuExpanded = false }
            ) {
                DohProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.toLabel()) },
                        onClick = {
                            dohProviderIndex = provider.ordinal
                            dohMenuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "TCP fragmentation strategy",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        FragmentStrategyOption.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(
                    selected = fragmentStrategy == option,
                    onClick = { fragmentStrategyIndex = option.ordinal }
                )
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "TTL desync",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TTL-based desync",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Low-TTL decoy on the first fragment, then real segments",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = ttlDesyncEnabled,
                onCheckedChange = { ttlDesyncEnabled = it }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Verbose logs",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Extra engine logging (for debugging)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = verboseLogsEnabled,
                onCheckedChange = { verboseLogsEnabled = it }
            )
        }
    }
}

private fun FragmentStrategyOption.toEngineStrategy(): FragmentStrategy = when (this) {
    FragmentStrategyOption.SPLIT_AT_SNI -> FragmentStrategy.SplitAtSni
    FragmentStrategyOption.TINY_FIRST -> FragmentStrategy.TinyFirst()
    FragmentStrategyOption.MULTI_SPLIT -> FragmentStrategy.MultiSplit(chunkCount = 4)
}

private fun FragmentStrategy.toOption(): FragmentStrategyOption = when (this) {
    FragmentStrategy.SplitAtSni -> FragmentStrategyOption.SPLIT_AT_SNI
    is FragmentStrategy.TinyFirst -> FragmentStrategyOption.TINY_FIRST
    is FragmentStrategy.MultiSplit -> FragmentStrategyOption.MULTI_SPLIT
}

private fun DohProvider.toLabel(): String = when (this) {
    DohProvider.CLOUDFLARE -> "Cloudflare"
    DohProvider.GOOGLE -> "Google"
}
