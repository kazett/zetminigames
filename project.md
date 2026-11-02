# ZetMinigames - Project Progress (game: Pillar of Fortune, Deathswap, Mace Roulette)

Plugin Paper (target 1.21.11, Java 21). Build: Maven via GitHub Actions.

## Cara upload (FLAT)
1. Upload SEMUA file di folder ini ke root repo GitHub (drag & drop, tanpa folder).
2. Khusus `build.yml`: setelah ke-upload, klik file-nya, klik ikon pensil (edit), di kolom nama file ganti jadi
   `.github/workflows/build.yml` (ketik pakai slash, GitHub otomatis bikin foldernya), lalu Commit.
   Tanpa langkah ini Actions gak jalan.
3. Buka tab Actions, tunggu hijau, download artifact `ZetMinigames` (isinya jar), taruh di `plugins/` server.

Kenapa bisa flat: `pom.xml` diset `sourceDirectory = root`, jadi semua .java dibaca dari root.

## Alur game
1. `/panel` -> menu GAMES -> Pillar of Fortune -> atur setting + centang pemain yang diajak (total 2-10).
2. Pemain diajak dapat chat dengan tombol [READY] / [TOLAK].
3. Semua ready -> world void dibuat, teleport ke pilar, countdown 5 detik (gak bisa gerak).
4. Tiap X detik/menit semua yang hidup dapat item random (edit di `ItemPool.java`).
5. Border mengecil setelah waktu yang diset, kecepatan pelan/sedang/cepat (300/150/60 detik).
6. Terakhir hidup menang. Inventory & lokasi pemain dibalikin, world dihapus.

Satu-satunya command yang perlu diketik: `/panel`. Ready/Tolak/Batalin = klik tombol di chat (click callback, tanpa command) atau lewat `/panel` lagi (menu lobby / menyerah).
`/pof ready|leave|cancel` masih ada cuma sebagai cadangan.

## Layout pilar
- Layout pilar: lihat bagian v17 (cincin luar + isi dalam, jarak >= 15).
- Border awal = pilar terluar + 10 block (`PofGame.BORDER_MARGIN`), 1 border buat semua.
- Catatan: WorldBorder vanilla selalu KOTAK. Border bulat beneran butuh sistem custom (lihat TODO).

## Progress
- [x] Tahap 1: world void, pilar 2-10 pemain, countdown, item random, border, eliminasi, menang, cleanup
- [x] Tahap 2: `/panel` Dialog, setting flex (detik/menit), invite + ready klik chat
- [x] Tahap 3: HUD custom (kepala pemain, X pas mati, NEXT ITEM + bar kuning, jumlah hidup) + resource pack (BELUM DITES)
- [x] Tahap 3b: HUD didesain ulang mirip referensi (frame kepala, tengkorak pas mati, ikon, bar kuning) + skin tombol/textfield/checkbox buat dialog `/panel` (BELUM DITES)
- [ ] Tahap 4 (opsional): border bulat custom, lebih banyak game

## Belum dites / risiko
- Belum pernah di-compile & dites di server. Kalau build GitHub Actions error, kirim log error-nya.
- API Dialog Paper masih experimental, nama method bisa beda antar versi Paper.
- HUD sekarang cuma actionbar sementara (tahap 3 diganti).
- Versi Paper diatur di `pom.xml` (`paper.version`).

## Tahap 3: HUD + resource pack
Cara kerja: 1 bossbar (warna WHITE) per game. Bar vanilla-nya dibikin transparan lewat pack, lalu semua tampilan
(panel, kepala, X, teks, bar kuning) digambar lewat font custom `pof:hud` di judul bossbar. Kepala pemain pakai
komponen object bawaan Minecraft 1.21.9+. Pemain tanpa pack otomatis dapat actionbar teks biasa.

Setup (sekali):
1. Repo harus **public** (supaya Minecraft bisa download pack).
2. Setelah Actions hijau, buka tab Releases -> "Latest build". Di situ ada `ZetMinigames.jar`, `ZetMinigames-Pack.zip`,
   dan SHA1 pack di deskripsi.
