package com.tas5805m.ctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.ui.components.CardTitle
import com.tas5805m.ctrl.ui.components.GlassBadge
import com.tas5805m.ctrl.ui.components.GlassCard
import com.tas5805m.ctrl.ui.components.GlassGap
import com.tas5805m.ctrl.ui.components.PollWhileStarted
import com.tas5805m.ctrl.ui.components.SendSlider
import com.tas5805m.ctrl.ui.components.SendSliderInt
import com.tas5805m.ctrl.ui.components.SwitchRow
import com.tas5805m.ctrl.ui.theme.BadRed
import com.tas5805m.ctrl.ui.theme.OkGreen
import com.tas5805m.ctrl.ui.theme.WarnAmber
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Home / Kontrol — gaya Glass (sama dengan prototipe), hanya berisi kontrol yang benar-benar ada di firmware:
 * master, mix kanal (sat/sub/ctr), polaritas sub, crossover, gain analog, mode, mute/amp state, auto-standby, level.
 * (Loudness, Night Mode, sumber input, Sat L/R terpisah, PVDD/suhu TIDAK ada di firmware → sengaja tidak ditampilkan.)
 */
@Composable
fun ControlScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spp = vm.spp

    if (!state.connected) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BluetoothDisabled, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))
                Text("Hubungkan perangkat di tab Koneksi", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    // Level meter hidup: poll `lvl`/`lvl2` ±2×/dtk hanya selama layar Home tampil (baris LVL tidak masuk log).
    PollWhileStarted {
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // —— Master volume: angka besar + slider ——
        GlassCard {
            CardTitle("Master Volume", trailing = { GlassBadge(state.modeName()) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                Text(
                    buildAnnotatedString {
                        append("${state.master}")
                        withStyle(SpanStyle(fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("%") }
                    },
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            GlassGap(8)
            SendSliderInt(
                value = state.master,
                onSend = { spp.setMaster(it) },
                valueRange = 0..100,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // —— Channel mix: sat / sub / ctr + polaritas sub. Mode 0: firmware hanya memakai MASTER. ——
        if (state.mode != 0) {
            GlassCard {
                CardTitle("Channel Mix")
                ChannelRow("Subwoofer", state.sub, trailing = {
                    if (state.hasSubInv && state.hasSubPath) {
                        OutlinedButton(
                            onClick = { spp.setSubInv(!state.subInv) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (state.subInv) 0.8f else 0.3f)),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp),
                        ) { Text(if (state.subInv) "180°" else "0°", fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    }
                }) { spp.setSub(it) }
                GlassGap()
                ChannelRow("Satellite", state.sat) { spp.setSat(it) }
                if (state.is31) {
                    GlassGap()
                    ChannelRow("Center", state.ctr) { spp.setCtr(it) }
                }
            }
        }

        // —— Crossover: satu nilai untuk LPF sub + HPF sat; firmware membulatkan per 10 Hz (tabel, bukan LR4 terhitung) ——
        GlassCard {
            CardTitle("Crossover", trailing = { GlassBadge("60–150 Hz · 10 Hz") })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("LPF SUB  ⇄  HPF SAT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${state.xover} Hz", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            // live = false: tiap `xover` menyetel ulang filter DSP (I2C) + status panjang → kirim hanya saat dilepas
            SendSlider(
                value = state.xover.toFloat(),
                onSend = { spp.setXover(((it / 10f).roundToInt() * 10).coerceIn(60, 150)) },
                valueRange = 60f..150f,
                steps = 8,
                live = false,
            )
        }

        // —— Gain analog (0..31, −0,5 dB per langkah) ——
        GlassCard {
            CardTitle("Analog Gain", trailing = { GlassBadge("${"%.1f".format(-state.gain * 0.5)} dB") })
            SendSliderInt(
                value = state.gain,
                onSend = { spp.setGain(it) },
                valueRange = 0..31,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // —— Mode (hanya yang diterima firmware menurut CAPS) ——
        GlassCard {
            CardTitle("Mode")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "Stereo", 1 to "2.1 D", 2 to "2.1 S", 3 to "3.1", 4 to "W.Sub")
                    .filter { (m, _) -> state.modeAllowed(m) || state.mode == m }
                    .forEach { (m, label) ->
                        FilterChip(
                            selected = state.mode == m,
                            enabled = state.modeAllowed(m),
                            onClick = { if (state.mode != m) spp.setMode(m) },
                            label = { Text(label, fontSize = 11.sp) },
                        )
                    }
            }
        }

        // —— Mute + amp state + auto-standby ——
        GlassCard {
            CardTitle("Amplifier", trailing = {
                if (state.inStandby) GlassBadge("STANDBY", color = WarnAmber)
            })
            SwitchRow(
                title = "Mute",
                sub = "Bisukan output (amp state: mute)",
                checked = state.ampstate == "mute",
                onChange = { spp.setAmp(if (it) "mute" else "play") },
            )
            GlassGap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("play", "hiz", "sleep").forEach { s ->
                    FilterChip(
                        selected = state.ampstate == s,
                        onClick = { spp.setAmp(s) },
                        label = { Text(s.uppercase(), fontSize = 11.sp) },
                    )
                }
            }
            if (state.hasStandby) {
                GlassGap(12)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Auto-standby", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        if (state.standbyMin == 0) "OFF" else "${state.standbyMin} menit",
                        fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary,
                    )
                }
                SendSliderInt(
                    value = state.standbyMin,
                    onSend = { spp.setStandby((it / 5) * 5) },
                    valueRange = 0..120,
                    steps = 23,
                    live = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // —— Level meter ——
        if (state.hasLvl) {
            GlassCard {
                CardTitle("Level", trailing = {
                    TextButton(onClick = {
                        spp.requestLvl()
                        if (state.hasLvl2) spp.requestLvl2()
                    }) { Text("↻", fontSize = 14.sp) }
                })
                LevelBar("L", state.lvlL)
                LevelBar("R", state.lvlR)
                if (state.hasLvl2) {
                    LevelBar("L2", state.lvl2L)
                    LevelBar("R2", state.lvl2R)
                }
            }
        }

        if (state.hasSave) {
            Button(
                onClick = { spp.save() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Save to AMP")
            }
        }
    }
}

/** Satu baris kanal: label + nilai (%) mono + slider; [trailing] untuk tombol kecil (mis. fase). */
@Composable
private fun ChannelRow(
    label: String,
    value: Int,
    trailing: @Composable () -> Unit = {},
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            trailing()
            Spacer(Modifier.weight(1f))
            Text("$value%", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        SendSliderInt(
            value = value,
            onSend = onChange,
            valueRange = 0..100,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun LevelBar(label: String, deciDb: Int) {
    val norm = ((deciDb + 1200) / 1200f).coerceIn(0f, 1f)
    val db = deciDb / 10f
    val color = when {
        norm > 0.9f -> BadRed
        norm > 0.7f -> WarnAmber
        else -> OkGreen
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall)
        LinearProgressIndicator(
            progress = { norm },
            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
        )
        Text("${db.roundToInt()}", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    }
}
