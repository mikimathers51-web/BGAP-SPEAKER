package com.tas5805m.ctrl.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AppViewModel

@Composable
fun DiagScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spp = vm.spp

    if (!state.connected) {
        Box(modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Hubungkan perangkat dulu")
        }
        return
    }

    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    spp.requestDump(); spp.requestStatus()
                    if (state.hasEqband) spp.requestEqDump()
                    if (state.hasLvl) spp.requestLvl()
                }) { Text("Refresh semua") }
                OutlinedButton(onClick = { spp.send("fault") }) { Text("Baca Fault") }
            }
        }

        item { Section("Status Audio", listOf(
            "Mode: ${state.modeName()} (${state.mode})",
            "Xover: ${state.xover} Hz",
            "Master/Sat/Sub/Ctr: ${state.master}/${state.sat}/${state.sub}/${state.ctr}",
            "Gain: ${state.gain} (${"%.1f".format(-state.gain * 0.5)} dB)",
            "Amp: ${state.ampstate}  ·  EQ: ${if (state.eqOn) "ON" else "OFF"} (EQM=${state.eqm})",
            "Mixer: LL=${"%.2f".format(state.ll)} LR=${"%.2f".format(state.lr)} RL=${"%.2f".format(state.rl)} RR=${"%.2f".format(state.rr)}"
        )) }

        item { Section("Capabilities (CAPS)", listOf(
            if (state.caps.isEmpty()) "(belum diterima)" else state.caps.joinToString(", ")
        )) }

        item { Section("Fault / Proteksi", listOf(
            state.faultRaw.ifEmpty { "Tidak ada data fault" },
            if (state.faults.isNotEmpty()) state.faults.entries.joinToString("  ") { "${it.key}=${it.value}" } else ""
        ).filter { it.isNotEmpty() }) }

        if (state.hasWsub || state.isWireless) {
            item {
                Section("Wireless Sub", listOf(
                    "Peers: ${state.wsubPeers.joinToString(", ").ifEmpty { state.wsubPeer }}",
                    "Links: ${state.wsubLinks.joinToString(",")}",
                    "Fill: ${state.wsubFillMs} ms  ·  Und: ${state.wsubUnd}  ·  Gap: ${state.wsubGap}",
                    "Rate: ${state.wsubRate} Hz  ·  Hi-Z: ${state.wsubHiz}  ·  FB: ${state.wsubFb}",
                    "RX a/c: ${state.wsubRxa}/${state.wsubRxc}  ·  Rej: ${state.wsubRej}  ·  STX: ${state.wsubStx}",
                    "Lat: ${state.wsubLat} ms  ·  Trim: ${state.wsubTrim} ms  ·  Max: ${state.wsubMax}"
                ))
            }
        }

        item {
            Text("Log SPP terakhir", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        items(state.lastLines.take(30)) { line ->
            Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Section(title: String, lines: List<String>) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            lines.forEach {
                Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