3. Jalanin server sekali biar `plugins/ZetMinigames/config.yml` kebentuk, lalu isi:
   - `resource-pack.url`: `https://github.com/<user>/<repo>/releases/download/latest-build/ZetMinigames-Pack.zip`
   - `resource-pack.sha1`: SHA1 dari deskripsi release
4. Restart server, join (pack otomatis ke-download).
Tiap kali file pack diubah (png/hud.json), SHA1 berubah, update lagi di config.

Tuning kalau tampilan agak geser (tanpa rebuild): `hud.head-advance` (8/9/10) dan `hud.slot-width` di config.yml.

Catatan: warna bossbar WHITE jadi transparan buat semua bossbar putih (juga dari plugin lain).

## Daftar file dan struktur package
Source di repo tetap FLAT (semua .java di root), tapi tiap file punya `package` sendiri, jadi jar hasil build rapi:
- `id.zetminigames` : `ZetMinigames.java` (main class)
- `id.zetminigames.core` : `GameManager`, `GameType`, `GameSettings`, `Match`, `Lobby`, `PlayerSnapshot`, `GameListener`, `Commands`
- `id.zetminigames.hud` : `Hud`, `WinScreen`, `CustomTitle`, `PackListener`
- `id.zetminigames.panel` : `Panels`
- `id.zetminigames.pof` : `PofGame`, `ItemPool`, `ShrinkSpeed`, `VoidGenerator`
- `id.zetminigames.deathswap` : `DeathSwapGame`
Game baru: bikin file flat di root dengan `package id.zetminigames.<nama>;`, implement `Match`, tambah di `GameType` + `GameManager.startGame` + `Panels`.
Bahan resource pack (flat): `config.yml`, `build-pack.sh`, `pack.mcmeta`, `hud.json`, `hud_low.json`, `hud_*.png`, `bar_clear.png`, `ui_*.png`, `gen_assets.py`.
Lain-lain: `pom.xml` (versi plugin diatur di `<version>`, otomatis masuk plugin.yml), `plugin.yml`, `build.yml`, `.gitignore`, `project.md`.
Nama: plugin `ZetMinigames`, jar `ZetMinigames.jar`, pack `ZetMinigames-Pack.zip`, folder config `plugins/ZetMinigames/`. Namespace resource pack sekarang `zetminigames` (font `zetminigames:hud`), command tetap `/panel` dan `/pof`, permission tetap `pof.use`, world sementara tetap `pof_*`.

## Cara tes sendirian
`/panel` -> Pillar of Fortune -> nyalain **MODE TES**, atur jumlah pilar (4-10, cuma buat tes), set interval item 5 detik dan border 20 detik biar cepet, lalu **Buat Lobby** tanpa centang siapa pun.
Game langsung mulai. Di mode tes game gak berhenti pas sisa 1 orang, cuma berhenti kalau lu mati / klik Menyerah di `/panel`.
Yang dicek: pilar kebentuk, countdown, item masuk tiap interval, border mengecil, jatuh ke void = eliminasi, setelah game inventory & lokasi balik normal, world pof_ kehapus.

## Revisi pack (Tahap 3b)
- `pack.mcmeta` pakai `pack_format 75` + `min_format 75` / `max_format 999` (1.21.11 = format 75).
- Semua PNG dibuat ulang lewat `python3 gen_assets.py` (butuh Pillow). Mau ganti warna/desain: edit script itu, jalanin, commit PNG baru.
- `ui_*.png` = skin widget (button, text_field, checkbox). `build-pack.sh` yang naruh ke path vanilla + bikin `.mcmeta` nine_slice.
  PENTING: skin ini GLOBAL, semua tombol/kolom teks Minecraft di client ikut berubah selama pack aktif.
  Gak mau global? hapus bagian "skin widget" di `build-pack.sh`.
