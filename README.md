# TAS5805M CTRL — Pure Kotlin + Jetpack Compose

Aplikasi kontroler **Bluetooth Classic SPP** untuk firmware ESP32 + TAS5805M (termasuk multi-sub wireless).

## Fitur

UI Home + EQ terinspirasi pola AXTON/DS18 (status Connect, master gauge, kurva EQ frekuensi).


- **Multi-sub pairing UI lengkap** + grafik buffer real-time (fill sparkline, underrun/gap history, gauge per-peer)
  - Scan slave, daftar peer (max 4), status per-peer (link, fill, underrun, gap, RX rate)
  - Pair / Unpair individual / Unpair All
  - Trim latency (-60 … +100 ms, slider + tombol ±1/±5)
  - Fallback stereo indicator
  - Radio counters (rxa/rxc/rej/stx)
- **Voicing sub wireless** — HPF subsonic (biquad) + peaking (band EQ upstream 80/125 Hz, gain 1 dB) di DSP chip, auto-setup dari fb driver (`wsub_voice`). Sejak app 3.10.0 / firmware 2.9.19; limiter dan harmonik dihapus
- **Polaritas sub** + **auto-standby** (`subinv`, `standby`), level meter sub per slave (`wlvl`), fault slave (`WSUBS flt=`)
- **DSP lengkap** — 15-band EQ (1 dB, integer seperti firmware), link L/R, quick shapes, EQ center/sub di mode 3 (`eqc`)
- **Preset** — simpan/terapkan mode+volume+EQ (DataStore)
- **Diagnosa** — fault, level meter, CAPS, log SPP
- **Custom adaptive icon** (speaker + EQ bars)

## Build via GitHub Actions

1. Push repo ke GitHub
2. Tab **Actions** → **Build APK** (otomatis atau *Run workflow*)
3. Unduh artifact `Tas5805mCtrl-APK`

## Lokal

```bash
gradle assembleRelease      # butuh Gradle 8.9 + JDK 17 + Android SDK 35 (atau buka di Android Studio)
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


## Perubahan 3.11.0

- **Diagnosa** ditulis ulang: ringkasan masalah otomatis (aturan dari tabel README firmware, laju paket dihitung dari `pkt=` slave),
  fault chip 1/2 + riwayat sesi, info chip (`INFO`, `VER`, `CLIP`, `MOD`, `DAC`), statistik link nirkabel & Bluetooth
  (`WSUB`, `WSUBT`, `WSUBD`, `WSUBC`, `BTD`, `BTEV`), "Reset ukur" (delta counter), terminal, dan **Bagikan laporan**.
- **Tab DSP** baru: mixer 2×2 + mixmode + lebar stereo, profil filter (mode 0), clipper, modulasi PWM, DAC BTL/PBTL (dengan konfirmasi),
  gain analog, auto-standby, polaritas sub, simpan/hapus/reboot.
- **Preset** (sub-tab di DSP): preset bawaan (EQ + voicing sub), preset lengkap kini juga menyimpan clipper, polaritas sub, standby,
  trim, voicing sub, dan EQ center/sub; ekspor/impor JSON lewat clipboard.
- Home: level meter hidup (poll `lvl`/`lvl2` saat tab tampil). Log tidak lagi dibanjiri baris poll.
- Perbaikan: daftar perangkat ter-pair kosong saat pertama dijalankan; `cancelDiscovery()` tidak lagi menggagalkan koneksi bila izin SCAN ditolak;
  reader sesi lama tidak menandai sesi baru sebagai putus; peringatan voicing (slave < 2.9.19, gain peak > +4 dB tanpa limiter).
- Build: AGP 8.7.3, `gradlew` palsu dihapus (CI memakai `gradle` 8.9), tes unit di CI, rilis otomatis untuk tag `v*`.
