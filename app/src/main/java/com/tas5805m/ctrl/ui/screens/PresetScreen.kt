package com.tas5805m.ctrl.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.data.Preset

@Composable
fun PresetScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama preset") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = { if (state.connected) { vm.savePreset(name); name = "" } },
                enabled = state.connected
            ) { Text("Simpan") }
        }
        Spacer(Modifier.height(8.dp))
        Text("Preset menyimpan mode, xover, volume, gain, mixer, dan 15-band EQ.", style = MaterialTheme.typography.bodySmall)

        LazyColumn(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (presets.isEmpty()) {
                item { Text("Belum ada preset. Atur EQ/volume lalu tekan Simpan.") }
            }
            itemsIndexed(presets) { i, p ->
                Card {
                    ListItem(
                        headlineContent = { Text(p.name) },
                        supportingContent = {
                            Text("Mode ${p.mode} · Xover ${p.xover}Hz · Master ${p.master}")
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { if (state.connected) vm.applyPreset(p) }) {
                                    Icon(Icons.Default.PlayArrow, "Terapkan", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { vm.deletePreset(i) }) {
                                    Icon(Icons.Default.Delete, "Hapus", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    )
                }
            }
        }

        // Factory
        Text("Factory quick presets", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Preset("Movie", mode = 1, xover = 100, master = 85, sat = 100, sub = 110, eqLeft = listOf(3f,2f,1f,0f,0f,0f,0f,0f,0f,0f,1f,2f,2f,1f,0f)),
                Preset("Music", mode = 0, xover = 80, master = 100, sat = 100, sub = 90, eqLeft = listOf(2f,1f,0f,0f,0f,0f,0f,0f,0f,1f,2f,2f,1f,0f,0f)),
                Preset("Night", mode = 2, xover = 80, master = 70, sat = 90, sub = 80, eqLeft = listOf(-2f,-1f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f)),
                Preset("Party", mode = 1, xover = 120, master = 100, sat = 100, sub = 120, eqLeft = listOf(5f,4f,2f,0f,-1f,0f,1f,2f,2f,1f,0f,0f,0f,0f,0f)),
            ).forEach { p ->
                AssistChip(onClick = { if (state.connected) vm.applyPreset(p.copy(eqRight = p.eqLeft)) }, label = { Text(p.name) })
            }
        }
    }
}
