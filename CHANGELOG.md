# Changelog

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

