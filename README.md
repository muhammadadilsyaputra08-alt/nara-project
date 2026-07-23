# Nara — Android Widget Assistant (Scaffold)

Project Android Studio (Kotlin) untuk widget asisten suara "Nara", sesuai rancangan teknis:
Widget → AppWidgetProvider → AssistantService (foreground) → STT → ModelEngine (GGUF hasil fine-tuning
`intent_dataset_v3.jsonl`) → JsonValidator → ActionRouter → Executors → TTS → Widget update.

## Struktur folder (persis sesuai rancangan)
```
app/src/main/java/com/axel/nara/
 ├─ ui/
 │   ├─ widget/
 │   │   ├─ AssistantWidgetProvider.kt
 │   │   ├─ AssistantWidgetRemoteViews.kt
 │   │   └─ WidgetStateBinder.kt
 │   └─ activity/
 │       └─ SettingsActivity.kt
 ├─ service/
 │   ├─ AssistantService.kt
 │   ├─ SpeechService.kt
 │   ├─ TtsService.kt
 │   └─ ForegroundNotifier.kt
 ├─ model/
 │   ├─ ModelEngine.kt
 │   ├─ JsonSchema.kt
 │   └─ ModelInputMapper.kt
 ├─ router/
 │   ├─ ActionRouter.kt
 │   ├─ DeviceActionHandler.kt
 │   ├─ AppActionHandler.kt
 │   ├─ WebSearchHandler.kt
 │   └─ ChatFallbackHandler.kt
 ├─ state/
 │   ├─ SessionStore.kt
 │   ├─ UserPrefs.kt
 │   └─ ConnectivityState.kt
 └─ utils/
     ├─ JsonValidator.kt
     ├─ NetworkUtils.kt
     └─ TimeUtils.kt
```

## Cara membuka project
1. Buka folder ini di **Android Studio (Koala/2024.1+)** — pilih "Open" bukan "Import".
2. Android Studio akan otomatis membuat `gradle-wrapper.jar` saat sync pertama
   (file `gradle/wrapper/gradle-wrapper.properties` sudah disediakan, target Gradle 8.7).
3. Sync Gradle, lalu build ke device/emulator (min SDK 26 / Android 8.0).

## Yang SUDAH berfungsi (siap jalan)
- Widget compact & expanded mode dengan tombol mic/settings/toggle mode/retry.
- State machine penuh (`Idle → Greeting → Listening → Processing → Executing → Speaking / Error / Fallback`).
- STT & TTS native Android (`SpeechRecognizer`, `TextToSpeech`), sapaan berbasis waktu.
- `JsonValidator` (ekstraksi JSON dari output model + validasi skema + retry 1x).
- `ActionRouter` lengkap dengan aturan konektivitas (`connection_required`) dan seluruh handler kategori
  (`device_control`, `app_action`, `web_search`, `chat`), termasuk aksi nyata: volume, brightness,
  wifi/bluetooth (buka panel sistem sesuai batasan Android 10+), flashlight, buka app via
  `PackageManager`, pencarian YouTube/Google, navigasi Maps, dll.
- DataStore untuk preferensi (`expanded mode`, `prefer offline`).

## Build tanpa Android Studio (device kamu kurang kuat?)

Project ini sudah dilengkapi **GitHub Actions** (`.github/workflows/build-apk.yml`) yang meng-compile
APK sepenuhnya di server GitHub — device kamu cukup untuk push kode via git, tidak perlu jalankan
Android Studio/NDK/SDK sama sekali secara lokal.

**Cara pakai:**
1. Push folder project ini (termasuk submodule llama.cpp — lihat bagian "Setup inference GGUF") ke
   repo GitHub baru.
2. Buka tab **Actions** di repo GitHub kamu → workflow "Build APK" akan otomatis jalan setiap push ke
   branch `main` (atau klik **Run workflow** untuk trigger manual).
3. Setelah selesai (~5-10 menit, termasuk compile native llama.cpp), buka halaman run tersebut →
   bagian **Artifacts** → download `nara-assistant-debug-apk`.
4. Extract zip-nya, dapat file `app-debug.apk` → transfer ke HP (lewat kabel/cloud) → install manual
   (aktifkan "Install dari sumber tidak dikenal" di pengaturan Android).

> Catatan: file model `.gguf` (±1GB) **tidak** perlu di-push ke GitHub — itu tetap kamu push manual
> ke device lewat `adb push` setelah APK terinstall (lihat bagian "Setup inference GGUF" di atas).
> Repo publik/private punya limit ukuran file (100MB per file di Git biasa), jadi GGUF memang harus
> lewat jalur terpisah, bukan ikut di-commit ke repo.

**Kalau tetap ingin coba IDE ringan di Android (AIDE, dsb.):** kemungkinan besar akan gagal di modul
`:llama` karena AIDE tidak punya dukungan NDK/CMake multi-module yang stabil. Kalau device kamu benar-benar
tidak bisa push ke GitHub/pakai laptop orang lain sama sekali, opsi realistis lain: minta bantuan
komputer/warnet sekali untuk clone+push awal ke GitHub, setelahnya semua build berikutnya otomatis
lewat Actions tanpa perlu Android Studio lagi.

