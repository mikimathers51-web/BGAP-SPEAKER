package com.tas5805m.ctrl.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tas5805m.ctrl.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * Screen Monitor dengan level meter real-time.
 * Bisa dipanggil tanpa argumen: MonitorScreen()
 * Kalau MainActivity-mu memanggil MonitorScreen(navController, ...), 
 * hapus parameter tersebut atau tambahkan default null di signature.
 */
@Composable
fun MonitorScreen() {
    val scroll = rememberScrollState()

    var masterL by remember { mutableStateOf(-60f) }
    var masterR by remember { mutableStateOf(-60f) }
    var masterLPeak by remember { mutableStateOf(-60f) }
    var masterRPeak by remember { mutableStateOf(-60f) }
    var subLvl by remember { mutableStateOf(-60f) }
    var satLLvl by remember { mutableStateOf(-60f) }
    var satRLvl by remember { mutableStateOf(-60f) }
    var subPeak by remember { mutableStateOf(-60f) }
    var satLPeak by remember { mutableStateOf(-60f) }
    var satRPeak by remember { mutableStateOf(-60f) }

    var isRms by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            val t = (System.currentTimeMillis() - start) / 1000f
            val subT = signal(t, -18f, 0f, 1.7f) + sin(t * 0.4f) * 3f
            val satLT = signal(t, -22f, 1.1f, 2.1f)
            val satRT = signal(t, -22f, 2.3f, 1.9f)
            val mLT = maxOf(subT, satLT) + 1.5f
            val mRT = maxOf(subT, satRT) + 1.5f

            val att = if (isRms) 0.15f else 0.40f
            val rel = if (isRms) 0.08f else 0.15f
            val off = if (isRms) -3f else 0f

            masterL = lerpMeter(masterL, mLT + off, att, rel)
            masterR = lerpMeter(masterR, mRT + off, att, rel)
            subLvl  = lerpMeter(subLvl,  subT + off, 0.30f, 0.12f)
            satLLvl = lerpMeter(satLLvl, satLT + off, att, rel)
            satRLvl = lerpMeter(satRLvl, satRT + off, att, rel)

            masterLPeak = updatePeak(masterLPeak, masterL)
            masterRPeak = updatePeak(masterRPeak, masterR)
            subPeak     = updatePeak(subPeak, subLvl)
            satLPeak    = updatePeak(satLPeak, satLLvl)
            satRPeak    = updatePeak(satRPeak, satRLvl)

            delay(33)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Mode toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(false to "PEAK", true to "RMS").forEach { (rms, label) ->
                val active = isRms == rms
                Text(
                    label,
                    color = if (active) AccentCyan else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) AccentCyan.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                        .padding(vertical = 8.dp)
                        .then(Modifier)
                )
            }
        }

        // Master
        GlassPanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MASTER OUTPUT", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
                val clip = masterL > -1f || masterR > -1f
                Text(
                    "CLIP",
                    color = if (clip) BadRed else TextDim,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (clip) BadRed.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MeterPair(
                    leftLevel = masterL, rightLevel = masterR,
                    leftPeak = masterLPeak, rightPeak = masterRPeak
                )
                Spacer(Modifier.width(4.dp))
                Column(
                    modifier = Modifier.height(160.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("0", "−6", "−12", "−24", "−36", "−48", "−60").forEach {
                        Text(it, color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Readout("L", masterL)
                    Readout("R", masterR)
                    Readout("PK", maxOf(masterLPeak, masterRPeak), 14.sp)
                }
            }
        }

        // Channels
        GlassPanel {
            Text("CHANNELS", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChannelColumn("Sub",   subLvl,  subPeak,  Modifier.weight(1f), isSub = true)
                ChannelColumn("Sat L", satLLvl, satLPeak, Modifier.weight(1f))
                ChannelColumn("Sat R", satRLvl, satRPeak, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.03f))
                )
            )
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun MeterPair(leftLevel: Float, rightLevel: Float, leftPeak: Float, rightPeak: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            VerticalMeter(leftLevel, leftPeak, Modifier.width(26.dp).weight(1f, fill = true).height(150.dp))
            Spacer(Modifier.height(6.dp))
            Text("L", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            VerticalMeter(rightLevel, rightPeak, Modifier.width(26.dp).weight(1f, fill = true).height(150.dp))
            Spacer(Modifier.height(6.dp))
            Text("R", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun VerticalMeter(level: Float, peak: Float, modifier: Modifier, isSub: Boolean = false) {
    val animated by animateFloatAsState(
        targetValue = ((level.coerceIn(-60f, 0f) + 60f) / 60f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 800f),
        label = "meter"
    )
    val peakFrac = ((peak.coerceIn(-60f, 0f) + 60f) / 60f)

    Canvas(modifier = modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.04f))) {
        val brush = Brush.verticalGradient(
            colorStops = if (isSub) arrayOf(
                0f to Color(0xFFCA8A04),
                0.55f to Color(0xFFEAB308),
                0.72f to Color(0xFFF59E0B),
                0.86f to Color(0xFFF97316),
                1f to BadRed
            ) else arrayOf(
                0f to OkGreen,
                0.55f to OkGreen,
                0.66f to Color(0xFF84CC16),
                0.78f to WarnAmber,
                0.90f to Color(0xFFF97316),
                1f to BadRed
            ),
            startY = size.height,
            endY = 0f
        )
        val h = size.height * animated
        if (h > 0f) {
            drawRect(brush = brush, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
        }
        if (peakFrac > 0.001f) {
            val y = size.height * (1f - peakFrac)
            drawLine(Color.White, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        }
    }
}

@Composable
private fun ChannelColumn(name: String, level: Float, peak: Float, modifier: Modifier, isSub: Boolean = false) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        VerticalMeter(level, peak, Modifier.fillMaxWidth().weight(1f), isSub = isSub)
        Spacer(Modifier.height(8.dp))
        Text(name, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(fmtDb(level), color = dbColor(level), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Readout(label: String, value: Float, size: androidx.compose.ui.unit.TextUnit = 20.sp) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp, modifier = Modifier.width(24.dp))
        Text(fmtDb(value), color = dbColor(value), fontSize = size,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(4.dp))
        Text("dB", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

// ========================================================
// Helpers
// ========================================================
private fun signal(t: Float, base: Float, offset: Float, speed: Float): Float =
    base +
    sin(t * speed + offset) * 6f +
    sin(t * speed * 2.31f + offset * 1.7f) * 3.5f +
    sin(t * speed * 5.13f + offset * 2.9f) * 1.8f +
    sin(t * speed * 9.7f + offset * 4.1f) * 0.9f

private fun lerpMeter(current: Float, target: Float, attack: Float, release: Float): Float {
    val t = target.coerceIn(-60f, 0f)
    return (current + (t - current) * (if (t > current) attack else release)).coerceIn(-60f, 0f)
}

private fun updatePeak(peak: Float, level: Float): Float =
    if (level > peak) level else (peak - 0.35f).coerceAtLeast(-60f).coerceAtLeast(level)

private fun fmtDb(db: Float): String = when {
    db <= -59.5f -> "−∞"
    db > -0.5f -> "0.0"
    else -> "%.1f".format(db)
}

private fun dbColor(db: Float): Color = when {
    db > -1f -> BadRed
    db > -6f -> WarnAmber
    else -> AccentCyan
}
