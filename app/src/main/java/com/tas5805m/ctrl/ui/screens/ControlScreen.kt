package com.tas5805m.ctrl.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.ui.components.SendSlider
import com.tas5805m.ctrl.ui.components.SendSliderInt
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

/**
 * Home / Kontrol — gaya Axton-like:
 * status Connect, master volume besar, mode chips, xover, level meter mini.
 */
@Composable
fun ControlScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spp = vm.spp

    if (!state.connected) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BluetoothDisabled, null, Modifier.size(48.dp), tint = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Text("Hubungkan perangkat di tab Koneksi", color = Color.Gray)
            }
        }
        return
    }

    // Level meter hidup: poll `lvl`/`lvl2` ±2×/dtk hanya selama layar Home tampil (baris LVL tidak masuk log).
    LaunchedEffect(Unit) {
        while (true) {
            val s = vm.state.value
            if (s.hasLvl) spp.requestLvl()
            if (s.hasLvl2) spp.requestLvl2()
            delay(500)
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // —— Connect status bar (Axton-style) ——
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (state.connected) Color(0xFF0D3D2E) else Color(0xFF3D1A1A)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (state.connected) Color(0xFF69F0AE) else Color(0xFFEF5350))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.connected) "CONNECTED" else "DISCONNECTED",
                        fontWeight = FontWeight.Bold,
                        color = if (state.connected) Color(0xFF69F0AE) else Color(0xFFEF5350),
                        fontSize = 13.sp
                    )
                    Text(
                        "${state.deviceName} · ${state.modeName()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    Icons.Default.BluetoothConnected,
                    null,
                    tint = if (state.connected) Color(0xFF69F0AE) else Color.Gray
                )
            }
        }

        // —— Master volume (large) ——
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("MASTER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                // Circular-ish gauge via Canvas arc
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                    MasterArc(value = state.master / 100f)
                    Text(
                        "${state.master}",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(8.dp))
                SendSliderInt(
                    value = state.master,
                    onSend = { spp.setMaster(it) },
                    valueRange = 0..100,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // —— Sat / Sub / Center row ——
        // Mode 0: firmware hanya memakai MASTER (satvol/subvol diabaikan). CTR hanya bermakna di mode 3.
        if (state.mode != 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniVolCard("SAT", state.sat, Modifier.weight(1f)) { spp.setSat(it) }
                MiniVolCard("SUB", state.sub, Modifier.weight(1f)) { spp.setSub(it) }
                if (state.is31) {
                    MiniVolCard("CTR", state.ctr, Modifier.weight(1f)) { spp.setCtr(it) }
                }
            }
        }

        // —— Polaritas sub + auto-standby ——
        if ((state.hasSubInv && state.hasSubPath) || state.hasStandby) {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    if (state.hasSubInv && state.hasSubPath) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("POLARITAS SUB", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    if (state.subInv) "Dibalik (180°)" else "Normal",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(checked = state.subInv, onCheckedChange = { spp.setSubInv(it) })
                        }
                    }
                    if (state.hasStandby) {
                        if (state.hasSubInv && state.hasSubPath) Spacer(Modifier.height(10.dp))
                        Text(
                            "AUTO-STANDBY  " + (if (state.standbyMin == 0) "OFF" else "${state.standbyMin} menit") +
                                (if (state.inStandby) "  ·  SEDANG STANDBY" else ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SendSliderInt(
                            value = state.standbyMin,
                            onSend = { spp.setStandby((it / 5) * 5) },
                            valueRange = 0..120,
                            steps = 23,
                            live = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // —— Mode ——
        Card(shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("MODE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Hanya mode yang benar-benar diterima firmware (CAPS): master nirkabel terkunci mode 4,
                    // mode 1/3 butuh chip kedua, mode 4 hanya di env wireless.
                    listOf(0 to "Stereo", 1 to "2.1 D", 2 to "2.1 S", 3 to "3.1", 4 to "W.Sub")
                        .filter { (m, _) -> state.modeAllowed(m) || state.mode == m }
                        .forEach { (m, label) ->
                            FilterChip(
                                selected = state.mode == m,
                                enabled = state.modeAllowed(m),
                                onClick = { if (state.mode != m) spp.setMode(m) },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                }
            }
        }

        // —— Xover + Gain ——
        Card(shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("CROSSOVER  ${state.xover} Hz", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                // live = false: tiap `xover` menyetel ulang filter DSP (I2C) + status panjang → kirim hanya saat dilepas
                SendSlider(
                    value = state.xover.toFloat(),
                    onSend = { spp.setXover(((it / 10f).roundToInt() * 10).coerceIn(60, 150)) },
                    valueRange = 60f..150f,
                    steps = 8,
                    live = false
                )
                Text(
                    "GAIN  ${state.gain}  (${"%.1f".format(-state.gain * 0.5)} dB)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                SendSliderInt(
                    value = state.gain,
                    onSend = { spp.setGain(it) },
                    valueRange = 0..31
                )
            }
        }

        // —— Amp state ——
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("play", "mute", "hiz", "sleep").forEach { s ->
                FilterChip(
                    selected = state.ampstate == s,
                    onClick = { spp.setAmp(s) },
                    label = { Text(s.uppercase(), fontSize = 11.sp) }
                )
            }
        }

        // —— Level meters mini ——
        if (state.hasLvl) {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("LEVEL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            spp.requestLvl()
                            if (state.hasLvl2) spp.requestLvl2()
                        }) { Text("↻", fontSize = 14.sp) }
                    }
                    LevelBar("L", state.lvlL)
                    LevelBar("R", state.lvlR)
                    if (state.hasLvl2) {
                        LevelBar("L2", state.lvl2L)
                        LevelBar("R2", state.lvl2R)
                    }
                }
            }
        }

        if (state.hasSave) {
            Button(
                onClick = { spp.save() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Save to AMP")
            }
        }
    }
}

@Composable
private fun MasterArc(value: Float) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 10.dp.toPx()
        val pad = stroke / 2
        // background arc
        drawArc(
            color = Color(0xFF2A2A2A),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(pad, pad),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        // value arc
        drawArc(
            brush = Brush.sweepGradient(listOf(primary.copy(alpha = 0.5f), primary)),
            startAngle = 135f,
            sweepAngle = 270f * value.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(pad, pad),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun MiniVolCard(label: String, value: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("$value", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            SendSliderInt(
                value = value,
                onSend = onChange,
                valueRange = 0..100,
                modifier = Modifier.height(28.dp)
            )
        }
    }
}

@Composable
fun LevelBar(label: String, deciDb: Int) {
    val norm = ((deciDb + 1200) / 1200f).coerceIn(0f, 1f)
    val db = deciDb / 10f
    val color = when {
        norm > 0.9f -> Color(0xFFEF5350)
        norm > 0.7f -> Color(0xFFFFB74D)
        else -> Color(0xFF69F0AE)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall)
        LinearProgressIndicator(
            progress = { norm },
            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = Color(0xFF2A2A2A)
        )
        Text("${db.roundToInt()}", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall)
    }
}
