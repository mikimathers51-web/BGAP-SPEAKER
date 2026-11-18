package com.tas5805m.ctrl.data

/**
 * Preset bawaan. kind="eq" hanya mengubah EQ (+EQ on) — tidak menyentuh volume/mixer/xover.
 * kind="voice" hanya mengubah voicing sub (+trim bila ada). Band 0..2 (20/32/50 Hz) otomatis dilewati
 * saat crossover aktif (mode 1..4) karena dicadangkan untuk filter crossover.
 * Urutan band: 20, 32, 50, 80, 125, 200, 315, 500, 800, 1250, 2000, 3150, 5000, 8000, 16000 Hz.
 */
object BuiltinPresets {
    private fun eq(name: String, note: String, g: List<Int>) = Preset(
        name = name, kind = "eq", builtin = true, note = note,
        eqLeft = g.map { it.toFloat() }, eqRight = g.map { it.toFloat() },
    )

    private fun voice(name: String, note: String, v: VoiceParams) = Preset(
        name = name, kind = "voice", builtin = true, note = note, voice = v,
    )

    val all: List<Preset> = listOf(
        eq("Flat", "Semua band 0 dB.", List(15) { 0 }),
        eq("Bass Boost", "Naikkan bass lembut (+6 dB di 20 Hz).", listOf(6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        eq("Vokal Jernih", "Kurangi gemuruh, angkat 1–3 kHz.", listOf(-6, -5, -3, -2, -1, 0, 0, 1, 2, 3, 3, 2, 1, 0, -1)),
        eq("Treble Boost", "Angkat 1,2–16 kHz.", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 5)),
        eq("Loudness", "Bass dan treble naik untuk volume rendah.", listOf(6, 5, 3, 1, 0, 0, -1, -1, 0, 0, 1, 2, 3, 4, 3)),
        eq("Film", "Bass dalam + dialog jelas.", listOf(5, 4, 3, 2, 0, 0, 0, 0, 1, 2, 2, 2, 2, 1, 0)),
        eq("V-Shape", "Bass dan treble menonjol, mid turun.", listOf(4, 3, 2, 1, 0, -1, -2, -2, -1, 0, 1, 2, 3, 3, 2)),
        eq("Malam", "Bass dan treble diredam untuk tetangga.", listOf(-3, -3, -2, -1, 0, 0, 0, 0, 0, 0, 0, 0, -1, -2, -3)),
        voice(
            "Sub kecil (3–5\")", "HPF 45 Hz, peaking +3 dB di band 80 Hz. Butuh slave ≥ 2.9.19.",
            VoiceParams(on = true, hpf = 45, pkHz = 60, pkDb10 = 30)
        ),
        voice(
            "Sub besar (8–12\")", "HPF 28 Hz, peaking +2 dB di band 80 Hz. Butuh slave ≥ 2.9.19.",
            VoiceParams(on = true, hpf = 28, pkHz = 45, pkDb10 = 20)
        ),
        voice("Voicing OFF", "Matikan HPF subsonic dan peaking.", VoiceParams(on = false)),
    )
}
