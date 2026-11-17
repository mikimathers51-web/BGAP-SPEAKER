package com.tas5805m.ctrl.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.AppViewModel
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * EQ screen — Axton/DS18 style: frequency response curve + band sliders.
 */
@Composable
fun EqScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spp = vm.spp
    var link by remember { mutableStateOf(true) }

    if (!state.connected) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Hubungkan perangkat dulu", color = Color.Gray)
        }
        return
    }

    val gains = if (link) state.eqLeft else state.eqLeft // curve uses L; R separate when unlinked
    val gainsR = state.eqRight

    Column(modifier.fillMaxSize()) {
        // Toolbar
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.eqOn,
                onClick = { spp.setEq(!state.eqOn) },
                label = { Text(if (state.eqOn) "EQ ON" else "EQ OFF", fontWeight = FontWeight.Bold) }
            )
            FilterChip(
                selected = link,
                onClick = { link = !link },
                label = { Text(if (link) "L+R" else "L / R") }
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { spp.requestEqDump() }) { Text("Sync") }
        }

        if (state.is21 || state.is31) {
            Text(
                "Mode 2.1/3.1: band 0–2 dikunci (crossover)",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFB74D),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // —— Frequency response curve (hero) ——
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(160.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(8.dp)) {
                EqCurve(
                    gainsL = state.eqLeft,
                    gainsR = if (link) state.eqLeft else state.eqRight,
                    linked = link,
                    modifier = Modifier.fillMaxSize()
                )
                // dB labels
                Column(Modifier.align(Alignment.CenterStart).padding(start = 2.dp)) {
                    listOf("+15", "0", "−15").forEach {
                        Text(it, fontSize = 9.sp, color = Color.White.copy(alpha = 0.4f))
                        if (it != "−15") Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // Quick shapes
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val shapes = listOf(
                "Flat" to List(15) { 0f },
                "Bass+" to listOf(6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
                "V-Shape" to listOf(4f, 3f, 1f, 0f, -1f, -2f, -1f, 0f, 1f, 2f, 3f, 3f, 2f, 1f, 0f),
                "Vocal" to listOf(-2f, -1f, 0f, 1f, 2f, 3f, 3f, 2f, 1f, 0f, -1f, -1f, 0f, 0f, 0f),
                "Treble+" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 2f, 3f, 4f, 4f, 3f, 2f),
            )
            shapes.forEach { (name, g) ->
                AssistChip(
                    onClick = {
                        g.forEachIndexed { i, v ->
                            if (!((state.is21 || state.is31) && i <= 2)) {
                                spp.setEqBand("b", i, v)
                            }
                        }
                    },
                    label = { Text(name, fontSize = 12.sp) }
                )
            }
        }

        // Band sliders
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(AmpState.BAND_FREQS) { i, freq ->
                val locked = (state.is21 || state.is31) && i <= 2
                val gL = state.eqLeft.getOrElse(i) { 0f }
                val gR = state.eqRight.getOrElse(i) { 0f }
                var localL by remember(gL) { mutableFloatStateOf(gL) }
                var localR by remember(gR) { mutableFloatStateOf(gR) }

                Card(
                    colors = if (locked)
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    else CardDefaults.cardColors(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                        val freqLabel = when {
                            freq >= 1000 && freq % 1000 == 0 -> "${freq / 1000}k"
                            freq >= 1000 -> "${freq / 1000}.${(freq % 1000) / 100}k"
                            else -> "$freq"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                freqLabel,
                                modifier = Modifier.width(40.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (locked) Color.Gray else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                if (link) "${"%.1f".format(localL)} dB"
                                else "L ${"%.1f".format(localL)}  R ${"%.1f".format(localR)}",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            if (locked) Text("  🔒", fontSize = 11.sp)
                        }
                        if (!locked) {
                            Slider(
                                value = localL,
                                onValueChange = {
                                    localL = it
                                    if (link) localR = it
                                },
                                onValueChangeFinished = {
                                    if (link) spp.setEqBand("b", i, localL)
                                    else spp.setEqBand("l", i, localL)
                                },
                                valueRange = -15f..15f,
                                steps = 59,
                                modifier = Modifier.height(28.dp)
                            )
                            if (!link) {
                                Slider(
                                    value = localR,
                                    onValueChange = { localR = it },
                                    onValueChangeFinished = { spp.setEqBand("r", i, localR) },
                                    valueRange = -15f..15f,
                                    steps = 59,
                                    modifier = Modifier.height(28.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFFF80AB),
                                        activeTrackColor = Color(0xFFFF80AB)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Log-frequency EQ response curve (Axton-style visual).
 * Approximates piecewise-linear response through the 15 band gains.
 */
@Composable
fun EqCurve(
    gainsL: List<Float>,
    gainsR: List<Float>,
    linked: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = Color(0xFFFF80AB)
    val freqs = AmpState.BAND_FREQS

    // Precompute log positions 0..1
    val logPos = remember {
        val minL = ln(freqs.first().toFloat())
        val maxL = ln(freqs.last().toFloat())
        freqs.map { ((ln(it.toFloat()) - minL) / (maxL - minL)).coerceIn(0f, 1f) }
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val scaleY = h / 2f / 15f // ±15 dB

        // Grid: 0 dB + ± lines
        drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, midY), Offset(w, midY), strokeWidth = 1.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, midY - 7.5f * scaleY), Offset(w, midY - 7.5f * scaleY), 1.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, midY + 7.5f * scaleY), Offset(w, midY + 7.5f * scaleY), 1.dp.toPx())

        // Vertical decade marks (approx at band centers)
        logPos.forEach { p ->
            val x = p * w
            drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, h), 1.dp.toPx())
        }

        fun buildPath(gains: List<Float>): Path {
            val path = Path()
            val n = min(gains.size, logPos.size)
            for (i in 0 until n) {
                val x = logPos[i] * w
                val y = midY - gains[i].coerceIn(-15f, 15f) * scaleY
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }

        val pathL = buildPath(gainsL)
        drawPath(pathL, primary, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        // Dots on bands
        val n = min(gainsL.size, logPos.size)
        for (i in 0 until n) {
            val x = logPos[i] * w
            val y = midY - gainsL[i].coerceIn(-15f, 15f) * scaleY
            drawCircle(primary, radius = 3.dp.toPx(), center = Offset(x, y))
        }

        if (!linked) {
            val pathR = buildPath(gainsR)
            drawPath(pathR, secondary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