- Tombol dialog dapat ikon custom (`hud_icons.png`, 7 ikon: trophy, users, flag, check, cross, play, back).
- Judul dialog `/panel` dapat ikon mahkota (`hud_logo.png`, glyph \uE006) kalau pack udah ke-load di pemain itu.
- Pemain mati di HUD = kepala diganti tengkorak (`hud_skull.png`), tiap slot punya frame (`hud_frame.png`).
- Default `hud.slot-width` jadi 11. Config lama di server tetap 10 (gak ketimpa), boleh dibiarin atau diganti manual.
- Batasan: latar/blur layar dialog dan layout dialog itu hardcode di client, gak bisa diubah resource pack.

## Kalau pack masih gak muncul, cek urut ini
1. `resource-pack.url` & `sha1` di config.yml udah diisi, repo public, link bisa dibuka di browser.
2. Pas join muncul prompt download pack. Kalau gak muncul = url kosong / salah.
3. Client wajib 1.21.9+ (kepala pakai object component). Server Paper 1.21.11.
4. SHA1 diupdate tiap pack berubah (lihat deskripsi Release "Latest build").

## Layar menang + tombol tes (Tahap 3c)
- Pemenang: title 4x = banner WINNER + frame emas + kepala pemenang di tengah; subtitle = mahkota, nama, jumlah kill.
  Dibangun di `WinScreen.java`. Aset: `hud_banner.png`, `hud_winframe.png`, `hud_kill.png` (glyph \uE008, \uE009, \uE007).
  Pemain tanpa pack dapat title teks biasa (MENANG! + nama + kill). Ada sound + 3 kembang api kecil. Layar tampil 6 detik, cleanup di 8 detik.
- Kill dihitung dari `recentKiller` (pukulan terakhir <10 detik sebelum mati). Jatuh ke void tanpa dipukul = gak ada yang dapat kill.
- MODE TES: `/panel` pas lagi main muncul tombol **Selesaikan Game (TES)** (cuma di mode tes). Klik = game langsung kelar, lu jadi pemenang.
  Kalau kill lu masih 0, diisi contoh 3 biar tampilan kelihatan.
- Ubah desain aset: edit `gen_assets.py`, jalanin `python3 gen_assets.py` (juga nulis ulang `hud.json`), commit PNG + hud.json.
  Tulisan WINNER dirender pakai font Liberation Sans Bold; kalau font itu gak ada di PC lu, otomatis pakai font pixel cadangan.
- Posisi banner/kepala di layar menang gak bisa dites di sini. Kalau kepala keliatan geser kiri/kanan, ubah `hud.head-advance` (8/9/10) di config.yml.

## Revisi v3
- Border sekarang masuk HUD custom (baris ke-4 panel: ikon + "BORDER 4:45", jadi merah "BORDER MENGECIL!" pas mengecil). Actionbar cuma dipakai pemain TANPA pack.
  Baris ini pakai font kedua `pof:hud_low` (`hud_low.json`) karena posisi vertikal glyph ditentukan per font.
- Bug spasi di font HUD diperbaiki (spasi sekarang advance 4 px), sebelumnya "NEXT ITEM" nempel dan bagian kanan geser.
- Posisi vertikal kepala = baseline-8..0, frame (HUD & winner) disesuaikan biar kepala pas di tengah frame.
- Kill dihapus dari layar menang, subtitle cuma mahkota + nama (simetris).
- Layar menang dikirim ke semua pemain match yang online (termasuk yang udah mati/menyerah). Tanpa pack = title teks biasa.
- File baru: `hud_low.json`, `hud_border.png`. Dihapus: `hud_kill.png`.

