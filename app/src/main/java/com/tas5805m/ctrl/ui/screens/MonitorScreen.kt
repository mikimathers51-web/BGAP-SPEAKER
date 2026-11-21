package com.bgap.speaker.ui.monitor

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bgap.speaker.ui.components.GlassCard
import com.bgap.speaker.ui.components.PeerMeterRow
import com.bgap.speaker.ui.components.VerticalLevelMeter
import com.bgap.speaker.ui.theme.BgapColors

@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.start()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Toolbar: mode toggle + reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeToggle(
                current = state.mode,
                onModeChange = viewModel::setMode,
                modifier = Modifier.weight(1f)
            )
            ResetPeaksButton(onClick = viewModel::resetPeaks)
        }

        // Master Output
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "MASTER OUTPUT",
                    color = BgapColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
                ClipLed(active = state.masterL.levelDb > -1f || state.masterR.levelDb > -1f)
            }
            Spacer(Modifier.height(12.dp))
            MasterMetersBlock(state = state)
        }

        // Channels
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CHANNELS",
                    color = BgapColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
                Text(
                    "from TAS5805M",
                    color = BgapColors.Primary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgapColors.Primary.copy(alpha = 0.10f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            ChannelMetersBlock(state = state)
        }

        // Peers
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "WIRELESS PEERS",
                color = BgapColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PeerMeterRow("Sub 1", "LR", state.peer1.levelDb, state.peer1.peakDb)
                PeerMeterRow("Sub 2", "Kitchen", state.peer2.levelDb, state.peer2.peakDb)
                PeerMeterRow("Sub 3", "Bedroom", state.peer3.levelDb, state.peer3.peakDb)
            }
        }
    }
}

@Composable
private fun ModeToggle(
    current: MeterMode,
    onModeChange: (MeterMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(2.dp)
    ) {
        ModeButton("PEAK", current == MeterMode.PEAK, Modifier.weight(1f)) {
            onModeChange(MeterMode.PEAK)
        }
        ModeButton("RMS", current == MeterMode.RMS, Modifier.weight(1f)) {
            onModeChange(MeterMode.RMS)
        }
    }
}

@Composable
private fun ModeButton(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) BgapColors.Primary.copy(alpha = 0.18f) else Color.Transparent
            )
    ) {
        Text(
            label,
            color = if (active) BgapColors.Primary else BgapColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ResetPeaksButton(onClick: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BgapColors.Danger.copy(alpha = 0.10f))
    ) {
        Text(
            "Reset Peaks",
            color = BgapColors.Danger,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ClipLed(active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (active) BgapColors.Danger else Color(0xFF1A1A1A))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "CLIP",
            color = if (active) BgapColors.Danger else BgapColors.TextDim,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun MasterMetersBlock(state: MonitorUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Dual meters
        Row(
            modifier = Modifier.height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VerticalLevelMeter(
                    levelDb = state.masterL.levelDb,
                    peakDb = state.masterL.peakDb,
                    isClipping = state.masterL.isClipping,
                    modifier = Modifier
                        .width(26.dp)
                        .weight(1f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "L",
                    color = BgapColors.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VerticalLevelMeter(
                    levelDb = state.masterR.levelDb,
                    peakDb = state.masterR.peakDb,
                    isClipping = state.masterR.isClipping,
                    modifier = Modifier
                        .width(26.dp)
                        .weight(1f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "R",
                    color = BgapColors.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // dB scale
        Column(
            modifier = Modifier
                .height(160.dp)
                .width(30.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("0", "−6", "−12", "−24", "−36", "−48", "−60").forEach {
                Text(
                    it,
                    color = BgapColors.TextDim,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Readouts
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            ReadoutRow("L", state.masterL.levelDb)
            ReadoutRow("R", state.masterR.levelDb)
            ReadoutRow(
                "PK",
                maxOf(state.masterL.peakDb, state.masterR.peakDb),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ReadoutRow(label: String, value: Float, fontSize: androidx.compose.ui.unit.TextUnit = 20.sp) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            label,
            color = BgapColors.TextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp,
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = when {
                value <= -59.5f -> "−∞"
                value > -0.05f -> "0.0"
                else -> "%.1f".format(value)
            },
            color = when {
                value > -1f -> BgapColors.Danger
                value > -6f -> BgapColors.Warn
                else -> BgapColors.Primary
            },
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "dB",
            color = BgapColors.TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ChannelMetersBlock(state: MonitorUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChannelMeterColumn(
            name = "Sub",
            reading = state.sub,
            isSub = true,
            modifier = Modifier.weight(1f)
        )
        ChannelMeterColumn(
            name = "Sat L",
            reading = state.satL,
            modifier = Modifier.weight(1f)
        )
        ChannelMeterColumn(
            name = "Sat R",
            reading = state.satR,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChannelMeterColumn(
    name: String,
    reading: MeterReading,
    modifier: Modifier = Modifier,
    isSub: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VerticalLevelMeter(
            levelDb = reading.levelDb,
            peakDb = reading.peakDb,
            isSub = isSub,
            isClipping = reading.isClipping,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            name,
            color = BgapColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = when {
                reading.levelDb <= -59.5f -> "−∞"
                reading.levelDb > -0.05f -> "0.0"
                else -> "%.1f".format(reading.levelDb)
            },
            color = when {
                reading.levelDb > -1f -> BgapColors.Danger
                reading.levelDb > -6f -> BgapColors.Warn
                else -> BgapColors.TextMuted
            },
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
