package com.tas5805m.ctrl.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.PeerDetail
import com.tas5805m.ctrl.data.FillSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

class SppClient(private val context: Context) {
    companion object {
        private const val TAG = "SppClient"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val adapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var socket: BluetoothSocket? = null
    private var readerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _state = MutableStateFlow(AmpState())
    val state: StateFlow<AmpState> = _state.asStateFlow()

    private val _bonded = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val bonded: StateFlow<List<BluetoothDevice>> = _bonded.asStateFlow()

    @SuppressLint("MissingPermission")
    fun refreshBonded() {
        try {
            _bonded.value = adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "refreshBonded", e)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        return@withContext try {
            val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter?.cancelDiscovery()
            sock.connect()
            socket = sock
            _state.update {
                it.copy(
                    connected = true,
                    deviceName = device.name ?: "Unknown",
                    deviceAddress = device.address,
                    lastError = ""
                )
            }
            startReader(sock)
            // initial sync
            send("caps")
            Thread.sleep(120)
            send("dump")
            Thread.sleep(180)
            send("status")
            true
        } catch (e: Exception) {
            Log.e(TAG, "connect failed", e)
            _state.update { it.copy(connected = false, lastError = "Connect gagal: ${e.message}") }
            false
        }
    }

    fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        _state.update { it.copy(connected = false) }
    }