## Item pool v3 (ItemPool.java)
- Item yang sama baru boleh keluar lagi setelah minimal 4 item lain di antaranya (`GAP_BETWEEN = 4`), sesudah itu murni random.
- Blok ~42% dari drop. Jumlah per drop SEKARANG maksimal 1 (blok dan item), diatur `MAX_BLOCK` / `MAX_ITEM` = 1 di `ItemPool.java`. Semua item non-blok peluangnya mirip (sekitar 1.5-2.8% per drop), gak ada yang super langka.
- Maks 4 blok berturut-turut (`MAX_BLOCK_RUN`). Pengocok per game: `ItemPool.Roller`, dipanggil dari `PofGame.giveItems`.
- Ubah bobot / jumlah di bagian `static {}`: `item(material, min, max, bobot)`, `block(material, bobot)`.

## Ronde + Seri (Tahap 5)
Setting baru di `/panel` > Pillar of Fortune:
- **Jumlah ronde (1-10)**, default 1 = main sekali kayak sebelumnya. Juara = win terbanyak.
- **Izinkan SERI** (default mati): kalau di akhir ada pemain dengan win sama banyak: aktif => layar SERI (banner ungu, kepala yang seri, maks 5 kepala);
  mati => **BONUS MATCH** (ronde tambahan cuma antar pemain yang seri, yang menang jadi juara; kalau gak ada pemenang, bonus diulang).
- **Stop lebih awal kalau pemenang udah pasti** (default nyala): berhenti kalau win terbanyak > win kedua + sisa ronde. Matiin = semua ronde tetap dimainkan.

Alur: tiap ronde = world void baru (world ronde lama dihapus setelah semua pemain pindah). Jeda antar ronde ~5 detik: layar "ROUND WIN" (banner biru) + skor di chat.
Pemain yang mati di satu ronde ikut main lagi di ronde berikutnya. Pemain yang keluar server = keluar dari match (win-nya gak dihitung).
HUD baris ke-4 kanan: `R 2/3` (atau `BONUS`), hanya kalau ronde > 1.
Layar akhir: WINNER (juara), SERI, atau GAME SELESAI kalau gak ada yang pernah menang.
Tombol tes (mode tes): **Selesaikan Ronde/Game (TES)** dan **Lihat Layar SERI (TES)**.
File baru aset: `hud_banner_round.png`, `hud_banner_draw.png` (glyph \uE00B, \uE00C). `PofGame.java`: `startRound()`, `finish()`, `showRound/Champion/Draw/None()`.

## Deathswap (Tahap 6)
Game kedua di `/panel` > GAMES > Deathswap. Min 2, maks 10 pemain.
- Setting: waktu TP tukar posisi (min 10 detik), jumlah ronde (1-10), izinkan SERI, stop lebih awal kalau pemenang pasti. Aturan ronde/seri/bonus match sama persis dengan POF.
- World NORMAL (nama `pof_ds_*`, seed acak) dibikin SEKALI di ronde 1 dan dipakai terus sampai game kelar. Tiap ronde pemain cuma di-TP ke titik random baru di world yang sama. Pemain disebar acak melingkar (jarak ratusan block, hindari air/lava), countdown 5 detik gak bisa gerak, lalu mulai tanpa item (cari sendiri).
- Tiap interval TP: tiap pemain hidup pindah ke posisi pemain hidup lain yang dipilih acak (independen, jadi beberapa pemain bisa pindah ke tempat yang sama). 5 detik terakhir ada title peringatan.
- PvP langsung (pukulan, panah dari pemain) dibatalkan. Jebakan, lava, jatuh, ledakan dll tetap bisa membunuh. Mati = spectator di tempat, item jatuh.
- HUD: panel yang sama dengan POF, tapi teks atas jadi `SWAP TP m:ss` + bar kuning menuju TP berikutnya, baris border dihilangkan. Label ronde tetap di kanan.
- File baru: `DeathSwapGame.java`, `GameType.java` (daftar game + min/maks pemain), `Match.java` (interface yang dipakai `Game` dan `DeathSwapGame`).
- Perubahan di file lama: `GameManager` (pilih game sesuai tipe), `GameListener` (pakai `Match`), `Panels` (tombol Deathswap + halaman setting sendiri), `Hud.buildSwap`, `GameSettings` (type, swapIntervalSec), `Game` (cuma `implements Match`).
- Countdown 5..1, peringatan TP (5..1, angka merah + ikon tukar), MULAI!, TUKAR POSISI, dan TERELIMINASI sekarang custom (glyph resource pack, `CustomTitle.java`). Pemain tanpa pack tetap dapat title teks biasa. Countdown awal + MULAI! di POF ikut pakai aset yang sama.
- Aset baru: `hud_count.png` (\uE030-E034), `hud_tpwarn.png` (\uE035-E039), `hud_banner_go.png` (\uE03A), `hud_banner_swap.png` (\uE03B), `hud_banner_elim.png` (\uE03C). Dibuat di `gen_assets.py`. SHA1 pack berubah, update di config.yml.
- Mode tes Deathswap: cuma buat sendirian (1 pemain). Pas TP, pemain dipindah ke titik spawn lain (pura-pura ada lawan). Game baru kelar kalau lu mati / nyerah / klik Selesai (tes). Belum dites di server.


