package com.tas5805m.ctrl.model // Sesuaikan dengan package aplikasi Anda

/**
 * Representasi lengkap status firmware TAS5805M-CTRL (v1 hingga v3.6 + Wireless Sub 2.9.17).
 */
data class AmpState(
    // --- Status Koneksi & Capabilities ---
    val connected: Boolean = false,
    val caps: Set<String> = emptySet(),
    val fwVersion: String = "",
    val fwRole: String = "",
    val freeHeap: Long = 0L,
    val freePsram: Long = 0L,
    val savedInNvs: Boolean = false,
    val lastError: String? = null,
    val lastOkCmd: String? = null,

    // --- MODE & Volume ---
    // 0 = Stereo, 1 = 2-Chip 2.1, 2 = 1-Chip 2.1, 3 = 2-Chip 3.1, 4 = Wireless Sub 2.1
    val audioMode: Int = 0,
    val xoverHz: Int = 150,
    val masterVol: Int = 90,
    val satVol: Int = 90,
    val ctrVol: Int = 85,
    val subVol: Int = 90,

    // --- STATE (Gain, Power State, EQ Toggle, Mixer Matrix) ---
    val gainCode: Int = 18,          // 0..31 (0 = 0 dB, 31 = -15.5 dB)
    val ampState: String = "play",   // play | mute | hiz | sleep | deepsleep
    val eqOn: Boolean = true,
    val mixLL: Float = 1.0f,
    val mixLR: Float = 0.0f,
    val mixRL: Float = 0.0f,
    val mixRR: Float = 1.0f,

    // --- FX & Auto-Standby ---
    val subInv: Boolean = false,
    val standbyMin: Int = 0,         // 0..240 menit (0 = off)
    val inStandby: Boolean = false,

    // --- EQ & Crossover Profiles (15 Band: 20Hz .. 16kHz) ---
    val eqLeft: List<Int> = List(15) { 0 },
    val eqRight: List<Int> = List(15) { 0 },
    val eqCenter: List<Int> = List(15) { 0 }, // Mode 3 (Chip 2 L)
    val eqSub: List<Int> = List(15) { 0 },    // Mode 3 (Chip 2 R)
    val eqModeReg: Int = 6,                   // 6=ON, 7=OFF, 14=BIAMP, 15=BIAMP_OFF
    val eqp1Left: Int = 0,
    val eqp1Right: Int = 0,
    val eqp2Left: Int = 0,
    val eqp2Right: Int = 0,

    // --- Hardware Config & Telemetry (v3) ---
    val dacMode1: String = "btl",             // btl | pbtl
    val dacMode2: String = "btl",             // Chip 2 (2-chip env)
    val modMode: String = "bd",               // bd | 1spw | hybrid
    val modFreq: String = "768k",             // 384k | 480k | 576k | 768k
    val modBdFreq: String = "175k",           // 80k | 100k | 120k | 175k
    val clipGain: Int = 0,                    // deci-dB (-300..0)
    val clipMarginL: Int = 0,                 // deci-dB (0..300)
    val clipMarginR: Int = 0,                 // deci-dB (0..300)

    // --- Chip Info ---
    val infoFs: String = "48KHz",
    val infoBck: Int = 64,
    val infoPwr: String = "PLAY",
    val infoMuteR: Boolean = false,
    val infoMuteL: Boolean = false,
    val infoSampleRate: Int = 0,

    // --- Level Meters (deci-dB: -1200..0) ---
    val lvl1L: Int = -1200,
    val lvl1R: Int = -1200,
    val lvl2L: Int = -1200,
    val lvl2R: Int = -1200,

    // --- Faults (Chip 1 & Chip 2) ---
    val fault1: ChipFault = ChipFault(),
    val fault2: ChipFault = ChipFault(),
    val fault2Available: Boolean = false,

    // --- Wireless Sub (Mode 4 / ESP-NOW Master) ---
    val wsub: WirelessSubState = WirelessSubState(),

    // --- Diagnosa Xover Timing (XOVT) ---
    val lastXoverTiming: XoverTiming? = null
) {
    val analogGainDb: Float get() = -gainCode * 0.5f

    // Helper deteksi capabilities dari token `CAPS`
    fun hasCap(token: String): Boolean = caps.contains(token)
    val supportsV2: Boolean get() = hasCap("v2")
    val supportsV3: Boolean get() = hasCap("v3")
    val supportsTwoChip: Boolean get() = hasCap("lvl2") || hasCap("ctr")
    val supportsWsub: Boolean get() = hasCap("wsub")
    val supportsWsubLevel: Boolean get() = hasCap("wlvl")
    val supportsSubInv: Boolean get() = hasCap("subinv")
    val supportsStandby: Boolean get() = hasCap("standby")
    val supportsEqCenterSub: Boolean get() = hasCap("eqc")
}

data class ChipFault(
    val err0: Int = 0,
    val err1: Int = 0,
    val err2: Int = 0,
    val ot: Int = 0,
    val ocR: Boolean = false,
    val ocL: Boolean = false,
    val dcR: Boolean = false,
    val dcL: Boolean = false,
    val uv: Boolean = false,
    val ov: Boolean = false,
    val clk: Boolean = false,
    val bq: Boolean = false,
    val crc: Boolean = false,
    val otSd: Boolean = false,
    val otWarn: Boolean = false,
    val readError: Boolean = false
) {
    val hasAnyFault: Boolean
        get() = ocR || ocL || dcR || dcL || uv || ov || clk || bq || crc || otSd || otWarn || readError
}

