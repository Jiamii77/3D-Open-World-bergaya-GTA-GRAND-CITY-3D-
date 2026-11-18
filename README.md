# Vice Roam — 3D Open-World Action Game

Game 3D Open-World Action-Adventure bergaya GTA untuk Android dengan mesin grafis OpenGL ES, siklus siang-malam dinamis, kendaraan, sistem wanted polisi 5-bintang, **Online Free Roam**, **Top 10 Leaderboard**, serta fitur ekspor APK langsung dari dalam aplikasi.

---

## Download Game (.APK)

Setiap push ke repositori ini otomatis membangun file APK melalui GitHub Actions (`.github/workflows/build-apk.yml`) dan melampirkannya langsung di halaman utama GitHub:

1. **Dari Panel Releases (Halaman Utama GitHub):**
   - Buka bagian **Releases** di sebelah kanan halaman utama repositori ini (klik **Latest** / **Vice Roam Latest APK**).
   - Unduh file lampiran **`ViceRoam-v1.0-debug.apk`**.
2. **Dari Tab Actions:**
   - Buka tab **Actions** → pilih workflow **Build Vice Roam APK** terbaru → unduh artifact **`ViceRoam-debug-apk`**.
3. **Pemasangan di Android:**
   - Buka file `.apk` yang sudah diunduh pada perangkat Android Anda dan izinkan pemasangan dari sumber tidak dikenal (*Install unknown apps*) jika diminta.

---

## Fitur Utama

- **Singleplayer & Open-World 3D**: Eksplorasi kota 3D dengan siklus siang-malam, kendaraan (Sedan, SUV, Moto, Police Cruiser), toko senjata, garasi, dan safe house.
- **Online Free Roam (Firebase Firestore + WebSocket)**: Bermain bersama 16–32 pemain dengan autentikasi Google Sign-In, safe zone spawn, dan quick chat/emote.
- **Top 10 Leaderboard**: Papan peringkat 10 skor pemain tertinggi secara real-time di Firestore.
- **Unduh APK & Pembaruan Sistem di Menu Utama**: Simpan salinan `.apk` terpasang ke folder **Downloads** perangkat (Storage Access Framework) atau buka tautan rilis terbaru di browser.

---

## Build Lokal (Android Studio / Gradle)

```bash
gradle :app:assembleDebug
```

File APK hasil build akan tersedia di `app/build/outputs/apk/debug/app-debug.apk`.
