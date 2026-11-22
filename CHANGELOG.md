# Changelog


## 3.15.0
- **2-way aktif mono** (firmware ≥ 2.9.23, CAPS `tway`, hanya mode 2): kartu *2-way aktif (mono)* di Home — sakelar `tway`, slider crossover (`tway_hz`, langkah 50 Hz, dibatasi `min`/`max` dari firmware, bawaan 1000–5000 Hz). Kanal L = tweeter (HPF), kanal R = woofer (LPF), Linkwitz-Riley orde-4. Saat aktif, kartu Crossover tabel disembunyikan dan Channel Mix berlabel *Tweeter (L)* / *Woofer (R)*.
- `AmpState`: `tway`, `twayHz`, `twayMin`, `twayMax`, `hasTway`, `twayActive`; baris `TWAY …` diparse (tidak masuk log).
- Uji unit: `XoverSplitTest` ditambah kasus TWAY.

## 3.14.0
- **Crossover terpisah** (firmware ≥ 2.9.22, CAPS `xover2`): kartu Crossover di Home punya slider *HPF SAT* dan *LPF SUB* sendiri (60–150 Hz, snap 10 Hz) dengan sakelar **Kunci bersama** (aktif = satu perintah `xover` untuk keduanya, seperti sebelumnya). Bila tidak dikunci dan HPF ≠ LPF, tampil peringatan overlap/celah. Firmware lama tetap memakai satu slider.
- Perintah baru `xover_hp` / `xover_lp`; baris `MODE` memuat `xhp=`/`xlp=` (`xover=` tetap = HPF). `AmpState.xoverLp` (firmware lama: sama dengan `xover`).
- **Preset** menyimpan `xoverLp` (opsional; preset lama = LPF sama dengan HPF) dan menerapkannya lewat `xover_hp` + `xover_lp` bila berbeda.
- Uji unit baru: `XoverSplitTest`.