## Mace Roulette (MaceGame.java)
- World void, platform bulat radius 14 di Y=100. Pemain (min 4) ngelilingin tengah, hadap tengah, gak bisa gerak/lompat.
- Mace (ItemDisplay) muter di tengah, berhenti ngarah ke 1 pemain = pemegang mace.
- Pemegang dapet mace (Breach IV) + 1 wind charge. Countdown 3, lalu semua bebas gerak.
- Pemegang mace dapet timer (default 15 detik, diatur di `/panel` > Mace Roulette > "Timer mace", min 5s) buat bunuh pemain lain. Gak ada deteksi windcharge/pukulan lagi: percobaan jalan terus sampai timer abis (atau pemegang mace mati), lalu semua balik ke lingkaran + roulette lagi.
- Countdown 3 detik setelah roulette cuma nahan pemegang mace; pemain lain udah bebas gerak buat ngehindar.
- Damage cuma dari pukulan pemegang mace pas timer jalan (fall/ledakan dibatalin). Jatuh dari platform = mati (void).
- Terakhir hidup menang. Ronde/seri/skip sama kayak game lain.
- Mode tes: boleh sendiri, ada DUMMY (armor stand) buat dipukul. /panel nambah tombol Selesai (tes) & Seri (tes).
- Tuning: `POINT_OFFSET` (arah mace pas berhenti), `PLATFORM_R`, `ATTEMPT_SEC`, `READY_SEC` di MaceGame.java. BELUM DITES.

## Aturan pemain
- Semua game minimal 2 pemain, maksimal 10.
- MODE TES cuma buat main sendirian (1 pemain). Kalau ada pemain lain dicentang, lobby ditolak. Main bareng = mode biasa (terakhir hidup menang).

## Changelog update (v2-v7)
- v2: angka countdown + semua banner title diturunin (`ascent` 28 -> 12 di `hud.json` + `gen_assets.py`). Pack harus di-build ulang + SHA1 di config.yml diganti. Semua teks di panel/chat/title disingkat (contoh: info lobby `Item 1m | Border 5m | Sedang`, durasi ditulis `5m` / `30s`).
- v3: Deathswap mode tes solo (1 pemain), tombol Selesai (tes) & Seri (tes) di `/panel` cuma muncul di mode tes.
- v4: game ketiga Mace Roulette (lihat bagian di atas).
- v5: semua game minimal 2 pemain. MODE TES cuma buat 1 pemain; kalau ada pemain lain dicentang, lobby ditolak ("Mode tes cuma buat sendiri."). Main bareng = mode biasa, terakhir hidup menang.
- v6: world Deathswap dipakai terus antar ronde (lihat atas). Prefix chat semua game jadi `[MINIGAMES]` (sebelumnya `[POF]`, `[DEATHSWAP]`, `[MACE]`).
- v7: tiap drop item pool Pillar of Fortune maksimal 1 (item maupun blok).
- Semua perubahan v2-v7 BELUM pernah di-compile / dites di server. Kalau build Actions error, kirim lognya.
- Catatan build: kalau Actions error "cannot find symbol ... package pof" soal `Game.java` / `PillarPlugin.java`, hapus dua file lama itu dari root repo (sisa proyek lama).
- v9: Mace mode tes: deteksi "kena dummy" dibuat lebih kuat (semua ArmorStand + cadangan lewat ayunan tangan ke arah dummy setelah windcharge). Pukulan ke dummy / pemain -> percobaan kelar otomatis, balik ke lingkaran.
- v10: Mace disederhanain jadi sistem timer (lihat bagian Mace Roulette). Opsi timer ditambah di panel (`GameSettings.maceSec`). Dummy mode tes cuma jadi target (gak pecah, gak ngaruh ke timer).

