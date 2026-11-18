package com.tas5805m.ctrl.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.data.Preset
import kotlinx.coroutines.launch

private fun describe(p: Preset): String = when (p.kind) {
    "eq", "voice" -> p.note
    else -> buildString {
        append("Mode ${p.mode} · Xover ${p.xover} Hz · Master ${p.master}")
        if (p.voice != null) append(" · voicing")
        if (p.clip != null) append(" · clipper")
        if (p.trim != null) append(" · trim ${p.trim}")
    }
}

private fun kindLabel(p: Preset): String = when (p.kind) {
    "eq" -> "EQ"
    "voice" -> "Voicing sub"
    else -> "Lengkap"
}

private fun canApply(p: Preset, s: AmpState): Boolean =
    s.connected && (p.kind != "voice" || (s.hasWsub && s.hasVoice))

@Composable
fun PresetScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var name by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "save") {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama preset") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Button(
                        onClick = { if (state.connected) { vm.savePreset(name); name = ""; notice = "Preset disimpan." } },
                        enabled = state.connected,
                    ) { Text("Simpan") }
                }
                Text(
                    "Preset lengkap menyimpan mode, xover, volume, gain, mixer, EQ 15 band, serta (bila ada) clipper, polaritas sub, standby, trim, voicing sub, dan EQ center/sub.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                if (notice.isNotEmpty()) Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        item(key = "h_builtin") {
            Text("Bawaan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Preset EQ hanya mengubah EQ (band 20/32/50 Hz dilewati saat crossover aktif); preset voicing hanya mengubah voicing sub.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        items(vm.builtinPresets, key = { "b_" + it.name }) { p ->
            PresetRow(p, canApply(p, state), onApply = { vm.applyPreset(p); notice = "“${p.name}” dikirim." }, onDelete = null)
        }

        item(key = "h_user") {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                Text("Preset saya", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    scope.launch {
                        clipboard.setText(AnnotatedString(vm.exportPresets()))
                        notice = "Preset disalin ke clipboard (JSON)."
                    }
                }) { Text("Ekspor") }
                TextButton(onClick = { importText = clipboard.getText()?.text ?: ""; showImport = true }) { Text("Impor") }
            }
        }
        if (presets.isEmpty()) {
            item(key = "empty") { Text("Belum ada preset. Atur EQ/volume lalu tekan Simpan.", style = MaterialTheme.typography.bodySmall) }
        }
        itemsIndexed(presets) { i, p ->
            PresetRow(p, canApply(p, state), onApply = { vm.applyPreset(p); notice = "“${p.name}” dikirim." }, onDelete = { vm.deletePreset(i) })
        }
        item(key = "end") { Spacer(Modifier.height(24.dp)) }
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Impor preset (JSON)") },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("Tempel JSON hasil Ekspor") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val n = vm.importPresets(importText)
                        notice = if (n > 0) "$n preset diimpor." else "JSON tidak valid atau kosong."
                    }
                    showImport = false
                }) { Text("Impor") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun PresetRow(p: Preset, enabled: Boolean, onApply: () -> Unit, onDelete: (() -> Unit)?) {
    Card {
        ListItem(
            headlineContent = { Text(p.name) },
            supportingContent = { Text(kindLabel(p) + " · " + describe(p)) },
            trailingContent = {
                Row {
                    IconButton(onClick = onApply, enabled = enabled) {
                        Icon(Icons.Default.PlayArrow, "Terapkan", tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, "Hapus", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
        )
    }
}
