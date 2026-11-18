package com.tas5805m.ctrl.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Slider yang TIDAK membanjiri firmware.
 *
 * Slider biasa memanggil onValueChange puluhan kali per detik; tiap perintah SPP membuat ESP32 menulis I2C
 * lalu mencetak status panjang (mode wireless: ±1,5 KB) sehingga link Bluetooth tersendat dan A2DP ikut putus-putus.
 *  - [live] = true  : kirim maksimal tiap [throttleMs] saat digeser + nilai akhir saat dilepas.
 *  - [live] = false : kirim HANYA nilai akhir saat dilepas (untuk xover / parameter yang menyetel ulang filter DSP).
 * Selama digeser dan sebentar setelah dilepas, thumb mengikuti jari (bukan nilai lama dari firmware), supaya tidak melompat.
 */
@Composable
fun SendSlider(
    value: Float,
    onSend: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    live: Boolean = true,
    throttleMs: Long = 150L,
    enabled: Boolean = true,
    colors: androidx.compose.material3.SliderColors = SliderDefaults.colors(),
) {
    var local by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var release by remember { mutableIntStateOf(0) }
    val lastSent = remember { LongArray(1) }

    LaunchedEffect(release) {
        if (release > 0) {
            delay(700)
            if (!dragging) local = null
        }
    }

    Slider(
        value = (local ?: value).coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = { v ->
            dragging = true
            local = v
            if (live) {
                val now = System.currentTimeMillis()
                if (now - lastSent[0] >= throttleMs) {
                    lastSent[0] = now
                    onSend(v)
                }
            }
        },
        onValueChangeFinished = {
            dragging = false
            local?.let { onSend(it) }
            release++
        },
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        colors = colors,
        modifier = modifier,
    )
}

/** Versi integer: nilai dibulatkan sebelum dikirim. */
@Composable
fun SendSliderInt(
    value: Int,
    onSend: (Int) -> Unit,
    valueRange: IntRange,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    live: Boolean = true,
    throttleMs: Long = 150L,
    enabled: Boolean = true,
) {
    SendSlider(
        value = value.toFloat(),
        onSend = { onSend(it.roundToInt()) },
        valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
        modifier = modifier,
        steps = steps,
        live = live,
        throttleMs = throttleMs,
        enabled = enabled,
    )
}