## Revisi Pillar of Fortune (v11)
- Pemain 2-20. Pilar beneran: kolom bedrock dari lantai (Y=64) naik 20 block (`PILLAR_H`), pemain berdiri di atasnya. Jarak antar pilar 15 block (`PILLAR_SPACING`), border ikut gede (pilar terluar + 10, `BORDER_MARGIN`).
- Lantai di bawah pilar = map (`PofMap.java`), dipilih di `/panel` > Pillar of Fortune > "Map": Lava Rising, Slime, Magma, Grass, Void, Cobweb, TNT, Powder Snow, atau Random.
  Satu map dipilih = semua ronde pakai map itu. Random = tiap ronde map beda (gak sama dua kali berturut-turut). Nama map muncul di subtitle countdown dan info lobby.
- Lava Rising: lantai batu biasa; mulai pas waktu "Border mulai", lava naik 1 lapis tiap detik (maks sampai 4 block di atas pilar), cuma di dalam area border.
- Lantai BULAT, 1 lapis block aja (di bawahnya void): stone (lava), slime, magma, grass, cobweb, TNT (gak nyala sendiri), powder snow. Void = tanpa lantai. Border vanilla tetap kotak.
- HUD: kalau pemain lebih dari 13, baris kepala di panel dilewati (panel cuma muat 13), sisanya (teks, bar, jumlah hidup) tetap muncul.
- Sebab kematian di chat: void / lava-api / jatuh / magma / ledakan.
- Mode tes: jumlah pilar boleh 2-20.
- BELUM DITES. Floor besar (20 pemain = sekitar 140x140 block) dibangun pas ronde mulai, bisa agak lag sebentar.
- v13: lantai POF bulat + 1 lapis, jarak pilar 15, notif eliminasi POF pakai banner custom kayak game lain, arah mace roulette dibenerin (`POINT_OFFSET` 135), namespace resource pack jadi `zetminigames` (hud.json, hud_low.json, build-pack.sh, gen_assets.py, Hud.java). Prompt pack di config.yml jadi "Resource pack ZetMinigames" (config lama di server gak ketimpa, edit manual).

## Battle Royale (BattleRoyaleGame.java, BrLoot.java) - v14
Game keempat, package `id.zetminigames.battleroyale`. Solo saja (mode tim dibatalin), 8-48 pemain. BELUM DICOMPILE / DITES.
- Alur lobby: `/panel` -> GAMES -> Battle Royale -> langsung jadi host lobby. Panel host: daftar READY (n/48), tombol Start, Undang Pemain, Refresh, Batal.
  Yang diundang dapat [READY]/[TOLAK] di chat. Start cuma bisa kalau READY >= 8. Pemain yang belum READY pas Start gak ikut. Lobby kadaluarsa 10 menit.
  Pemain biasa yang tolak/keluar cuma keluar dari lobby (lobby gak batal); kalau host yang keluar/batal, lobby batal.
- World: world vanilla BARU tiap game (seed acak, nama `pof_br_*`, dihapus setelah game; sisa crash dihapus pas server nyala). Gak pakai map/template.
  Platform langit (lingkaran, quartz + cincin emas + pinggir blackstone) dibangun di tengah, di `sky.y`, radius `sky.radius`.
