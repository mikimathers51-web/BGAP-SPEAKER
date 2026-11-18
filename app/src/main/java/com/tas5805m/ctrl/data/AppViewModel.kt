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

    private val diagRepo = DiagHistoryRepository(app)
    val diagHistory: StateFlow<List<DiagReport>> = diagRepo.reports.stateIn(
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

    val builtinPresets: List<Preset> = BuiltinPresets.all

    fun savePreset(name: String) {
        val s = state.value
        val clip = listOf("gain", "ml", "mr").map { s.tagInt("CLIP", it) }
        val p = Preset(
            name = name.ifBlank { "Preset ${System.currentTimeMillis() % 10000}" },
            mode = s.mode, xover = s.xover, master = s.master, sat = s.sat,
            sub = s.sub, ctr = s.ctr, gain = s.gain, eqOn = s.eqOn,
            ll = s.ll, lr = s.lr, rl = s.rl, rr = s.rr,
            eqLeft = s.eqLeft, eqRight = s.eqRight,
            created = java.time.Instant.now().toString(),
            clip = if (clip.all { it != null }) clip.map { it ?: 0 } else null,
            subInv = if (s.hasSubInv) s.subInv else null,
            standby = if (s.hasStandby) s.standbyMin else null,
            trim = if (s.hasWsub) s.wsubTrim else null,
            voice = if (s.hasWsub && s.hasVoice) s.voice else null,
            eqCtr = if (s.mode == 3 && s.hasEqc) s.eqCtr else null,
            eqSub = if (s.mode == 3 && s.hasEqc) s.eqSub else null,
        )
        viewModelScope.launch { presetRepo.add(p) }
    }

    /** Simpan EQ + voicing sub saat ini sebagai preset "scene" (tanpa mode/xover/volume). */
    fun saveScene(name: String) {
        val s = state.value
        val p = Preset(
            name = name.ifBlank { "Scene ${System.currentTimeMillis() % 10000}" },
            kind = "scene",
            eqOn = s.eqOn, eqLeft = s.eqLeft, eqRight = s.eqRight,
            created = java.time.Instant.now().toString(),
            voice = if (s.hasWsub && s.hasVoice) s.voice else null,
        )
        viewModelScope.launch { presetRepo.add(p) }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch { presetRepo.removeById(id) }
    }

    /** Terapkan preset; `onDone` menerima null (semua diterima) atau ringkasan perintah yang ditolak firmware. */
    fun applyPreset(p: Preset, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch { onDone(spp.applyPreset(p)) }
    }

    suspend fun exportPresets(): String = presetRepo.exportJson()

    /** Mengembalikan jumlah preset yang berhasil diimpor. */
    suspend fun importPresets(raw: String): Int = presetRepo.importJson(raw)

    /** Simpan teks laporan ke riwayat (maks 20 terbaru). */
    fun saveDiagReport(title: String, text: String) {
        val r = DiagHistory.make(title, text, System.currentTimeMillis())
        viewModelScope.launch { diagRepo.add(r) }
    }

    fun deleteDiagReport(id: String) { viewModelScope.launch { diagRepo.remove(id) } }

    fun clearDiagHistory() { viewModelScope.launch { diagRepo.clear() } }

    fun resetDiagnostics() {
        viewModelScope.launch { spp.resetDiagnostics() }
    }

    /** Muat ulang semua status (caps, dump, status, fault, info) — dipakai tombol Refresh di Diagnosa. */
    fun refreshAll() {
        viewModelScope.launch {
            spp.send("caps"); kotlinx.coroutines.delay(120)
            spp.requestDump(); kotlinx.coroutines.delay(200)
            spp.requestStatus(); spp.requestFault()
            if (state.value.hasInfo) spp.requestInfo()
        }
    }
}
