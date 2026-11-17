package com.tas5805m.ctrl.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tas5805m.ctrl.bluetooth.SppClient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val spp = SppClient(app)

    val state: StateFlow<AmpState> = spp.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AmpState()
    )
    val bonded = spp.bonded

    private val presetRepo = PresetRepository(app)
    val presets: StateFlow<List<Preset>> = presetRepo.presets.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    init {
        spp.refreshBonded()
    }

    fun refreshBonded() = spp.refreshBonded()

    fun connect(device: android.bluetooth.BluetoothDevice) {
        viewModelScope.launch { spp.connect(device) }
    }

    fun disconnect() = spp.disconnect()
    fun send(cmd: String) = spp.send(cmd)

    fun savePreset(name: String) {
        val s = state.value
        val p = Preset(
            name = name.ifBlank { "Preset ${System.currentTimeMillis() % 10000}" },
            mode = s.mode, xover = s.xover, master = s.master, sat = s.sat,
            sub = s.sub, ctr = s.ctr, gain = s.gain, eqOn = s.eqOn,
            ll = s.ll, lr = s.lr, rl = s.rl, rr = s.rr,
            eqLeft = s.eqLeft, eqRight = s.eqRight,
            created = java.time.Instant.now().toString()
        )
        viewModelScope.launch { presetRepo.add(p) }
    }

    fun deletePreset(index: Int) {
        viewModelScope.launch { presetRepo.remove(index) }
    }

    fun applyPreset(p: Preset) {
        viewModelScope.launch { spp.applyPreset(p) }
    }
}