data class WirelessSubState(
    val peers: List<String> = emptyList(),
    val primaryPeer: String = "none",
    val linked: Boolean = false,
    val fallbackActive: Boolean = false,      // fb=1 (sub hilang >= 6 dtk, satelit fallback ke FLAT)
    val scanning: Boolean = false,
    val foundPeers: List<String> = emptyList(),
    val maxPeers: Int = 4,
    val latencyMs: Int = 70,
    val trimMs: Int = 0,                      // -60..+100 ms
    val wifiChannel: Int = 1,
    val masterFwPatch: Int = 0,
    val lastErrName: String = "OK",
    val txFail: Long = 0,
    val ackOk: Long = 0,
    val ackFail: Long = 0,
    val noMem: Long = 0,
    val audioDrop: Long = 0,
    val masterHeap: Long = 0,
    val masterHeapMin: Long = 0,
    val masterHeapBlk: Long = 0,
    val tapAvg: Int = 0,
    val tapMax: Int = 0,
    val tapGapMax: Int = 0,
    val tapGapBig: Int = 0,
    val sndGapMax: Int = 0,
    val sndGapBig: Int = 0,
    val waitMax: Int = 0,
    val wait10: Int = 0,
    val qMax: Int = 0,
    val slots: Map<Int, WirelessSubSlot> = emptyMap(),
    val levels: Map<Int, Int?> = emptyMap(),  // slot -> deci-dB (-1200..0), null jika "na"
    val voice: WirelessSubVoice = WirelessSubVoice(),
    val txStats: WirelessSubTxStats? = null,
    val cpuStats: WirelessSubCpuStats? = null,
    val btDiag: WirelessSubBtDiag? = null,
    val recentBtEvents: List<String> = emptyList()
)

data class WirelessSubSlot(
    val slot: Int,
    val mac: String,
    val linked: Boolean = false,
    val fillMs: Int = 0,
    val underrun: Long = 0,
    val gap: Long = 0,
    val sampleRate: Int = 0,
    val hiz: Boolean = false,
    val rxAudioPerSec: Int = -1,
    val rxCtrlPerSec: Int = -1,
    val rejectedPkts: Long = 0,
    val statusTxFail: Long = 0,
    val txAckOk: Long = 0,
    val txAckFail: Long = 0,
    val txDrop: Long = 0,
    val txInflight: Int = 0,
    val faultMask: Int = -1,                  // -1 = slave lama/offline; 11-bit mask jika valid
    val faultCount: Long = 0,
    val uptimeSec: Int = -1,
    val resetReason: Int = -1,
    val rssi: Int = 0,
    val diag: WirelessSubSlotDiag? = null
)

data class WirelessSubSlotDiag(
    val g40: Int = 0,
    val g60: Int = 0,
    val g80: Int = 0,
    val g100: Int = 0,
    val g200: Int = 0,
    val g500: Int = 0,
    val gMax: Int = 0,
    val adapt: Int = 0,
    val fMin: Int = 0,
    val jMax: Int = 0,
    val pkt: Int = 0,
    val estRate: String = "",
    val needRate: String = "",
    val slaveFw: String = "?",
    val fillMin: String = "-1",
    val ageMs: Int = 0
)

data class WirelessSubVoice(
    val enabled: Boolean = false,
    val hpfHz: Int = 32,
    val peakHz: Int = 55,
    val peakDb10: Int = 0,                    // dalam 0.1 dB (mis. 30 = +3.0 dB)
    val peakQ10: Int = 10,                    // dalam 0.1 Q (mis. 14 = Q 1.4)
    val harmonics: Int = 0,
    val limiter: Int = 100
)

data class WirelessSubTxStats(
    val linkedCount: Int = 0,
    val inflightLimit: Int = 0,
    val noMem: Long = 0,
    val noMemRecovered: Long = 0,
    val dropTotal: Long = 0,
    val perSlotDrop: List<Long> = emptyList(),
    val latBaseMs: Int = 0,
    val latCmdMs: Int = 0,
    val aligned: Boolean = false,
    val extraMaxMs: Int = 0
)

data class WirelessSubCpuStats(
    val cpu0: Int = -1,
    val cpu1: Int = -1,
    val peak0: Int = -1,
    val peak1: Int = -1
)

data class WirelessSubBtDiag(
    val g40: Int = 0,
    val g60: Int = 0,
    val g80: Int = 0,
    val g100: Int = 0,
    val g200: Int = 0,
    val g500: Int = 0,
    val connState: Int = 0,
    val audioState: Int = 0,
    val sampleRate: Int = 0,
    val rssi: Int = 0,
    val rssiAgeMs: Int = -1,
    val eventDrops: Long = 0,
    val hooksMask: Int = 0
)

data class XoverTiming(
    val hz: Int,
    val applyMs: Long,
    val statusMs: Long,
    val stackFree: Long,
    val heapFree: Long,
    val heapMin: Long
)
