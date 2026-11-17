package com.tas5805m.ctrl.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AppViewModel

@SuppressLint("MissingPermission")
@Composable
fun ConnectScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val bonded by vm.bonded.collectAsStateWithLifecycle()

    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (state.connected) "Terhubung" else "Tidak terhubung",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.connected) Color(0xFF69F0AE) else Color(0xFFFFB74D)
                    )
                    if (state.connected) {
                        Spacer(Modifier.height(8.dp))
                        Text("${state.deviceName}\n${state.deviceAddress}")
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { vm.disconnect() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.LinkOff, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Putuskan")
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text("Pilih perangkat ESP32 yang sudah di-pair di Settings → Bluetooth.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.lastError.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(state.lastError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Perangkat ter-pair", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.refreshBonded() }) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
            }
        }

        if (bonded.isEmpty()) {
            item {
                Card {
                    ListItem(
                        headlineContent = { Text("Tidak ada perangkat paired") },
                        supportingContent = { Text("Pair dulu di Settings Android") }
                    )
                }
            }
        }

        items(bonded, key = { it.address }) { device ->
            Card(onClick = {
                if (!state.connected) vm.connect(device)
            }) {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Speaker, null) },
                    headlineContent = { Text(device.name ?: "Unknown") },
                    supportingContent = { Text(device.address) },
                    trailingContent = {
                        if (state.connected && state.deviceAddress == device.address) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF69F0AE))
                        } else {
                            Icon(Icons.Default.BluetoothConnected, null)
                        }
                    }
                )
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text(
                "Tips:\n• Device harus Bluetooth Classic (SPP).\n• Setelah connect, app otomatis kirim caps + dump + status.\n• Mode 4 = Wireless Sub (multi-peer max 4).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
