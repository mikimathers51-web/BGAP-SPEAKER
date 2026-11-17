# TAS5805M CTRL — Pure Kotlin + Jetpack Compose

Aplikasi kontroler **Bluetooth Classic SPP** untuk firmware ESP32 + TAS5805M (termasuk multi-sub wireless).

## Fitur

UI Home + EQ terinspirasi pola AXTON/DS18 (status Connect, master gauge, kurva EQ frekuensi).


- **Multi-sub pairing UI lengkap** + grafik buffer real-time (fill sparkline, underrun/gap history, gauge per-peer)
  - Scan slave, daftar peer (max 4), status per-peer (link, fill, underrun, gap, RX rate)
  - Pair / Unpair individual / Unpair All
  - Trim latency (-40 … +60 ms)
  - Fallback stereo indicator
  - Radio counters (rxa/rxc/rej/stx)
- **DSP lengkap** — 15-band EQ, link L/R, quick shapes
- **Preset** — simpan/terapkan mode+volume+EQ (DataStore)
- **Diagnosa** — fault, level meter, CAPS, log SPP
- **Custom adaptive icon** (speaker + EQ bars)

## Build via GitHub Actions

1. Push repo ke GitHub
2. Tab **Actions** → **Build APK** (otomatis atau *Run workflow*)
3. Unduh artifact `Tas5805mCtrl-APK`

## Lokal

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

## Tab aplikasi

| Tab | Fungsi |
|-----|--------|
| Koneksi | Pair & connect SPP |
| Kontrol | Mode, volume, xover, gain, amp state, level meter |
| EQ / DSP | 15-band + quick shapes |
| **Wireless** | Multi-sub pairing & diagnosa per-peer |
| Diagnosa | Status lengkap + log |

minSdk 26 · targetSdk 35 · Kotlin 2.0 · Compose BOM 2024.10
