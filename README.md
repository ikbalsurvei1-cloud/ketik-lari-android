# Lari Ketik: Bayangan Malam (Android Native Kotlin)

Game **2D Side-Scrolling Typing Runner** native Android (Kotlin + Custom Canvas View) bertema pelarian dari kejaran bayangan monster Urban Legend Indonesia.

## Fitur Permainan
1. **2D Side-Scrolling Runner**: Berlari di tengah malam melewati siluet pohon beringin, bambu, dan pagar tua di atas jembatan huruf.
2. **Dikejar Bayangan Urban Legend Indonesia**: Dikejar oleh *Bayangan Genderuwo*, *Siluet Kuntilanak*, *Kabut Banaspati*, dan *Sosok Wewe Gombel* yang berganti seiring jarak tempuh.
3. **Keyboard Virtual QWERTY & Kata Bahasa Indonesia**: Ketik kata utuh Bahasa Indonesia yang membentuk pijakan jalan tempat karakter berlari.
4. **Mekanik Salah Ketik (Typo) & Tersandung**:
   - Setiap salah ketik membuat karakter **melambat** dan memberi kesempatan bayangan monster mendekat.
   - Jika salah ketik **lebih dari 5 kali** (salah ketik ke-6), karakter akan **tersandung dan jatuh** (`TERSANDUNG!`), lalu langsung tertangkap oleh bayangan!

## Cara Build Menjadi File `.APK`

### Opsi 1: Menggunakan Android Studio (Paling Mudah)
1. Ekstrak file `LariKetik_AndroidProject.zip`.
2. Buka **Android Studio** -> pilih **Open** -> arahkan ke folder `LariKetikAndroid`.
3. Tunggu proses *Gradle Sync* selesai.
4. Klik menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. File `.apk` siap diinstal di `app/build/outputs/apk/debug/app-debug.apk`.

### Opsi 2: Build Otomatis Tanpa Instal Android Studio (via GitHub Actions)
Proyek ini sudah dilengkapi konfigurasi `.github/workflows/build-apk.yml`:
1. Buat repository baru di GitHub dan unggah isi folder proyek ini.
2. Buka tab **Actions** di GitHub repository kamu.
3. Workflow **Build Android APK** akan otomatis mengompilasi proyek dan menyediakan file `LariKetik-debug-apk` yang siap diunduh dan diinstal di HP Android.
