package com.tas5805m.ctrl.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.data.FillSample
import com.tas5805m.ctrl.data.PeerDetail
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun WsubScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spp = vm.spp

    // Poll every 1.5s only while connected — avoid hammering SPP
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            if (vm.state.value.connected) {
                spp.requestStatus()
            }
        }
    }

    if (!state.connected) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Hubungkan perangkat dulu")
        }
        return
    }

    // Isolate chart data so peer list doesn't recompose on every fill tick unnecessarily
    // (still same state object, but we pass stable primitives to heavy charts)
    val fillMs = state.wsubFillMs
    val targetMs = state.wsubLat
    val fillHistory = state.fillHistory
    val undHistory = state.undHistory
    val gapHistory = state.gapHistory
    val und = state.wsubUnd
    val gap = state.wsubGap

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") {
            HeaderCard(state)
        }

        item(key = "fill_chart") {
            FillChartCard(
                fillMs = fillMs,
                targetMs = targetMs,
                samples = fillHistory
            )
        }

        item(key = "und_gap") {
            UndGapCard(und = und, gap = gap, undHistory = undHistory, gapHistory = gapHistory)
        }

        item(key = "actions") {
            ActionRow(
                scanning = state.wsubScan == 1,
                onScan = { spp.wsubScan() },
                onReset = { spp.wsubDiagReset() },
                onUnpairAll = { spp.wsubUnpairAll() }
            )
        }

        if (state.wsubFound != "none" && state.wsubFound.isNotEmpty()) {
            item(key = "found") {
                FoundCard(
                    found = state.wsubFound,
                    paired = state.peerDetails.map { it.mac } + state.wsubPeers,
                    onPair = { spp.wsubPair(it) }
                )
            }
        }

        item(key = "peer_title") {
            Text(
                "Peer terpasang (${state.peerDetails.size}/${state.wsubMax})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (state.peerDetails.isEmpty()) {
            item(key = "no_peers") {
                Card {
                    ListItem(
                        headlineContent = { Text("Belum ada peer") },
                        supportingContent = { Text("Tekan Scan Slave lalu Pair MAC yang muncul") }
                    )
                }
            }
        }

        itemsIndexed(
            items = state.peerDetails,
            key = { _, p -> p.mac }
        ) { idx, peer ->
            PeerCardDetailed(
                peer = peer,
                index = idx,
                globalFill = fillMs,
                targetMs = targetMs,
                onUnpair = { spp.wsubUnpair(peer.mac) }
            )
        }

        item(key = "trim") {
            TrimCard(current = state.wsubTrim, onTrim = { spp.wsubTrim(it) })
        }

        item(key = "radio") {
            RadioCard(state)
        }

        item(key = "notes") {
            Text(
                "Catatan multi-sub:\n" +
                "• Max 4 peer (firmware ≥ 2.9.3)\n" +
                "• Grafik hanya update jika nilai berubah (hemat CPU)\n" +
                "• Poll status 1.5 dtk · history max ${AmpState.HISTORY_MAX} titik\n" +
                "• Unpair di slave: tahan BOOT 3 detik",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun HeaderCard(state: AmpState) {
    Card {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SpeakerGroup, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Wireless Sub — Multi-peer", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text("Mode: ${state.modeName()}  ·  Max peers: ${state.wsubMax}")
            Text("Latency target: ${state.wsubLat} ms  ·  Trim: ${state.wsubTrim} ms")
            if (state.wsubFb == 1) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Fallback stereo aktif — sub hilang ≥6 dtk",
                        color = Color(0xFFFFB74D),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun FillChartCard(fillMs: Int, targetMs: Int, samples: List<FillSample>) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Buffer Fill (real-time)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    "$fillMs ms",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Target ≈ $targetMs ms  ·  ${samples.size} titik",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            FillGauge(fillMs = fillMs, targetMs = targetMs, modifier = Modifier.fillMaxWidth().height(28.dp))
            Spacer(Modifier.height(12.dp))
            if (samples.isNotEmpty()) {
                Text("Riwayat fill", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                // key by last sample to skip redraw when identical
                key(samples.lastOrNull()?.fillMs, samples.size, targetMs) {
                    FillSparkline(
                        samples = samples,
                        targetMs = targetMs,
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )
                }
            } else {
                Text(
                    "Menunggu data WSUB…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun UndGapCard(und: Int, gap: Int, undHistory: List<Int>, gapHistory: List<Int>) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Text("Underrun & Gap", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Underrun: $und", style = MaterialTheme.typography.labelMedium)
                    key(undHistory.lastOrNull(), undHistory.size) {
                        MiniBarHistory(
                            values = undHistory,
                            color = Color(0xFFEF5350),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Gap: $gap", style = MaterialTheme.typography.labelMedium)
                    key(gapHistory.lastOrNull(), gapHistory.size) {
                        MiniBarHistory(
                            values = gapHistory,
                            color = Color(0xFFFFB74D),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    scanning: Boolean,
    onScan: () -> Unit,
    onReset: () -> Unit,
    onUnpairAll: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onScan) {
            Icon(Icons.Default.Radar, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (scanning) "Scanning…" else "Scan Slave")
        }
        OutlinedButton(onClick = onReset) {
            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Reset Diag")
        }
        OutlinedButton(onClick = onUnpairAll) {
            Icon(Icons.Default.LinkOff, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Unpair All")
        }
    }
}

@Composable
private fun FoundCard(found: String, paired: List<String>, onPair: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))) {
        Column(Modifier.padding(12.dp)) {
            Text("Ditemukan saat scan", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            found.split(",").filter { it.isNotBlank() && it != "none" }.forEach { mac ->
                val already = paired.any { it.equals(mac, true) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(mac, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    if (already) {
                        Text("Sudah paired", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    } else {
                        Button(
                            onClick = { onPair(mac) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.AddLink, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pair")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrimCard(current: Int, onTrim: (Int) -> Unit) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Text("Trim Latency (ms relatif 70 ms)", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(-40, -20, -10, 0, 10, 20, 40, 60).forEach { t ->
                    FilterChip(
                        selected = current == t,
                        onClick = { onTrim(t) },
                        label = { Text("$t") }
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioCard(state: AmpState) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Text("Radio Counters", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "RX a/c: ${state.wsubRxa}/${state.wsubRxc}  ·  Rej: ${state.wsubRej}  ·  STX: ${state.wsubStx}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Fill: ${state.wsubFillMs} ms  ·  Und: ${state.wsubUnd}  ·  Gap: ${state.wsubGap}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// -------------------- Charts (optimized) --------------------

@Composable
fun FillGauge(fillMs: Int, targetMs: Int, modifier: Modifier = Modifier) {
    val progress = if (targetMs > 0) (fillMs.toFloat() / (targetMs * 1.5f)).coerceIn(0f, 1f) else 0f
    val color = when {
        fillMs <= 0 -> Color(0xFFEF5350)
        fillMs < targetMs * 0.5f -> Color(0xFFFFB74D)
        fillMs > targetMs * 1.3f -> Color(0xFF4FC3F7)
        else -> Color(0xFF69F0AE)
    }
    // Cache target fraction
    val targetFrac = remember(targetMs) {
        if (targetMs > 0) (targetMs.toFloat() / (targetMs * 1.5f)).coerceIn(0f, 1f) else 0.66f
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width * progress
            if (w > 0f) {
                drawRect(color = color, size = Size(w, size.height))
            }
            val tx = size.width * targetFrac
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(tx, 0f),
                end = Offset(tx, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (fillMs <= 0) "EMPTY" else "$fillMs ms",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FillSparkline(
    samples: List<FillSample>,
    targetMs: Int,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    // Precompute maxY outside draw when samples change
    val maxY = remember(samples, targetMs) {
        val m = samples.maxOfOrNull { it.fillMs }?.toFloat() ?: 0f
        max(m, targetMs * 1.2f).coerceAtLeast(1f)
    }
    // Extract primitive array for faster draw loop (avoid object field access in tight loop)
    val fills = remember(samples) { FloatArray(samples.size) { samples[it].fillMs.toFloat() } }

    Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E))) {
        val n = fills.size
        if (n < 2) return@Canvas

        val stepX = size.width / (n - 1)
        val h = size.height
        val stroke = 2.5.dp.toPx()
        val targetY = h - (targetMs / maxY) * h

        // target line
        drawLine(
            Color.White.copy(alpha = 0.35f),
            Offset(0f, targetY),
            Offset(size.width, targetY),
            strokeWidth = 1.dp.toPx()
        )

        // Build path once per frame
        val line = Path()
        val area = Path()
        for (i in 0 until n) {
            val x = i * stepX
            val y = h - (fills[i] / maxY) * h
            if (i == 0) {
                line.moveTo(x, y)
                area.moveTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(size.width, h)
        area.lineTo(0f, h)
        area.close()

        drawPath(area, primary.copy(alpha = 0.2f))
        drawPath(line, primary, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // last point
        val lx = (n - 1) * stepX
        val ly = h - (fills[n - 1] / maxY) * h
        drawCircle(primary, radius = 4.dp.toPx(), center = Offset(lx, ly))
    }
}

@Composable
fun MiniBarHistory(values: List<Int>, color: Color, modifier: Modifier = Modifier) {
    val arr = remember(values) { IntArray(values.size) { values[it] } }
    val maxV = remember(values) { (values.maxOrNull() ?: 1).coerceAtLeast(1).toFloat() }

    Canvas(modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E1E1E))) {
        val n = arr.size
        if (n == 0) return@Canvas
        val barW = size.width / n
        val gap = barW * 0.3f
        val body = barW - gap
        for (i in 0 until n) {
            val h = (arr[i] / maxV) * size.height
            if (h > 0.5f) {
                drawRect(
                    color = color.copy(alpha = 0.85f),
                    topLeft = Offset(i * barW, size.height - h),
                    size = Size(body, h)
                )
            }
        }
    }
}

@Composable
private fun PeerCardDetailed(
    peer: PeerDetail,
    index: Int,
    globalFill: Int,
    targetMs: Int,
    onUnpair: () -> Unit
) {
    val linkColor = if (peer.link) Color(0xFF69F0AE) else Color(0xFFEF5350)
    val fill = if (peer.fillMs > 0) peer.fillMs else globalFill
    Card {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(linkColor))
                Spacer(Modifier.width(8.dp))
                Text("Peer ${index + 1}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    if (peer.link) "LINK OK" else "PUTUS",
                    color = linkColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(peer.mac, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            FillGauge(fillMs = fill, targetMs = targetMs, modifier = Modifier.fillMaxWidth().height(18.dp))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("Fill", "${peer.fillMs} ms")
                StatChip("Und", "${peer.und}")
                StatChip("Gap", "${peer.gap}")
                if (peer.rxa >= 0) StatChip("RX", "${peer.rxa}/${peer.rxc}")
                if (peer.hiz) StatChip("Hi-Z", "ON")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onUnpair,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Unpair peer ini")
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            "$label $value",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