- Chest: dipasang otomatis tiap chunk baru ke-load. Grid sel 14 block + geser acak +-2, jadi jarak antar chest tetangga 10-18 block. Dipasang di permukaan tanah (air/lava dilewati),
  cuma di dalam area border awal, di luar platform langit. Isi dari `BrLoot.java` (bobot di `static {}`: `add(material, min, max, bobot)`, 3-6 item per chest).
  Loot = senjata, armor, bow, crossbow, arrow, ender pearl, wind charge, mace, shield, + cobblestone / oak planks buat masang block.
- Start: semua pemain di-tp ke platform langit, countdown 5 detik gak bisa gerak, lalu dapat Slow Falling. Slow Falling dicopot pas mendarat (di bawah `sky.y - 6`, nginjak block padat / air).
- Aturan world: mob gak bisa spawn sama sekali, siang terus (waktu dikunci 6000), hujan boleh, pesan achievement di chat dimatiin.
  Block bisa dipasang (dari loot) tapi gak bisa dihancurin (semua block).
- PvP mati `pvp-delay-seconds` (60) detik sejak countdown selesai. Mati / nyerah / keluar = item jatuh, jadi spectator. Terakhir hidup menang.
- Zona: border vanilla (kotak), titik tengah tetap per game, ukuran awal `zone.start-size` (1000) ke `zone.final-size` (20). Jumlah tahap diacak 8-12 (lihat v17).
  Jadwal diacak tiap game (waktu tunggu + lama mengecil), tapi tahap terakhir selalu selesai di `zone.total-seconds` (1200 = menit 20). Minimal tunggu/mengecil per tahap: `zone.min-wait-seconds` / `zone.min-shrink-seconds`.
- Config baru (config.yml server lama gak ketimpa, default sama kayak di bawah dan udah ada di kode):
  `battleroyale.center.x/z`, `sky.y` (tempat berdiri) + `sky.radius`, `zone.start-size/final-size/min-stages/max-stages/total-seconds/min-wait-seconds/min-shrink-seconds/damage`, `pvp-delay-seconds`.
- HUD (v15): bossbar custom per pemain (font `hud_top`/`hud`/`hud_low`): fase zona + timer, PVP, jumlah hidup, bar waktu, panah arah + jarak ke zona, kill. Tanpa pack = actionbar teks (ada panah unicode). Dinding zona = partikel merah di dekat pemain. Peringatan zona 30s/10s + hitung mundur 5..1, title "ZONA MENGECIL".
- File lama yang diubah (cuma nambah entri BR): `GameType`, `GameManager` (invite, startLobby, lobby host-driven), `Panels` (tombol + panel host/undang), `config.yml`.
- MODE TES (v15): `/panel` > Battle Royale > tombol **MODE TES: OFF/ON** di panel host. Cuma buat sendiri (1 pemain, undangan diblok). Game jalan normal, bedanya sisa 1 pemain TIDAK menang. `/panel` pas main muncul **Selesai (tes)** (cuma di mode tes) = game kelar, lu jadi pemenang. Game kelar sendiri kalau semua mati/nyerah. Tombol Seri (tes) gak dipakai di BR. Opsi percepat tes: `battleroyale.test.total-seconds` & `test.pvp-delay-seconds` di config.yml.
- Catatan: popup toast achievement di layar gak bisa dimatiin dari plugin (cuma pesan chat-nya). Titik tengah world acak bisa jatuh di laut.
- v14: tambah Battle Royale.

- v15: Battle Royale mode tes + HUD zona custom (aset baru: `hud_br.png`, `hud_arrows.png`, `hud_top.json`; `hud.json` & `hud_low.json` diperbarui). SHA1 pack berubah -> update `resource-pack.sha1` di config.yml. BELUM DICOMPILE / DITES.


## v17 (revisi POF + Battle Royale) - BELUM DICOMPILE / DITES
Perlu build ulang pack (ada ikon jam baru di `hud_br.png`): update `resource-pack.sha1` di config.yml lagi.

