package com.notmysni.ui.sni

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val PRESET_SNI_HOSTS = listOf(
    "v.whatsapp.net",
    "instagram.com",
    "youtube.com",
    "twitter.com",
    "www.google.com",
    "cloudfront.net"
)

/**
 * Hostname validation: dot-separated labels, alphanumeric and hyphen, no leading/trailing hyphen per label.
 */
private val HOSTNAME_REGEX = Regex(
    "^(?=.{1,253}$)" +
        "(?!-)" +
        "[A-Za-z0-9-]{1,63}" +
        "(?<!-)" +
        "(" +
        "\\.(?!-)" +
        "[A-Za-z0-9-]{1,63}" +
        "(?<!-)" +
        ")*$"
)

private fun isValidHostname(value: String): Boolean =
    HOSTNAME_REGEX.matches(value.trim())

@Composable
fun SniSelectorScreen(
    selectedHostname: String,
    onSelectedHostnameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var customInput by remember { mutableStateOf("") }

    LaunchedEffect(selectedHostname) {
        customInput = if (selectedHostname in PRESET_SNI_HOSTS) "" else selectedHostname
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "SNI selector",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Preset hosts",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(PRESET_SNI_HOSTS, key = { it }) { host ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = selectedHostname == host,
                        onClick = {
                            customInput = ""
                            onSelectedHostnameChange(host)
                        }
                    )
                    Text(
                        text = host,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Custom SNI",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        val trimmedCustom = customInput.trim()
        val customNonEmpty = trimmedCustom.isNotEmpty()
        val customValid = customNonEmpty && isValidHostname(trimmedCustom)
        val customError = customNonEmpty && !customValid

        OutlinedTextField(
            value = customInput,
            onValueChange = { customInput = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Hostname") },
            isError = customError,
            supportingText = {
                when {
                    customError -> Text("Invalid hostname format")
                    customNonEmpty && customValid -> Text("Valid hostname")
                    else -> Text("Letters, digits, hyphens; dot-separated labels")
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onSelectedHostnameChange(trimmedCustom) },
            enabled = customValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use custom hostname")
        }
    }
}
