# Nevra Browser
HTML/CSS/JS murni + Capacitor. Tanpa bundler: plugin diakses via `window.Capacitor.Plugins`.

## Struktur
- `www/index.html`, `css/style.css` — UI
- `www/js/app.js` — state, tab, bookmark, riwayat, pengaturan, validasi URL
- `www/js/engine.js` — mesin browsing (native: InAppBrowser; web: iframe)
- `www/js/ads.js` — AdMob (ID TEST)
- `.github/workflows/build.yml` — build APK di cloud
- `assets/` — ikon (di-generate ke semua ukuran Android oleh workflow)

## Build APK tanpa Android Studio (GitHub Actions)
1. Buat repo GitHub (bisa private), push folder ini (`git init && git add . && git commit -m init && git push`).
2. Tab **Actions** → **Build APK** → Run workflow (otomatis juga tiap push ke `main`).
3. Setelah selesai, unduh artifact **nuvora-debug-apk** → `app-debug.apk` → install di HP.

## Preview cepat (Termux)
`cd www && python -m http.server 8080` lalu buka http://localhost:8080 (mode iframe: banyak situs memblokir iframe; di APK memakai WebView native).

## AdMob produksi
- Banner ID: ubah `ADMOB_BANNER_ID` di `www/js/ads.js`, hapus `isTesting:true`.
- App ID: simpan sebagai GitHub secret `ADMOB_APP_ID` (default: ID test Google).
- Jangan klik iklan sendiri saat produksi.

## Catatan penting
- Plugin `@capgo/inappbrowser` dipakai untuk membuka WebView penuh; toolbar navigasi (back/forward/reload) disediakan plugin. Versi/opsi plugin bisa berubah — jika build error, cek dokumentasi plugin dan sesuaikan `engine.js`.
- Skema selain http/https ditolak; data hanya di `localStorage` perangkat, tanpa server.
- Sebelum rilis Play Store: wajib kebijakan privasi (AdMob memakai ID iklan) dan keystore rilis.