### Pillar of Fortune
- **Layout pilar** (`PillarLayout.java`, tanpa Bukkit, sudah dites terpisah): sampai 6 pemain = 1 cincin. 7+ pemain = cincin luar (maks 10 pilar) + sisanya di dalam (cincin kecil selang-seling, kadang 1 pilar di tengah),
  semua jarak >= 15 block, cincin dibikin sekecil mungkin. 20 pemain = 10 luar + 10 dalam, radius terluar sekitar 36 block (dulu 1 cincin penuh = 48). 8-11 pemain tetap 1 cincin (itu yang paling kecil).
  Posisi tiap pemain diacak tiap ronde (jadi gak selalu di luar / di dalam). Border = pilar terluar + 10, lantai bulat ikut.
- **Lava Rising**: lantai = danau lava dari awal (bedrock di bawahnya). Lava langsung naik begitu countdown kelar (naik pertama di detik ke-25), makin lama makin cepat & makin tinggi sekali naik:
  0-2 menit tiap 25s +1 block | 2-3 menit tiap 20s +2 | 3-5 menit tiap 15s +3 | 5 menit dst tiap 10s +5. Naik terus sampai batas atas world (`world.getMaxHeight()-1`, build up gak nyelametin). Tabel di `PofGame.lavaStep()`.
  Lapisan diisi dicicil beberapa tick (`LAVA_BLOCKS_PER_TICK`). Hitung mundur + jumlah block naik tampil di actionbar.
- **Siap-siap 15 detik**: pemain di-TP ke pilar, ditahan `READY_SEC` (10 detik, actionbar "BERSIAP") lalu countdown 5..1. Item dan lava baru jalan setelah countdown kelar.
- **Timer di HUD**: baris ke-4 kanan ada ikon jam + lama game berjalan (`m:ss`, mulai dari "MULAI!"). Label ronde (`R2/3`, tanpa spasi) geser ke kiri jam. Jarak kosong minimal antar elemen baris 4 = 19 px (dihitung buat kasus terburuk: R10/10, border 10:00, jam 99:59).
  Teks border pas mengecil jadi `MENGECIL!` (`KECIL!` kalau ada label ronde). Kalau ada label ronde, tulisan "BORDER" di kiri dihilangkan (ikon aja). Ikon jam = glyph `\uE044` (`hud_br.png`).

### Battle Royale
- **Titik tengah otomatis di daratan**: tiap game, plugin ngecek peta biome (tanpa generate chunk) buat sampai 40 titik acak dan pilih yang paling sedikit lautnya (berhenti kalau >= 92% darat).
  Hasilnya ditulis di log server. Mau pakai titik tetap: `battleroyale.center.auto: false` + `center.x/z`.
- **Zona awal 1000x1000** (`zone.start-size`). Config lama otomatis di-upgrade (`rev: 3`).
- **Jumlah tahap zona diacak 8-12** tiap game (`zone.min-stages` / `zone.max-stages`), ukuran tiap tahap juga acak (tetap mengecil), tahap terakhir PASTI 20x20 dan selesai tepat di menit 20 (`zone.total-seconds` 1200).
  Logika di `ZonePlan.java` (tanpa Bukkit, dites 20.000 jadwal acak). Jumlah tahap cuma muncul di log server.
- HUD zona sekarang cuma `ZONA m:ss` (menunggu) / `MENGECIL m:ss`, tanpa "ZONA 2/6".
- **Deathswap**: sama kayak POF, pemain ditahan `READY_SEC` (10 detik, actionbar "BERSIAP") di spawn sebelum countdown 5..1, total 15 detik. Timer swap baru jalan setelah "MULAI!".
- Siap-siap (POF + Deathswap) sekarang full custom: banner BERSIAP (`hud_banner_ready.png`, glyph \uE03D, tampil 10 detik) + HUD bossbar baris atas "MULAI 15s" dengan bar kuning menyusut. Bukan actionbar lagi. Pack berubah -> update sha1.
