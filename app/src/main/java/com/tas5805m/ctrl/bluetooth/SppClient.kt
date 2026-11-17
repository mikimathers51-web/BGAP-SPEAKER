package com.tas5805m.ctrl.bluetooth // Sesuaikan dengan package aplikasi Anda

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.tas5805m.ctrl.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class SppClient(
    private val adapter: BluetoothAdapter?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val _state = MutableStateFlow(AmpState())
    val state: StateFlow<AmpState> = _state.asStateFlow()

    private val _rawLogs = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val rawLogs: SharedFlow<String> = _rawLogs.asSharedFlow()

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null
    private val writeMutex = Mutex()

    // =========================================================================
    // KONEKSI BLUETOOTH SPP
    // =========================================================================

    @SuppressLint("MissingPermission")
    suspend fun connect(deviceAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        disconnect()
        val btAdapter = adapter ?: return@withContext Result.failure(IllegalStateException("Bluetooth tidak tersedia"))
        try {
            if (btAdapter.isDiscovering) btAdapter.cancelDiscovery()
            val device: BluetoothDevice = btAdapter.getRemoteDevice(deviceAddress)
            val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
            sock.connect()

            socket = sock
            outputStream = sock.outputStream
            _state.update { AmpState(connected = true) }

            startReader(sock)

            // Deteksi capabilities lalu lakukan sinkronisasi penuh (v3 dump atau fallback status)
            sendRaw("caps")
            delay(80)
            sendRaw("dump")
            Result.success(Unit)
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        outputStream = null
        socket = null
        _state.update { it.copy(connected = false) }
    }

    private fun startReader(sock: BluetoothSocket) {
        readJob = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(sock.inputStream, Charsets.US_ASCII))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        _rawLogs.tryEmit(trimmed)
                        parseLine(trimmed)
                    }
                }
            } catch (_: Exception) {
                // Socket tertutup / terputus
            } finally {
                _state.update { it.copy(connected = false) }
            }
        }
    }

    suspend fun sendRaw(cmd: String): Boolean = withContext(Dispatchers.IO) {
        val out = outputStream ?: return@withContext false
        writeMutex.withLock {
            try {
                val clean = cmd.trim().take(198) + "\n"
                out.write(clean.toByteArray(Charsets.US_ASCII))
                out.flush()
                true
            } catch (e: Exception) {
                disconnect()
                false
            }
        }
    }

    // =========================================================================
    // API PERINTAH FIRMWARE (LENGKAP v1 - v3.6 + WIRELESS SUB)
    // =========================================================================

    // --- Sinkronisasi & Sistem ---
    suspend fun requestCaps() = sendRaw("caps")
    suspend fun requestFullDump() = sendRaw("dump")
    suspend fun requestStatus() = sendRaw("status")
    suspend fun requestInfo() = sendRaw("info")
    suspend fun ping() = sendRaw("ping")
    suspend fun readAndClearFaults() = sendRaw("fault")
    suspend fun saveToNvs() = sendRaw("save")
    suspend fun eraseNvs() = sendRaw("erase")
    suspend fun checkSavedNvs() = sendRaw("saved")
    suspend fun rebootDevice() = sendRaw("reboot")

    // --- Mode, Crossover & Volume ---
    suspend fun setMode(mode: Int) = sendRaw("mode ${mode.coerceIn(0, 4)}")
    suspend fun setCrossoverHz(hz: Int) = sendRaw("xover ${hz.coerceIn(60, 150)}")
    suspend fun setMasterVolume(vol: Int) = sendRaw("master ${vol.coerceIn(0, 100)}")
    suspend fun setSatelliteVolume(vol: Int) = sendRaw("satvol ${vol.coerceIn(0, 100)}")
    suspend fun setCenterVolume(vol: Int) = sendRaw("ctr ${vol.coerceIn(0, 100)}")
    suspend fun setSubVolume(vol: Int) = sendRaw("subvol ${vol.coerceIn(0, 100)}")
    suspend fun setAnalogGainCode(code: Int) = sendRaw("gain ${code.coerceIn(0, 31)}")

    /** Konversi dB (0.0 .. -15.5 dB) ke kode gain 0..31 */
    suspend fun setAnalogGainDb(db: Float) {
        val clamped = db.coerceIn(-15.5f, 0f)
        val code = ((-clamped) * 2.0f).roundToInt().coerceIn(0, 31)
        setAnalogGainCode(code)
    }

    // --- Power State & FX ---
    /** state: "play" | "mute" | "hiz" | "sleep" | "deepsleep" */
    suspend fun setAmpState(state: String) = sendRaw("ampstate ${state.lowercase(Locale.US)}")
    suspend fun setSubPolarityInvert(invert: Boolean) = sendRaw("subinv ${if (invert) 1 else 0}")
    suspend fun setAutoStandbyMinutes(minutes: Int) = sendRaw("standby ${minutes.coerceIn(0, 240)}")

    // --- EQ & Crossover Profiles ---
    suspend fun setEqEnabled(enabled: Boolean) = sendRaw(if (enabled) "eq on" else "eq off")
    suspend fun requestEqDump() = sendRaw("eqdump")

    /**
     * @param channel "l", "r", atau "b" (both)
     * @param band 0..14 (di mode 1/2/3/4, band 0..2 dikunci untuk crossover)
     * @param gainDb -15..+15 dB
     */
    suspend fun setEqBand(channel: String, band: Int, gainDb: Int): Boolean {
        val ch = channel.lowercase(Locale.US)
        val b = band.coerceIn(0, 14)
        val g = gainDb.coerceIn(-15, 15)
        return if (_state.value.supportsV2) {
            sendRaw("eqband $ch $b $g")
        } else {
            // Fallback firmware v1
            sendRaw("eq l $b $g")
        }
    }

    /**
     * Khusus Mode 3 (2-Chip 3.1): mengatur EQ Center ('c') atau Sub ('s') pada Chip 2.
     * Band valid: 3..14, Gain: -15..+15 dB.
     */
    suspend fun setEqCenterOrSub(target: Char, band: Int, gainDb: Int): Boolean {
        val t = target.lowercaseChar()
        if (t != 'c' && t != 's') return false
        return sendRaw("eqc $t ${band.coerceIn(3, 14)} ${gainDb.coerceIn(-15, 15)}")
    }

    /**
     * Profil filter bawaan (hanya aktif di Mode 0 / non-2.1).
     * 0=FLAT, 1..10=LPF 60..150Hz, 11..20=HPF 60..150Hz
     */
    suspend fun setEqProfile(channel: String, profileId: Int) =
        sendRaw("eqpset ${channel.lowercase(Locale.US)} ${profileId.coerceIn(0, 20)}")

    // --- Mixer Matrix & Stereo Widening ---
    /**
     * @param element "ll" | "lr" | "rl" | "rr"
     * @param gain -1.0f .. 2.0f
     */
    suspend fun setMixerGain(element: String, gain: Float) {
        val g = gain.coerceIn(-1.0f, 2.0f)
        sendRaw(String.format(Locale.US, "mixer %s %.2f", element.lowercase(Locale.US), g))
    }

    /**
     * Pelebaran stereo (Mid/Side): LL=RR=1+w, LR=RL=-w (w = 0.0f .. 0.5f).
     */
    suspend fun setStereoWidening(width: Float) {
        val w = width.coerceIn(0.0f, 0.5f)
        val main = 1.0f + w
        val cross = -w
        setMixerGain("ll", main)
        setMixerGain("rr", main)
        setMixerGain("lr", cross)
        setMixerGain("rl", cross)
    }

    /** mode: "stereo" | "inv" | "mono" | "left" | "right" */
    suspend fun setMixerMode(mode: String) = sendRaw("mixmode ${mode.lowercase(Locale.US)}")

    // --- Konfigurasi Hardware DAC, Modulasi & Clipper (v3) ---
    suspend fun getDacMode() = sendRaw("dacget")
    /** mode: "btl" | "pbtl" (Mode 1/3 hanya mengubah Chip 2) */
    suspend fun setDacMode(mode: String) = sendRaw("dacset ${mode.lowercase(Locale.US)}")

    suspend fun getModulation() = sendRaw("modget")
    /**
     * @param mode "bd" | "1spw" | "hybrid"
     * @param freq "384k" | "480k" | "576k" | "768k"
     * @param bdFreq "80k" | "100k" | "120k" | "175k"
     */
    suspend fun setModulation(mode: String, freq: String, bdFreq: String) =
        sendRaw("modset ${mode.lowercase(Locale.US)} ${freq.lowercase(Locale.US)} ${bdFreq.lowercase(Locale.US)}")

    suspend fun getClipper() = sendRaw("clipget")
    /**
     * @param gainDeciDb -300..0 (mis. -30 = -3.0 dB)
     * @param marginLeftDeciDb 0..300
     * @param marginRightDeciDb 0..300
     */
    suspend fun setClipper(gainDeciDb: Int, marginLeftDeciDb: Int, marginRightDeciDb: Int) =
        sendRaw("clipset ${gainDeciDb.coerceIn(-300, 0)} ${marginLeftDeciDb.coerceIn(0, 300)} ${marginRightDeciDb.coerceIn(0, 300)}")

    // --- Level Meters (Polling Cepat) ---
    suspend fun pollLevels() {
        val s = _state.value
        if (!s.connected) return
        if (s.supportsV2) sendRaw("lvl")
        if (s.supportsTwoChip && (s.audioMode == 1 || s.audioMode == 3)) sendRaw("lvl2")
        if (s.supportsWsubLevel && s.audioMode == 4) sendRaw("wlvl")
    }

    // --- Wireless Sub (Mode 4 / ESP-NOW) ---
    suspend fun wsubScan() = sendRaw("wsub_scan")
    suspend fun wsubPair(mac: String) = sendRaw("wsub_pair ${mac.uppercase(Locale.US)}")
    suspend fun wsubUnpair(mac: String) = sendRaw("wsub_unpair ${mac.uppercase(Locale.US)}")
    suspend fun wsubUnpairAll() = sendRaw("wsub_unpair_all")
    suspend fun wsubSetTrimMs(trimMs: Int) = sendRaw("wsub_trim ${trimMs.coerceIn(-60, 100)}")
    suspend fun wsubDiagReset() = sendRaw("wsub_diag_reset")

    /**
     * Mengatur Voicing DSP pada Wireless Sub Slave.
     */
    suspend fun wsubSetVoice(
        enabled: Boolean,
        hpfHz: Int,
        peakHz: Int,
        peakDb10: Int,
        peakQ10: Int,
        harmonics: Int,
        limiter: Int
    ) = sendRaw(
        "wsub_voice ${if (enabled) 1 else 0} $hpfHz $peakHz $peakDb10 $peakQ10 $harmonics $limiter"
    )

    // =========================================================================
    // PARSER PROTOKOL FIRMWARE
    // =========================================================================

    private fun parseLine(line: String) {
        when {
            line.startsWith("OK ") -> {
                _state.update { it.copy(lastOkCmd = line.removePrefix("OK ").trim(), lastError = null) }
            }
            line.startsWith("ERR ") -> {
                _state.update { it.copy(lastError = line.removePrefix("ERR ").trim()) }
            }
            line.startsWith("CAPS ") -> {
                val tokens = line.removePrefix("CAPS ").split(" ").filter { it.isNotBlank() }.toSet()
                _state.update { it.copy(caps = tokens) }
            }
            line.startsWith("MODE ") -> parseModeLine(line)
            line.startsWith("STATE ") -> parseStateLine(line)
            line.startsWith("FX ") -> parseFxLine(line)
            line.startsWith("FAULT ") -> _state.update { it.copy(fault1 = parseFaultLine(line)) }
            line.startsWith("FAULT2 ") -> _state.update { it.copy(fault2 = parseFaultLine(line), fault2Available = true) }
            line == "FAULT_ERR" -> _state.update { it.copy(fault1 = it.fault1.copy(readError = true)) }
            line == "FAULT2_ERR" -> _state.update { it.copy(fault2 = it.fault2.copy(readError = true), fault2Available = true) }
            line.startsWith("EQ l=") -> _state.update { it.copy(eqLeft = parseIntCsv(line.removePrefix("EQ l="))) }
            line.startsWith("EQ r=") -> _state.update { it.copy(eqRight = parseIntCsv(line.removePrefix("EQ r="))) }
            line.startsWith("EQC ") -> _state.update { it.copy(eqCenter = parseIntCsv(line.removePrefix("EQC "))) }
            line.startsWith("EQS ") -> _state.update { it.copy(eqSub = parseIntCsv(line.removePrefix("EQS "))) }
            line.startsWith("EQM ") -> {
                val reg = line.removePrefix("EQM ").trim().toIntOrNull() ?: 6
                _state.update { it.copy(eqModeReg = reg, eqOn = (reg == 6 || reg == 14)) }
            }
            line.startsWith("EQP ") -> {
                val kv = parseKv(line.removePrefix("EQP "))
                _state.update {
                    it.copy(
                        eqp1Left = kv["l"]?.toIntOrNull() ?: it.eqp1Left,
                        eqp1Right = kv["r"]?.toIntOrNull() ?: it.eqp1Right
                    )
                }
            }
            line.startsWith("EQP2 ") -> {
                val kv = parseKv(line.removePrefix("EQP2 "))
                _state.update {
                    it.copy(
                        eqp2Left = kv["l"]?.toIntOrNull() ?: it.eqp2Left,
                        eqp2Right = kv["r"]?.toIntOrNull() ?: it.eqp2Right
                    )
                }
            }
            line.startsWith("LVL ") -> {
                val kv = parseKv(line.removePrefix("LVL "))
                _state.update {
                    it.copy(
                        lvl1L = kv["l"]?.toIntOrNull() ?: it.lvl1L,
                        lvl1R = kv["r"]?.toIntOrNull() ?: it.lvl1R
                    )
                }
            }
            line.startsWith("LVL2 ") -> {
                val kv = parseKv(line.removePrefix("LVL2 "))
                _state.update {
                    it.copy(
                        lvl2L = kv["l"]?.toIntOrNull() ?: it.lvl2L,
                        lvl2R = kv["r"]?.toIntOrNull() ?: it.lvl2R
                    )
                }
            }
            line.startsWith("WLVL") -> parseWlvlLine(line)
            line.startsWith("DAC ") -> {
                val kv = parseKv(line.removePrefix("DAC "))
                _state.update { it.copy(dacMode1 = kv["mode"] ?: it.dacMode1) }
            }
            line.startsWith("DAC2 ") -> {
                val kv = parseKv(line.removePrefix("DAC2 "))
                _state.update { it.copy(dacMode2 = kv["mode"] ?: it.dacMode2) }
            }
            line.startsWith("MOD ") -> {
                val kv = parseKv(line.removePrefix("MOD "))
                _state.update {
                    it.copy(
                        modMode = kv["mode"] ?: it.modMode,
                        modFreq = kv["freq"] ?: it.modFreq,
                        modBdFreq = kv["bd"] ?: it.modBdFreq
                    )
                }
            }
            line.startsWith("CLIP ") -> {
                val kv = parseKv(line.removePrefix("CLIP "))
                _state.update {
                    it.copy(
                        clipGain = kv["gain"]?.toIntOrNull() ?: it.clipGain,
                        clipMarginL = kv["ml"]?.toIntOrNull() ?: it.clipMarginL,
                        clipMarginR = kv["mr"]?.toIntOrNull() ?: it.clipMarginR
                    )
                }
            }
            line.startsWith("INFO ") -> {
                val kv = parseKv(line.removePrefix("INFO "))
                _state.update {
                    it.copy(
                        infoFs = kv["fs"] ?: it.infoFs,
                        infoBck = kv["bck"]?.toIntOrNull() ?: it.infoBck,
                        infoPwr = kv["pwr"] ?: it.infoPwr,
                        infoMuteR = kv["mute_r"] == "1",
                        infoMuteL = kv["mute_l"] == "1",
                        infoSampleRate = kv["rate"]?.toIntOrNull() ?: it.infoSampleRate
                    )
                }
            }
            line.startsWith("SAVED ") -> {
                val saved = line.removePrefix("SAVED ").trim() == "1"
                _state.update { it.copy(savedInNvs = saved) }
            }
            line.startsWith("VER ") -> {
                val kv = parseKv(line.removePrefix("VER "))
                _state.update {
                    it.copy(
                        fwVersion = kv["fw"] ?: it.fwVersion,
                        fwRole = kv["role"] ?: it.fwRole,
                        freeHeap = kv["heap"]?.toLongOrNull() ?: it.freeHeap,
                        freePsram = kv["psram"]?.toLongOrNull() ?: it.freePsram
                    )
                }
            }
            line.startsWith("XOVT ") -> {
                val kv = parseKv(line.removePrefix("XOVT "))
                _state.update {
                    it.copy(
                        lastXoverTiming = XoverTiming(
                            hz = kv["hz"]?.toIntOrNull() ?: 0,
                            applyMs = kv["apply"]?.toLongOrNull() ?: 0L,
                            statusMs = kv["status"]?.toLongOrNull() ?: 0L,
                            stackFree = kv["stack_free"]?.toLongOrNull() ?: 0L,
                            heapFree = kv["heap"]?.toLongOrNull() ?: 0L,
                            heapMin = kv["heapmin"]?.toLongOrNull() ?: 0L
                        )
                    )
                }
            }
            // Wireless Sub Lines
            line.startsWith("WSUB ") -> parseWsubStatusLine(line)
            line.startsWith("WSUBS ") -> parseWsubSlaveLine(line)
            line.startsWith("WSUBD ") -> parseWsubDiagLine(line)
            line.startsWith("WSUBV ") -> parseWsubVoiceLine(line)
            line.startsWith("WSUBT ") -> parseWsubTxLine(line)
            line.startsWith("WSUBC ") -> parseWsubCpuLine(line)
            line.startsWith("BTD ") -> parseBtDiagLine(line)
            line.startsWith("BTEV ") -> {
                _state.update {
                    val updated = (it.wsub.recentBtEvents + line).takeLast(32)
                    it.copy(wsub = it.wsub.copy(recentBtEvents = updated))
                }
            }
        }
    }

    private fun parseModeLine(line: String) {
        // Format: MODE <m> xover=<hz> master=<n> sat=<n> ctr=<n> sub=<n>
        val parts = line.split(" ")
        val modeNum = parts.getOrNull(1)?.toIntOrNull()
        val kv = parseKv(line)
        _state.update {
            it.copy(
                audioMode = modeNum ?: it.audioMode,
                xoverHz = kv["xover"]?.toIntOrNull() ?: it.xoverHz,
                masterVol = kv["master"]?.toIntOrNull() ?: it.masterVol,
                satVol = kv["sat"]?.toIntOrNull() ?: it.satVol,
                ctrVol = kv["ctr"]?.toIntOrNull() ?: it.ctrVol,
                subVol = kv["sub"]?.toIntOrNull() ?: it.subVol
            )
        }
    }

    private fun parseStateLine(line: String) {
        // Format: STATE gain=18 amp=play eq=1 ll=1.00 lr=0.00 rl=0.00 rr=1.00
        val kv = parseKv(line.removePrefix("STATE "))
        _state.update {
            it.copy(
                gainCode = kv["gain"]?.toIntOrNull() ?: it.gainCode,
                ampState = kv["amp"] ?: it.ampState,
                eqOn = kv["eq"]?.let { v -> v == "1" } ?: it.eqOn,
                mixLL = kv["ll"]?.toFloatOrNull() ?: it.mixLL,
                mixLR = kv["lr"]?.toFloatOrNull() ?: it.mixLR,
                mixRL = kv["rl"]?.toFloatOrNull() ?: it.mixRL,
                mixRR = kv["rr"]?.toFloatOrNull() ?: it.mixRR
            )
        }
    }

    private fun parseFxLine(line: String) {
        // Format: FX inv=0|1 stby=<menit> sb=0|1
        val kv = parseKv(line.removePrefix("FX "))
        _state.update {
            it.copy(
                subInv = kv["inv"]?.let { v -> v == "1" } ?: it.subInv,
                standbyMin = kv["stby"]?.toIntOs: AmpState, line: String): AmpState {
        var l = s.eqpLeft; var r = s.eqpRight
        for (p in line.split(Regex("\\s+")).drop(1)) {
            val kv = p.split("=")
            if (kv.size != 2) continue
            val v = kv[1].toIntOrNull() ?: continue
            if (kv[0] == "l") l = v
            if (kv[0] == "r") r = v
        }
        return s.copy(eqpLeft = l, eqpRight = r)
    }

    private fun parseLvl(s: AmpState, line: String, is2: Boolean): AmpState {
        var l: Int? = null; var r: Int? = null
        for (p in line.split(Regex("\\s+")).drop(1)) {
            val kv = p.split("=")
            if (kv.size != 2) continue
            val v = kv[1].toIntOrNull() ?: continue
            if (kv[0] == "l") l = v
            if (kv[0] == "r") r = v
        }
        return if (is2) s.copy(lvl2L = l ?: s.lvl2L, lvl2R = r ?: s.lvl2R)
        else s.copy(lvlL = l ?: s.lvlL, lvlR = r ?: s.lvlR)
    }

    private fun parseWsub(s: AmpState, line: String): AmpState {
        var peer = s.wsubPeer; var link = s.wsubLink; var fill = s.wsubFillMs
        var und = s.wsubUnd; var gap = s.wsubGap; var rate = s.wsubRate
        var hiz = s.wsubHiz; var scan = s.wsubScan; var found = s.wsubFound
        var fb = s.wsubFb; var peers = s.wsubPeers; var links = s.wsubLinks
        var rxa = s.wsubRxa; var rxc = s.wsubRxc; var rej = s.wsubRej; var stx = s.wsubStx
        var lat = s.wsubLat; var trim = s.wsubTrim; var max = s.wsubMax

        for (p in line.split(Regex("\\s+")).drop(1)) {
            val kv = p.split("=")
            if (kv.size != 2) continue
            val k = kv[0]; val v = kv[1]
            when (k) {
                "peer" -> peer = v
                "link" -> link = v.toIntOrNull() ?: 0
                "fill" -> fill = v.toIntOrNull() ?: 0
                "und" -> und = v.toIntOrNull() ?: 0
                "gap" -> gap = v.toIntOrNull() ?: 0
                "rate" -> rate = v.toIntOrNull() ?: 0
                "hiz" -> hiz = v.toIntOrNull() ?: 0
                "scan" -> scan = v.toIntOrNull() ?: 0
                "found" -> found = v
                "fb" -> fb = v.toIntOrNull() ?: 0
                "peers" -> peers = if (v == "none" || v.isEmpty()) emptyList() else v.split(",")
                "links" -> links = v.split(",").map { it.toIntOrNull() ?: 0 }
                "rxa" -> rxa = v.toIntOrNull() ?: -1
                "rxc" -> rxc = v.toIntOrNull() ?: -1
                "rej" -> rej = v.toIntOrNull() ?: 0
                "stx" -> stx = v.toIntOrNull() ?: 0
                "lat" -> lat = v.toIntOrNull() ?: 70
                "trim" -> trim = v.toIntOrNull() ?: 0
                "max" -> max = v.toIntOrNull() ?: 4
            }
        }

        // Build peer details from peers + links
        val details = peers.mapIndexed { i, mac ->
            PeerDetail(
                mac = mac,
                link = links.getOrNull(i) == 1,
                fillMs = if (i == 0) fill else 0,
                und = if (i == 0) und else 0,
                gap = if (i == 0) gap else 0,
                rxa = if (i == 0) rxa else -1,
                rxc = if (i == 0) rxc else -1,
            )
        }.ifEmpty {
            if (peer != "none" && peer.isNotEmpty()) {
                listOf(PeerDetail(mac = peer, link = link == 1, fillMs = fill, und = und, gap = gap, rxa = rxa, rxc = rxc, hiz = hiz == 1))
            } else emptyList()
        }

        // History: only append when fill/und/gap actually changed (reduce allocations + recompositions)
        val last = s.fillHistory.lastOrNull()
        val changed = last == null || last.fillMs != fill || last.und != und || last.gap != gap || last.rxa != rxa
        val hist: List<FillSample>
        val undH: List<Int>
        val gapH: List<Int>
        if (changed) {
            val sample = FillSample(fillMs = fill, und = und, gap = gap, rxa = rxa)
            val maxH = AmpState.HISTORY_MAX
            hist = if (s.fillHistory.size >= maxH) s.fillHistory.drop(1) + sample else s.fillHistory + sample
            undH = if (s.undHistory.size >= maxH) s.undHistory.drop(1) + und else s.undHistory + und
            gapH = if (s.gapHistory.size >= maxH) s.gapHistory.drop(1) + gap else s.gapHistory + gap
        } else {
            hist = s.fillHistory
            undH = s.undHistory
            gapH = s.gapHistory
        }
        return s.copy(
            wsubPeer = peer, wsubLink = link, wsubFillMs = fill, wsubUnd = und, wsubGap = gap,
            wsubRate = rate, wsubHiz = hiz, wsubScan = scan, wsubFound = found, wsubFb = fb,
            wsubPeers = peers, wsubLinks = links, wsubRxa = rxa, wsubRxc = rxc,
            wsubRej = rej, wsubStx = stx, wsubLat = lat, wsubTrim = trim, wsubMax = max,
            peerDetails = details,
            fillHistory = hist,
            undHistory = undH,
            gapHistory = gapH
        )
    }
}
