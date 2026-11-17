package com.tas5805m.ctrl

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.ui.screens.ConnectScreen
import com.tas5805m.ctrl.ui.screens.ControlScreen
import com.tas5805m.ctrl.ui.screens.DiagScreen
import com.tas5805m.ctrl.ui.screens.EqScreen
import com.tas5805m.ctrl.ui.screens.PresetScreen
import com.tas5805m.ctrl.ui.screens.WsubScreen
import com.tas5805m.ctrl.ui.theme.TasTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* granted map */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestBtPermissions()
        setContent {
            TasTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableIntStateOf(0) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val items = listOf(
                                Triple("Koneksi", Icons.Default.Bluetooth, 0),
                                Triple("Home", Icons.Default.Tune, 1),
                                Triple("EQ", Icons.Default.Equalizer, 2),
                                Triple("Wireless", Icons.Default.SettingsRemote, 3),
                                Triple("Preset", Icons.Default.MonitorHeart, 4), // reuse icon
                                Triple("Diagnosa", Icons.Default.MonitorHeart, 5),
                            )
                            // Limit to 5 for cleaner nav – merge preset into control if needed
                            listOf(
                                Triple("Koneksi", Icons.Default.Bluetooth, 0),
                                Triple("Home", Icons.Default.Tune, 1),
                                Triple("EQ", Icons.Default.Equalizer, 2),
                                Triple("Wireless", Icons.Default.SettingsRemote, 3),
                                Triple("Diagnosa", Icons.Default.MonitorHeart, 4),
                            ).forEach { (label, icon, idx) ->
                                NavigationBarItem(
                                    selected = tab == idx,
                                    onClick = { tab = idx },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label, maxLines = 1) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    when (tab) {
                        0 -> ConnectScreen(vm, Modifier.padding(padding))
                        1 -> ControlScreen(vm, Modifier.padding(padding))
                        2 -> EqScreen(vm, Modifier.padding(padding))
                        3 -> WsubScreen(vm, Modifier.padding(padding))
                        4 -> DiagAndPresetScreen(vm, Modifier.padding(padding))
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
        permissionLauncher.launch(perms.toTypedArray())
    }
}


@Composable
fun DiagAndPresetScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier) {
        TabRow(selectedTabIndex = sub) {
            Tab(selected = sub == 0, onClick = { sub = 0 }, text = { Text("Diagnosa") })
            Tab(selected = sub == 1, onClick = { sub = 1 }, text = { Text("Preset") })
        }
        when (sub) {
            0 -> DiagScreen(vm, Modifier.fillMaxSize())
            1 -> PresetScreen(vm, Modifier.fillMaxSize())
        }
    }
}

