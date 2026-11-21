package com.tas5805m.ctrl

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.ui.screens.ConnectScreen
import com.tas5805m.ctrl.ui.screens.ControlScreen
import com.tas5805m.ctrl.ui.screens.DiagScreen
import com.tas5805m.ctrl.ui.screens.DspScreen
import com.tas5805m.ctrl.ui.screens.EqScreen
import com.tas5805m.ctrl.ui.screens.MonitorScreen
import com.tas5805m.ctrl.ui.screens.PresetScreen
import com.tas5805m.ctrl.ui.screens.WsubScreen
import com.tas5805m.ctrl.ui.theme.BadRed
import com.tas5805m.ctrl.ui.theme.OkGreen
import com.tas5805m.ctrl.ui.theme.TasTheme
import com.tas5805m.ctrl.ui.theme.WarnAmber
import com.tas5805m.ctrl.ui.theme.resolveDark
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

private const val TAB_CONNECT = 0
private const val TAB_HOME = 1
private const val TAB_EQ = 2
private const val TAB_DSP = 3
private const val TAB_WIRELESS = 4
private const val TAB_MONITOR = 5
private const val TAB_DIAG = 6

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Daftar perangkat ter-pair baru bisa dibaca setelah izin BLUETOOTH_CONNECT diberikan.
        vm.refreshBonded()
        vm.tryAutoConnect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestBtPermissions()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val reconnecting by vm.reconnecting.collectAsStateWithLifecycle()
            val dark = resolveDark(settings.theme)
            // Ikon status/navigasi bar mengikuti tema pilihan (bukan hanya tema sistem).
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            TasTheme(settings.theme, settings.dynamicColor) {
                val state by vm.state.collectAsStateWithLifecycle()
                val snack = remember { SnackbarHostState() }
                LaunchedEffect(state.lastError) {
                    if (state.lastError.isNotEmpty()) snack.showSnackbar(state.lastError.take(140))
                }
                var tab by rememberSaveable { mutableIntStateOf(TAB_CONNECT) }
                val showWireless = state.connected && (state.hasWsub || state.isWireless)
                val current = if (tab == TAB_WIRELESS && !showWireless) TAB_HOME else tab

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snack) },
                    topBar = { StatusTopBar(state.connected, state.deviceName, reconnecting) },
                    bottomBar = {
                        NavigationBar {
                            val items = buildList {
                                add(Triple("Koneksi", Icons.Default.Bluetooth, TAB_CONNECT))
                                add(Triple("Home", Icons.Default.Tune, TAB_HOME))
                                add(Triple("EQ", Icons.Default.Equalizer, TAB_EQ))
                                add(Triple("DSP", Icons.Default.GraphicEq, TAB_DSP))
                                if (showWireless) add(Triple("Wireless", Icons.Default.SettingsRemote, TAB_WIRELESS))
                                add(Triple("Monitor", Icons.Default.Speed, TAB_MONITOR))
                                add(Triple("Diagnosa", Icons.Default.MonitorHeart, TAB_DIAG))
                            }
                            items.forEach { (label, icon, idx) ->
                                NavigationBarItem(
                                    selected = current == idx,
                                    onClick = { tab = idx },
                                    icon = { Icon(icon, contentDescription = label) },
                                    alwaysShowLabel = items.size <= 6,   // 7 tab: label hanya untuk yang aktif agar muat
                                    label = { Text(label, maxLines = 1) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    when (current) {
                        TAB_CONNECT -> ConnectScreen(vm, Modifier.padding(padding))
                        TAB_HOME -> ControlScreen(vm, Modifier.padding(padding))
                        TAB_EQ -> EqScreen(vm, Modifier.padding(padding))
                        TAB_DSP -> DspAndPresetScreen(vm, Modifier.padding(padding))
                        TAB_WIRELESS -> WsubScreen(vm, Modifier.padding(padding))
                        TAB_MONITOR -> MonitorScreen(vm, Modifier.padding(padding))
                        TAB_DIAG -> DiagScreen(vm, Modifier.padding(padding))
                    }
                }
            }
        }
    }

    private fun requestBtPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_CONNECT
            perms += Manifest.permission.BLUETOOTH_SCAN
        } else {
            perms += Manifest.permission.BLUETOOTH
            perms += Manifest.permission.BLUETOOTH_ADMIN
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        }
        // Sudah diberikan sebelumnya -> tidak ada dialog & callback tidak dipanggil, jadi refresh langsung.
        val missing = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) { vm.refreshBonded(); vm.tryAutoConnect() } else permissionLauncher.launch(missing.toTypedArray())
    }
}

/** Tab DSP berisi dua sub-tab: kontrol DSP chip dan Preset. */
@Composable
fun DspAndPresetScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier) {
        TabRow(selectedTabIndex = sub) {
            Tab(selected = sub == 0, onClick = { sub = 0 }, text = { Text("DSP") })
            Tab(selected = sub == 1, onClick = { sub = 1 }, text = { Text("Preset") })
        }
        when (sub) {
            0 -> DspScreen(vm, Modifier.fillMaxSize())
            1 -> PresetScreen(vm, Modifier.fillMaxSize())
        }
    }
}

/** Top bar: logo, "BGAP-SPEAKER", status mono + titik berdenyut (hijau = tersambung, kuning = menyambung ulang, merah = putus). */
@Composable
private fun StatusTopBar(connected: Boolean, deviceName: String, reconnecting: Int) {
    val (color, label) = when {
        connected -> OkGreen to "TAS5805M · ${deviceName.ifBlank { "terhubung" }}"
        reconnecting > 0 -> WarnAmber to "menyambung ulang ($reconnecting/5)…"
        else -> BadRed to "tidak terhubung"
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "dot",
    )
    val primary = MaterialTheme.colorScheme.primary
    val amber = MaterialTheme.colorScheme.tertiary
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(38.dp)
                    .background(Brush.linearGradient(listOf(primary.copy(alpha = 0.18f), amber.copy(alpha = 0.10f))), RoundedCornerShape(12.dp))
                    .border(1.dp, primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(20.dp)) {   // ikon equalizer: 4 batang
                    val w = size.width; val h = size.height
                    listOf(0.15f to 0.55f, 0.38f to 0.9f, 0.62f to 0.4f, 0.85f to 0.7f).forEach { (x, level) ->
                        drawLine(primary, Offset(w * x, h * (0.5f + level / 2)), Offset(w * x, h * (0.5f - level / 2)), strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text("BGAP-SPEAKER", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).alpha(if (connected) pulse else 1f).background(color, CircleShape))
                    Text(
                        label, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