## 3.13.0
- **Wireless:** kartu *Penyelarasan latensi* (firmware ≥ 2.9.21, CAPS `wsub_tune`): slider 0–200 ms (`wsub_extra`) untuk membatasi seberapa jauh sub boleh menunda diri; menampilkan latensi dasar dan yang diperintahkan ke sub (`latb`/`latc` dari `WSUBT`).
- **Diagnosa:** kartu *Status ringkas* di paling atas (Amp, Fault, Mode, Firmware, SPP, peer wireless) — hanya data yang benar-benar dilaporkan firmware (tanpa suhu/PVDD/uptime). Judul kartu Diagnosa dan OTA diseragamkan dengan `CardTitle`.
- **EQ:** kurva respons diberi isian gradien di bawah garis (gaya preview).
- Semua layar (Koneksi, Home, EQ, DSP/Preset, Wireless, Monitor, Diagnosa + kartu OTA) memakai `GlassCard`; layar **Koneksi** ditulis ulang (kartu perangkat terhubung, daftar ter-pair dengan tombol Sambung, switch Auto-reconnect/Warna dinamis).
- `hasVoice` kini juga mengenali token `voice_basic` dari `CAPS` firmware (tidak lagi bergantung pada kedatangan baris `WSUBV`).
- Pesan galat OTA untuk `ota_nvs` dan `ota_args`.
- Layar **Wireless** dan kurva **EQ** memakai `GlassCard` yang sama dengan Home; warna status (merah/kuning/hijau) di Wireless dan EQ kini dari tema (`BadRed`/`WarnAmber`/`OkGreen`). Perilaku tidak berubah.
- **Home** ditulis ulang bergaya Glass (komponen baru `GlassCard`/`CardTitle`/`GlassBadge`/`SwitchRow`): master angka besar, Channel Mix (sub dengan tombol fase 0°/180°, sat, ctr), crossover satu slider LPF sub ⇄ HPF sat (snap 10 Hz), gain analog, Mode, switch **Mute** + amp state + auto-standby, level meter. Hanya kontrol yang ada di firmware 2.9.20; Loudness/Night/sumber input/PVDD/suhu sengaja tidak ditampilkan.
- Tampilan baru "Glass" (panel hampir hitam, kartu kaca, cyan #7DD3FC + amber #FBBF24), tema Sistem/Gelap/Terang + warna dinamis, top bar logo + status mono berdenyut, snackbar galat.
- Sambung otomatis ke perangkat terakhir saat app dibuka; sambung ulang 5× (jeda 3 dtk) saat koneksi putus. Antrean poll dibatasi/di-dedupe.
- Tab **Monitor** baru: meter L/R, chip 2, level sub wireless (PEAK/AVG, peak-hold, puncak sesi, Reset Peaks) dan sparkline buffer 30 dtk per sub.
- Polling `lvl`/`status`/`wlvl` kini hanya berjalan saat app terlihat (`PollWhileStarted`), tidak lagi di latar belakang.
- Preset `voice` dipaksa `harm=0 limit=100` (firmware menolak nilai lain).
- 12 preset bawaan baru (EQ, voicing sub, scene).
- Bandwidth loop class-D dibatasi sesuai datasheet per modulasi dan frekuensi switching (`ModRules`).
- Kartu **Update Firmware (OTA)** di tab Diagnosa: kirim `firmware.bin` lewat Bluetooth (butuh firmware dengan OTA + tabel partisi 2 slot).

## 3.12.1

- **Slider fb (Voicing Sub)**: rentang kini 30–150 Hz, sama dengan peaking/firmware (sebelumnya 30–100 Hz sehingga thumb tertahan di 100 saat label menampilkan nilai lebih besar).
- **Voicing**: perintah `wsub_voice` kini memperbarui state UI langsung (optimistis). Sebelumnya, menggeser dua slider berurutan sebelum balasan `WSUBV` tiba bisa mengirim nilai basi dan mengembalikan slider pertama di firmware.
- **SendSlider**: tidak lagi mengirim perintah ganda untuk nilai yang sama (throttle lalu "dilepas", atau beberapa posisi float yang dibulatkan ke Int yang sama) — mengurangi beban link SPP/A2DP.
- Teks UI/dokumentasi disesuaikan: keterangan slider fb (gain +4 dB hanya bila gain sebelumnya ≤ 0), catatan voicing slave ≥ 2.9.20, tabel tab, README ringkas, riwayat dipindah ke berkas ini.

## 3.12.0

- **Preset**
  - Preset punya `id` (UUID); hapus berdasarkan id, bukan indeks (tidak salah hapus saat daftar berubah). Preset lama mendapat id deterministik.
  - Jenis baru **Scene** = EQ + voicing sub tanpa mode/xover/volume (aman di mode 4, tidak melonjakkan volume). Tombol "Simpan sebagai Scene".
  - Preset bawaan baru: EQ (Satelit kecil, Suara/Podcast, Hangat, Anti-desis, Game), voicing (Sub sealed kecil, Sub ported, Ruangan kecil, Sub aman), Scene (Malam, Film, Musik, Pesta).
  - Peringatan per preset: band 20/32/50 Hz dilewati saat crossover aktif (bass lewat voicing sub), perkiraan boost maks + saran turunkan Master, peak sub > +4 dB tanpa limiter.
  - Apply memberi tahu bila firmware menolak perintah (hitung baris `ERR` selama apply) — bukan sekadar "dikirim".
  - Impor: semua nilai di-clamp ke rentang firmware (NaN aman), maks 200 KB / 50 preset per impor / 100 total, duplikat persis dilewati, nama kembar diberi akhiran (2).
- **Voicing sub** (slave ≥ 2.9.20): peaking bebas — frekuensi, **Q**, gain 0,5 dB. Slave 2.9.19 tetap didukung (UI lama otomatis).
- **Build**: rilis dapat ditandatangani kunci sendiri lewat secret `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (tanpa itu: kunci debug + peringatan). Tes unit sekarang **menggagalkan** build; tes baru `PresetToolsTest`.

### 3.12.0 — Diagnosa: simpan laporan

- **Simpan file** — tulis laporan ke `diagnosa-YYYYMMDD-HHmmss.txt` lewat dialog pilih lokasi Android (tanpa izin storage tambahan).
- **Riwayat laporan** — maks 20 terbaru, tersimpan di DataStore (bertahan setelah app ditutup, bisa dibuka walau tidak terhubung). Buka / Bagikan / Simpan file / Hapus per laporan.
- **Reset ukur** otomatis menyimpan laporan "Sebelum reset ukur" ke riwayat, jadi hasil tuning (`wsub_trim`, `WSUB_PKT_TX`, dll.) bisa dibandingkan dengan laporan sesudahnya.

## 3.11.0

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

