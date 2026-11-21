package com.tas5805m.ctrl.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tas5805m.ctrl.ui.components.CardTitle
import com.tas5805m.ctrl.ui.components.GlassCard
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.data.MeterChannel
import com.tas5805m.ctrl.data.MeterSnapshot
import com.tas5805m.ctrl.ui.components.PollWhileStarted
import com.tas5805m.ctrl.ui.theme.BadRed
import com.tas5805m.ctrl.ui.theme.OkGreen
import com.tas5805m.ctrl.ui.theme.WarnAmber
import kotlin.math.roundToInt

private const val POLL_MS = 330L          // ±3×/dtk; `lvl`/`lvl2`/`wlvl` murah (lihat PROTOCOL.md)
private const val SPARK_POINTS = 60       // ±30 dtk @ 1 sampel / 500 ms
private const val SPARK_EVERY = 2         // ambil sampel buffer tiap 2 siklus poll ≈ 0,66 dtk

/** Monitor: meter L/R (chip 1), sub (chip 2), level sub wireless, plus sparkline buffer per sub 30 dtk. Poll hanya saat tab terlihat. */
@Composable
fun MonitorScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    val channels = remember { HashMap<String, MeterChannel>() }
    val sparks = remember { HashMap<String, ArrayDeque<Int>>() }
    var tick by remember { mutableIntStateOf(0) }       // memicu rekomposisi tiap sampel
    var avgMode by remember { mutableStateOf(false) }   // false = PEAK (now + hold), true = AVG

    PollWhileStarted {
        var n = 0
        while (true) {
            val s = vm.state.value
            if (s.connected) {
                if (s.hasLvl) vm.spp.send("lvl")
                if (s.hasLvl2) vm.spp.send("lvl2")
                if (s.hasWlvl) vm.spp.send("wlvl")
                val t = System.currentTimeMillis()
                if (s.hasLvl) {
                    channels.getOrPut("L") { MeterChannel() }.push(s.lvlL, t)
                    channels.getOrPut("R") { MeterChannel() }.push(s.lvlR, t)
                }
                if (s.hasLvl2) {
                    channels.getOrPut("L2") { MeterChannel() }.push(s.lvl2L, t)
                    channels.getOrPut("R2") { MeterChannel() }.push(s.lvl2R, t)
                }
                s.peerDetails.forEach { p ->
                    p.lvlDeciDb?.let { channels.getOrPut("P" + p.mac) { MeterChannel() }.push(it, t) }
                    if (n % SPARK_EVERY == 0) {
                        val d = sparks.getOrPut(p.mac) { ArrayDeque() }
                        d.addLast(if (p.link) p.fillMs else 0)
                        while (d.size > SPARK_POINTS) d.removeFirst()
                    }
                }
                tick++
                n++
            }
            kotlinx.coroutines.delay(POLL_MS)
        }
    }

    @Suppress("UNUSED_EXPRESSION") tick   // baca agar Compose berlangganan
    fun snap(key: String): MeterSnapshot? = channels[key]?.snapshot()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !avgMode, onClick = { avgMode = false }, label = { Text("PEAK") })
                FilterChip(selected = avgMode, onClick = { avgMode = true }, label = { Text("AVG") })
                Box(Modifier.weight(1f))
                OutlinedButton(onClick = { channels.values.forEach { it.resetPeaks() }; tick++ }) { Text("Reset Peaks") }
            }
        }
        if (!state.connected) {
            item { Text("Sambungkan perangkat di tab Koneksi untuk melihat level.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (!state.hasLvl) {
            item { Text("Firmware ini belum mendukung level meter (`lvl`).", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item {
                MeterCard("Master Output") {
                    snap("L")?.let { GlassMeter("L", it, avgMode) }
                    snap("R")?.let { GlassMeter("R", it, avgMode) }
                }
            }
            if (state.hasLvl2) item {
                MeterCard("Chip 2 / Sub") {
                    snap("L2")?.let { GlassMeter("L2", it, avgMode) }
                    snap("R2")?.let { GlassMeter("R2", it, avgMode) }
                }
            }
        }
        if (state.connected && state.peerDetails.isNotEmpty()) {
            item { Text("Sub wireless · ${state.peerDetails.count { it.link }}/${state.peerDetails.size} aktif", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
            state.peerDetails.forEach { p ->
                item(key = "peer-" + p.mac) {
                    MeterCard("Sub ${if (p.slot >= 0) p.slot + 1 else ""}  ·  ${p.mac.takeLast(8)}") {
                        snap("P" + p.mac)?.takeIf { p.link && p.lvlDeciDb != null }?.let { GlassMeter("Sub", it, avgMode) }
                            ?: Text(if (p.link) "level n/a (slave lama atau data basi)" else "offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val spark = sparks[p.mac]?.toList().orEmpty()
                        Text("Buffer · 30 dtk terakhir   ${if (p.link) "${p.fillMs} ms" else "—"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Sparkline(spark, MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item { Box(Modifier.height(12.dp)) }
    }
}

@Composable
private fun MeterCard(title: String, content: @Composable () -> Unit) {
    GlassCard(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(title, Modifier.padding(bottom = 0.dp))
            content()
        }
    }
}

/** Satu kanal: bar level (now atau avg), penanda peak-hold, angka dB mono, puncak sesi. */
@Composable
private fun GlassMeter(label: String, s: MeterSnapshot, avgMode: Boolean) {
    val value = if (avgMode) s.avg else s.now
    val norm = ((value + 1200) / 1200f).coerceIn(0f, 1f)
    val holdNorm = ((s.peakHold + 1200) / 1200f).coerceIn(0f, 1f)
    val color = when { norm > 0.9f -> BadRed; norm > 0.7f -> WarnAmber; else -> OkGreen }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val holdColor = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(30.dp), style = MaterialTheme.typography.labelMedium)
        Canvas(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(track)) {
            drawRect(color, size = androidx.compose.ui.geometry.Size(size.width * norm, size.height))
            if (!avgMode && holdNorm > 0.01f) {
                val x = (size.width * holdNorm).coerceIn(2f, size.width - 2f)
                drawLine(holdColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f)
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(62.dp)) {
            Text("${(value / 10f).roundToInt()} dB", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
            Text("▲${(s.sessionMax / 10f).roundToInt()}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Sparkline(values: List<Int>, color: Color) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(34.dp)) {
        drawLine(grid, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 1f)
        if (values.size < 2) return@Canvas
        val maxV = maxOf(values.max(), 1).toFloat()
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (SPARK_POINTS - 1).coerceAtLeast(1)
            val y = size.height - (v / maxV) * (size.height - 4f) - 2f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
}
