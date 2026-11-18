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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MonitorHeart
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
import com.tas5805m.ctrl.ui.screens.PresetScreen
import com.tas5805m.ctrl.ui.screens.WsubScreen
import com.tas5805m.ctrl.ui.theme.TasTheme

private const val TAB_CONNECT = 0
private const val TAB_HOME = 1
private const val TAB_EQ = 2
private const val TAB_DSP = 3
private const val TAB_WIRELESS = 4
private const val TAB_DIAG = 5

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Daftar perangkat ter-pair baru bisa dibaca setelah izin BLUETOOTH_CONNECT diberikan.
        vm.refreshBonded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestBtPermissions()
        setContent {
            TasTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableIntStateOf(TAB_CONNECT) }
                val showWireless = state.connected && (state.hasWsub || state.isWireless)
                val current = if (tab == TAB_WIRELESS && !showWireless) TAB_HOME else tab

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val items = buildList {
                                add(Triple("Koneksi", Icons.Default.Bluetooth, TAB_CONNECT))
                                add(Triple("Home", Icons.Default.Tune, TAB_HOME))
                                add(Triple("EQ", Icons.Default.Equalizer, TAB_EQ))
                                add(Triple("DSP", Icons.Default.GraphicEq, TAB_DSP))
                                if (showWireless) add(Triple("Wireless", Icons.Default.SettingsRemote, TAB_WIRELESS))
                                add(Triple("Diagnosa", Icons.Default.MonitorHeart, TAB_DIAG))
                            }
                            items.forEach { (label, icon, idx) ->
                                NavigationBarItem(
                                    selected = current == idx,
                                    onClick = { tab = idx },
                                    icon = { Icon(icon, contentDescription = label) },
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
        if (missing.isEmpty()) vm.refreshBonded() else permissionLauncher.launch(missing.toTypedArray())
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