`GgufModelEngine` di `model/ModelEngine.kt` saat ini adalah **placeholder aman** — project selalu bisa
di-build & di-install tanpa binding native apa pun (asisten akan selalu menjawab `fallback_chat`
sampai kamu wire inference sungguhan). Ini disengaja: API resmi `examples/llama.android` di repo
llama.cpp beberapa kali berubah struktur/kelasnya antar versi (pernah `LLamaAndroid`, sekarang facade
`AiChat`/`InferenceEngine`), jadi menaruh dependency compile-time ke sana berisiko diam-diam gagal
begitu upstream berubah lagi.

**Cara integrasi yang aman (lakukan manual, jangan auto-generate dari asumsi API):**
1. `git submodule add https://github.com/ggml-org/llama.cpp third_party/llama.cpp`
2. Buka `third_party/llama.cpp/examples/llama.android` **sebagai project terpisah** di Android Studio
   (sesuai cara resmi di `docs/android.md` repo llama.cpp) — cek langsung kelas & method publik yang
   tersedia di versi yang kamu clone (jangan asumsikan dari dokumentasi lama).
3. Setelah tahu API-nya, isi `TODO` di `load()` dan `generate()` pada `GgufModelEngine` sesuai kelas
   yang benar-benar ada di versi kamu. Tambahkan modul itu ke `settings.gradle.kts` (`include(":llama")`
   + `project(":llama").projectDir = file(...)`) dan `implementation(project(":llama"))` di
   `app/build.gradle.kts` (baris ini sudah ada, tinggal di-uncomment).
4. Sync Gradle — build native library (`arm64-v8a`) butuh NDK terpasang (biasanya auto lewat SDK Manager).

**Alur model (bagian yang stabil, tidak berubah):**
1. Jalankan notebook `finetune_qwen_intent_router.ipynb` sampai selesai (adapter LoRA di Drive).
2. Merge adapter ke base model + convert ke `.gguf` + quantize `Q4_K_M`.
3. Push file `.gguf` ke device: `adb push nara-intent-qwen2.5-1.5b-q4_k_m.gguf /data/data/com.axel.nara/files/nara-intent-qwen2.5-1.5b.gguf`
   (nama file harus cocok dengan `MODEL_FILE_NAME` di `AssistantService.kt`, atau sesuaikan konstantanya).
4. Build & jalankan app — `AssistantService` akan `load()` model dari `filesDir` di sesi pertama.

**Catatan format prompt (berlaku untuk backend native apa pun yang kamu pasang):** karena model kita
base model (bukan chat/instruct), prompt harus dikirim **apa adanya** sesuai format training
(`"Ucapan: ...\nJSON: "`), TANPA chat template disisipkan — kalau API native yang kamu pasang otomatis
menambahkan chat template (banyak yang begitu secara default), cari opsi untuk mematikannya.

## Yang PERLU kamu lengkapi sebelum rilis
1. **Binding inference GGUF** — lihat panduan "Setup inference GGUF" di atas. Ini satu-satunya item
   yang sengaja belum diotomasi karena API native-nya masih berubah-ubah upstream.
2. **Screenshot terprogram & lock/unlock_screen** — butuh `MediaProjection` (izin runtime dari Activity)
   dan `DevicePolicyManager` (admin perangkat). Placeholder ada di `DeviceActionHandler.kt` dengan pesan
   `Result.Failure` yang jelas — wire sesuai kebutuhan keamanan app kamu.
3. **Notification panel / Quick Settings langsung** — Android modern membatasi ini untuk app pihak ketiga;
   scaffold memberi pesan yang jelas. Alternatif: `TileService` (untuk quick settings tile kustom) atau
   Accessibility Service (butuh persetujuan user eksplisit, pertimbangkan kebijakan Play Store).
4. **Kontak WhatsApp by name** — `AppActionHandler.openWhatsappChat()` saat ini hanya membuka app;
   tambahkan `ContactsContract` lookup jika mau langsung ke chat kontak tertentu.
5. **Jawaban chat bebas** (`ibu kota indonesia apa`, dll) — model intent-router ini HANYA menghasilkan JSON,
   bukan jawaban natural language. `ChatFallbackHandler` perlu dihubungkan ke LLM chat terpisah (cloud atau
   model kecil lain) untuk `target = fallback_chat` yang bersifat direct-answer.
6. Icon launcher (`ic_launcher_foreground.xml`) masih placeholder huruf "N" — ganti dengan desain final.

## Alur data end-to-end
```
Dataset (intent_dataset_v3.jsonl)
   → fine-tuning (finetune_qwen_intent_router.ipynb, Colab + Drive + HF)
   → adapter LoRA → merge + convert GGUF
   → taruh di device (filesDir/assets)
   → GgufModelEngine.load()/generate()
   → JsonValidator → ActionRouter → Executor → TTS → Widget
```

## Permission yang dipakai
Lihat `AndroidManifest.xml`: `RECORD_AUDIO`, `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`
(+ `FOREGROUND_SERVICE_MICROPHONE`), `POST_NOTIFICATIONS`, `CAMERA` (flashlight), `BLUETOOTH_CONNECT`,
`WRITE_SETTINGS` (brightness — butuh persetujuan manual user via halaman sistem), `ACCESS_WIFI_STATE`,
`CHANGE_WIFI_STATE`.