    private fun startReader(sock: BluetoothSocket) {
        readerJob = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(sock.inputStream, Charsets.UTF_8))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        parseLine(trimmed)
                    }
                }
            } catch (e: Exception) {
                if (isActive) Log.e(TAG, "reader", e)
            } finally {
                _state.update { it.copy(connected = false) }
            }
        }
    }

    fun send(cmd: String) {
        scope.launch {
            try {
                val out = OutputStreamWriter(socket?.outputStream ?: return@launch, Charsets.UTF_8)
                out.write(cmd.trim() + "\n")
                out.flush()
                Log.d(TAG, ">> $cmd")
            } catch (e: Exception) {
                Log.e(TAG, "send", e)
                _state.update { it.copy(lastError = "Kirim gagal") }
            }
        }
    }

    // Convenience
    fun setMode(m: Int) = send("mode $m")
    fun setMaster(v: Int) = send("master $v")
    fun setSat(v: Int) = send("satvol $v")
    fun setSub(v: Int) = send("subvol $v")
    fun setCtr(v: Int) = send("ctr $v")
    fun setXover(hz: Int) = send("xover $hz")
    fun setGain(g: Int) = send("gain $g")
    fun setAmp(s: String) = send("ampstate $s")
    fun setEq(on: Boolean) = send(if (on) "eq on" else "eq off")
    fun setEqBand(ch: String, band: Int, gain: Float) =
        send("eqband $ch $band ${"%.1f".format(gain)}")
    fun setMixer(ch: String, g: Float) = send("mixer $ch ${"%.2f".format(g)}")
    fun requestStatus() = send("status")
    fun requestDump() = send("dump")
    fun requestEqDump() = send("eqdump")
    fun requestLvl() = send("lvl")
    fun requestLvl2() = send("lvl2")
    fun save() = send("save")
    fun wsubScan() = send("wsub_scan")
    fun wsubPair(mac: String) = send("wsub_pair $mac")
    fun wsubUnpair(mac: String? = null) =
        send(if (mac == null) "wsub_unpair" else "wsub_unpair $mac")
    fun wsubUnpairAll() = send("wsub_unpair_all")
    fun wsubTrim(ms: Int) = send("wsub_trim $ms")
    fun wsubDiagReset() = send("wsub_diag_reset")

    suspend fun applyPreset(p: com.tas5805m.ctrl.data.Preset) {
        setMode(p.mode)
        kotlinx.coroutines.delay(140)
        setXover(p.xover)
        setMaster(p.master)
        setSat(p.sat)
        setSub(p.sub)
        setCtr(p.ctr)
        setGain(p.gain)
        setEq(p.eqOn)
        setMixer("ll", p.ll); setMixer("lr", p.lr)
        setMixer("rl", p.rl); setMixer("rr", p.rr)
        for (i in 0 until 15) {
            if ((p.mode == 1 || p.mode == 2 || p.mode == 3) && i <= 2) continue
            setEqBand("b", i, p.eqLeft.getOrElse(i) { 0f })
            kotlinx.coroutines.delay(28)
        }
        kotlinx.coroutines.delay(100)
        requestStatus()
        requestEqDump()
    }

    private fun parseLine(line: String) {
        _state.update { s ->
            val lines = (listOf(line) + s.lastLines).take(80)
            var ns = s.copy(lastLines = lines)

            when {
                line.startsWith("MODE ") -> ns = parseMode(ns, line)
                line.startsWith("STATE ") -> ns = parseState(ns, line)
                line.startsWith("FAULT ") -> ns = parseFault(ns, line)
                line.startsWith("EQ ") -> ns = parseEq(ns, line)
                line.startsWith("EQM ") -> ns = ns.copy(eqm = line.split(" ").lastOrNull()?.toIntOrNull() ?: ns.eqm)
                line.startsWith("EQP ") -> ns = parseEqp(ns, line)
                line.startsWith("LVL ") -> ns = parseLvl(ns, line, false)
                line.startsWith("LVL2 ") -> ns = parseLvl(ns, line, true)
                line.startsWith("CAPS ") -> ns = ns.copy(caps = line.removePrefix("CAPS ").split(Regex("\\s+")).toSet())
                line.startsWith("WSUB ") -> ns = parseWsub(ns, line)
                line.startsWith("ERR ") -> ns = ns.copy(lastError = line)
                line.startsWith("OK ") -> if (ns.lastError.isNotEmpty()) ns = ns.copy(lastError = "")
            }
            ns
        }
    }

    private fun parseMode(s: AmpState, line: String): AmpState {
        val parts = line.split(Regex("\\s+"))
        var mode = s.mode
        var xover = s.xover; var master = s.master; var sat = s.sat; var sub = s.sub; var ctr = s.ctr
        if (parts.size >= 2) mode = parts[1].toIntOrNull() ?: mode
        for (p in parts.drop(2)) {
            val kv = p.split("=")
            if (kv.size != 2) continue
            val v = kv[1].toIntOrNull() ?: continue
            when (kv[0]) {
                "xover" -> xover = v
                "master" -> master = v
                "sat" -> sat = v
                "sub" -> sub = v
                "ctr" -> ctr = v
            }
        }
        return s.copy(mode = mode, xover = xover, master = master, sat = sat, sub = sub, ctr = ctr)
    }

    private fun parseState(s: AmpState, line: String): AmpState {
        var gain = s.gain; var amp = s.ampstate; var eq = s.eqOn
        var ll = s.ll; var lr = s.lr; var rl = s.rl; var rr = s.rr
        for (p in line.split(Regex("\\s+")).drop(1)) {
            val kv = p.split("=")
            if (kv.size != 2) continue
            when (kv[0]) {
                "gain" -> gain = kv[1].toIntOrNull() ?: gain
                "amp" -> amp = kv[1]
                "eq" -> eq = kv[1] == "1" || kv[1] == "on"
                "ll" -> ll = kv[1].toFloatOrNull() ?: ll
                "lr" -> lr = kv[1].toFloatOrNull() ?: lr
                "rl" -> rl = kv[1].toFloatOrNull() ?: rl
                "rr" -> rr = kv[1].toFloatOrNull() ?: rr
            }
        }
        return s.copy(gain = gain, ampstate = amp, eqOn = eq, ll = ll, lr = lr, rl = rl, rr = rr)
    }

    private fun parseFault(s: AmpState, line: String): AmpState {
        val map = mutableMapOf<String, Int>()
        for (p in line.split(Regex("\\s+")).drop(1)) {
            val kv = p.split("=")
            if (kv.size == 2) {
                map[kv[0]] = if (kv[1].startsWith("0x")) kv[1].removePrefix("0x").toIntOrNull(16) ?: 0
                else kv[1].toIntOrNull() ?: 0
            }
        }
        return s.copy(faultRaw = line, faults = map)
    }

    private fun parseEq(s: AmpState, line: String): AmpState {
        val isLeft = line.startsWith("EQ l=")
        val data = line.substring(5)
        val vals = data.split(",").map { it.trim().toFloatOrNull() ?: 0f }
        return if (vals.size >= 15) {
            if (isLeft) s.copy(eqLeft = vals.take(15)) else s.copy(eqRight = vals.take(15))
        } else s
    }

    private fun parseEqp(s: AmpState, line: String): AmpState {
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
