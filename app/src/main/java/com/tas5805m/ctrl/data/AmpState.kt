package com.tas5805m.ctrl.data

data class AmpState(
    val connected: Boolean = false,
    val deviceName: String = "",
    val deviceAddress: String = "",

    // Core
    val mode: Int = 0,
    val xover: Int = 100,
    val master: Int = 80,
    val sat: Int = 100,
    val sub: Int = 100,
    val ctr: Int = 100,
    val gain: Int = 18,
    val ampstate: String = "play",
    val eqOn: Boolean = true,

    // Mixer
    val ll: Float = 1f, val lr: Float = 0f,
    val rl: Float = 0f, val rr: Float = 1f,

    // EQ 15 bands
    val eqLeft: List<Float> = List(15) { 0f },
    val eqRight: List<Float> = List(15) { 0f },
    val eqm: Int = 6,
    val eqpLeft: Int = 0,
    val eqpRight: Int = 0,

    // Level meters (deci-dB)
    val lvlL: Int = -1200,
    val lvlR: Int = -1200,
    val lvl2L: Int = -1200,
    val lvl2R: Int = -1200,

    // Fault
    val faultRaw: String = "",
    val faults: Map<String, Int> = emptyMap(),

    // CAPS
    val caps: Set<String> = emptySet(),

    // Wireless Sub (legacy single + multi)
    val wsubPeer: String = "none",
    val wsubLink: Int = 0,
    val wsubFillMs: Int = 0,
    val wsubUnd: Int = 0,
    val wsubGap: Int = 0,
    val wsubRate: Int = 0,
    val wsubHiz: Int = 0,
    val wsubScan: Int = 0,
    val wsubFound: String = "none",
    val wsubFb: Int = 0,
    val wsubPeers: List<String> = emptyList(),
    val wsubLinks: List<Int> = emptyList(),
    val wsubRxa: Int = -1,
    val wsubRxc: Int = -1,
    val wsubRej: Int = 0,
    val wsubStx: Int = 0,
    val wsubLat: Int = 70,
    val wsubTrim: Int = 0,
    val wsubMax: Int = 4,

    // Per-peer detailed status (multi-sub)
    val peerDetails: List<PeerDetail> = emptyList(),

    // Rolling history for real-time charts (last ~60 samples)
    val fillHistory: List<FillSample> = emptyList(),
    val undHistory: List<Int> = emptyList(),
    val gapHistory: List<Int> = emptyList(),

    val lastLines: List<String> = emptyList(),
    val lastError: String = "",
) {
    val hasEqband get() = "eqband" in caps
    val hasLvl get() = "lvl" in caps
    val hasLvl2 get() = "lvl2" in caps
    val hasV3 get() = "v3" in caps
    val hasWsub get() = "wsub" in caps
    val hasCtr get() = "ctr" in caps
    val hasSave get() = "save" in caps
    val is21 get() = mode == 1 || mode == 2
    val is31 get() = mode == 3
    val isWireless get() = mode == 4

    fun modeName() = when (mode) {
        0 -> "Stereo"
        1 -> "2.1 Dual-chip"
        2 -> "2.1 Single-chip"
        3 -> "3.1"
        4 -> "Wireless Sub"
        else -> "Mode $mode"
    }

    companion object {
        const val HISTORY_MAX = 48
        val BAND_FREQS = listOf(20, 32, 50, 80, 125, 200, 315, 500, 800, 1250, 2000, 3150, 5000, 8000, 16000)
    }
}

data class FillSample(
    val t: Long = System.currentTimeMillis(),
    val fillMs: Int = 0,
    val und: Int = 0,
    val gap: Int = 0,
    val rxa: Int = -1,
)

data class PeerDetail(
    val mac: String,
    val link: Boolean = false,
    val fillMs: Int = 0,
    val und: Int = 0,
    val gap: Int = 0,
    val rxa: Int = -1,
    val rxc: Int = -1,
    val rssi: Int = 0,
    val hiz: Boolean = false,
)

data class Preset(
    val name: String,
    val mode: Int = 0,
    val xover: Int = 100,
    val master: Int = 80,
    val sat: Int = 100,
    val sub: Int = 100,
    val ctr: Int = 100,
    val gain: Int = 18,
    val eqOn: Boolean = true,
    val ll: Float = 1f, val lr: Float = 0f,
    val rl: Float = 0f, val rr: Float = 1f,
    val eqLeft: List<Float> = List(15) { 0f },
    val eqRight: List<Float> = List(15) { 0f },
    val created: String = "",
)
