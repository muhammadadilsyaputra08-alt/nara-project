package com.axel.nara.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Dilempar ModelEngine (bukan langsung exception native com.arm.aichat) supaya lapisan
 * pemanggil (AssistantService) punya pesan yang jelas & bisa ditangani tanpa meng-crash app.
 *
 * CATATAN AKAR MASALAH CRASH "UnsupportedArchitectureException":
 * com.arm.aichat.InferenceEngineImpl.loadModel() melempar UnsupportedArchitectureException
 * untuk SEMUA kegagalan native load() (return code != 0) — bukan cuma soal arsitektur CPU.
 * Native ai_chat.cpp: `if (!model) return 1;` lalu Kotlin: `if (it != 0) throw UnsupportedArchitectureException()`.
 * Nama exception itu MENYESATKAN: penyebab sebenarnya biasanya file .gguf korup/tidak lengkap
 * (hasil copy dari SettingsActivity terputus/salah file), quantization/arsitektur model yang
 * tidak didukung versi llama.cpp ini, atau RAM tidak cukup. Kita validasi magic header + ukuran
 * file duluan supaya pesan errornya akurat, DAN yang lebih penting: exception ini sebelumnya
 * tidak pernah ditangkap di AssistantService.processUtterance() sehingga selalu meng-crash
 * seluruh aplikasi (lihat perbaikan try/catch di AssistantService).
 */
sealed class ModelLoadException(message: String, val userMessage: String) : Exception(message) {
    class FileNotFound(path: String) : ModelLoadException(
        "File model tidak ditemukan: $path",
        userMessage = "File model belum ada. Silakan pilih file model di menu pengaturan."
    )
    class FileCorruptOrIncomplete(path: String) : ModelLoadException(
        "File model rusak/tidak lengkap (header GGUF tidak valid): $path",
        userMessage = "File model sepertinya rusak atau belum lengkap. Coba impor ulang file modelnya."
    )
    class NativeLoadFailed(cause: Throwable, fileSizeBytes: Long, nativeLogSnippet: String) : ModelLoadException(
        message = "Model gagal dimuat oleh inference engine (ukuran file: ${fileSizeBytes / (1024 * 1024)} MB). " +
            "Kemungkinan file .gguf korup/terpotong, arsitektur/kuantisasi model tidak didukung, " +
            "atau RAM tidak cukup.\n" +
            "--- Log native (llama.cpp) terkait ---\n$nativeLogSnippet",
        userMessage = "Model gagal dimuat (ukuran file: ${fileSizeBytes / (1024 * 1024)} MB). " +
            "Coba impor ulang file model — kemungkinan filenya belum lengkap."
    ) {
        init { initCause(cause) }
    }
}

/**
 * Kontrak inference model lokal. Diimplementasikan lewat backend GGUF pilihanmu.
 * ModelEngine TIDAK tahu apa pun soal routing/aksi — tugasnya murni: teks prompt masuk
 * -> teks JSON keluar (mentah, sebelum divalidasi oleh JsonValidator).
 */
interface ModelEngine {

    /** True jika model sudah dimuat ke memori dan siap dipakai untuk generate(). */
    suspend fun isReady(): Boolean

    /** Memuat model dari file GGUF di [modelPath]. Panggil sekali saat AssistantService dibuat. */
    suspend fun load(modelPath: String)

    /** Menjalankan satu kali inference. [prompt] sudah dalam format training (lihat ModelInputMapper). */
    suspend fun generate(prompt: String, maxNewTokens: Int = 256): String

    /** Melepas resource model (dipanggil saat AssistantService.onDestroy()). */
    suspend fun unload()
}

/**
 * Implementasi nyata pakai binding native llama.cpp Android (modul :llama, lihat
 * settings.gradle.kts + .github/workflows/build-apk.yml — di-clone dari
 * github.com/ggml-org/llama.cpp, tag b10076, folder examples/llama.android/lib).
 *
 * CATATAN PENTING UNTUK SESI/DEV SELANJUTNYA (riwayat koreksi):
 * Package `com.example.llama` SALAH TOTAL sejak awal — bukan regresi baru. Package yang benar
 * adalah `com.arm.aichat` (facade publik `AiChat`/`InferenceEngine`), dengan implementasi
 * internal di `com.arm.aichat.internal.InferenceEngineImpl` (ini juga yang muncul di warning
 * compiler soal parameter `systemPrompt`). Error "unresolved reference 'example'" sebenarnya
 * SUDAH ada di build-build sebelumnya juga, hanya kelewat saat filtering log karena compiler
 * Kotlin tidak selalu melaporkan ulang semua error turunan dari ekspresi yang tipenya sudah
 * error — jangan ulangi kesalahan itu, selalu grep "Unresolved reference" secara utuh.
 * Karena tipe penerima sebelumnya sudah error, klaim lama bahwa `load()` "terkonfirmasi benar"
 * TIDAK valid. Diganti ke `loadModel(modelPath: String)` sesuai riset independen dan dokumentasi
 * arsitektur ("load your selected model via its app-private file path" — path String, bukan File).
 * Package `com.arm.aichat` sekarang sudah BENAR (dikonfirmasi: build lolos sampai tahap
 * compileDebugKotlin tanpa error "unresolved reference 'aichat'" lagi). Method factory yang
 * benar adalah `AiChat.getInferenceEngine(context): InferenceEngine` (bukan `getInstance()`
 * ataupun tebakan `create()` sebelumnya) — dikonfirmasi dari beberapa sumber independen yang
 * menunjukkan potongan kode konkret, termasuk cuplikan `AiChat.kt` sendiri:
 * `fun getInferenceEngine(context: Context) = InferenceEngineImpl.getInstance(context)`.
 * `getInstance()` internal-nya ada di InferenceEngineImpl (private/internal), bukan di AiChat —
 * itulah kenapa `AiChat.getInstance()` gagal resolve sebelumnya.
 * Kalau `getInferenceEngine()` ternyata masih salah juga, buka langsung source aslinya:
 * external/llama.cpp/examples/llama.android/lib/src/main/java/com/arm/aichat/AiChat.kt
 */
class GgufModelEngine(private val appContext: Context) : ModelEngine {

    @Volatile private var modelLoaded = false
    private var engine: com.arm.aichat.InferenceEngine? = null

    // FIX crash "IllegalStateException: Cannot load model in ModelReady!" (ModelInference log):
    // AiChat.getInferenceEngine() mengembalikan SATU instance singleton native. Sebelumnya
    // isReady()-check lalu load() tidak atomik: dua pemanggil bisa lolos check bersamaan
    // (mis. widget di-tap 2x cepat -> 2 startService intent -> 2 coroutine processUtterance
    // berjalan berbarengan tanpa job sebelumnya dibatalkan, lihat fix di AssistantService),
    // keduanya lihat modelLoaded=false, keduanya panggil loadModel() ke singleton yang sama;
    // panggilan kedua mendarat saat native state sudah ModelReady -> exception ini, yang lolos
    // dari catch (UnsupportedArchitectureException) sehingga jatuh ke catch(Exception) generik
    // dan tercatat sebagai "ModelInference", bukan "ModelLoad". Mutex ini menyerialkan seluruh
    // akses load/generate/unload supaya check-then-act di atas jadi atomik.
    private val mutex = Mutex()

    override suspend fun isReady(): Boolean = modelLoaded

    override suspend fun load(modelPath: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (modelLoaded) return@withContext
            val file = File(modelPath)
            if (!file.exists()) throw ModelLoadException.FileNotFound(modelPath)
            if (!isValidGgufHeader(file)) throw ModelLoadException.FileCorruptOrIncomplete(modelPath)

            val instance = com.arm.aichat.AiChat.getInferenceEngine(appContext)
            try {
                instance.loadModel(modelPath)
            } catch (e: com.arm.aichat.UnsupportedArchitectureException) {
                // Nama exception ini menyesatkan — lihat catatan di atas ModelLoadException.
                // Ambil log native (llama.cpp, tag "ai-chat") supaya penyebab ASLI ikut tercatat.
                val nativeLog = com.axel.nara.utils.NativeLogCapture.captureRecentNativeLogs()
                throw ModelLoadException.NativeLoadFailed(e, file.length(), nativeLog)
            }
            engine = instance
            modelLoaded = true
        }
    }

    /** GGUF file dimulai dengan magic bytes "GGUF" (0x47 0x47 0x55 0x46). Cek cepat sebelum native load. */
    private fun isValidGgufHeader(file: File): Boolean {
        if (file.length() < 8) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(4)
                raf.readFully(header)
                header.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46)) // "GGUF"
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun generate(prompt: String, maxNewTokens: Int): String = mutex.withLock {
      withContext(Dispatchers.Default) {
        check(modelLoaded) { "Model belum dimuat. Panggil load() terlebih dahulu." }
        val activeEngine = engine ?: error("Engine null padahal modelLoaded true — state tidak konsisten.")

        // BUG SEBELUMNYA (v9): maxNewTokens diterima sebagai parameter tapi TIDAK PERNAH diteruskan
        // ke sendUserPrompt(), jadi selalu jatuh ke default library InferenceEngine.DEFAULT_PREDICT_LENGTH
        // = 1024 token. Sudah diperbaiki: predictLength diteruskan eksplisit.
        //
        // BUG BARU YANG MENYEBABKAN CRASH LOG "JsonInvalid" (isposable...isposable):
        // predictLength cuma BATAS ATAS, bukan syarat berhenti. Prompt training cuma satu giliran
        // ("Ucapan: X\nJSON: Y") tapi generate() sebelumnya tidak punya kondisi stop apa pun selain
        // token habis/EOS — begitu model gagal mengikuti format (mis. karena sisa konteks dari
        // utterance sebelumnya masih nempel di context native, lihat fix reset di AssistantService),
        // ia terus meracau: menghasilkan giliran "Ucapan:/JSON:" baru berulang-ulang lalu jatuh ke
        // pengulangan token identik ("isposable" x13). Tanpa syarat stop, seluruh predictLength
        // dibakar untuk sampah, dan JsonValidator akhirnya tidak menemukan '{' sama sekali.
        // Fix: (1) hentikan generate SEGERA setelah 1 objek JSON top-level pertama lengkap
        // (kurung kurawal balik ke depth 0), (2) deteksi loop token identik berturut-turut dan
        // hentikan generate lebih awal alih-alih menunggu predictLength penuh untuk output yang
        // sudah jelas rusak (hemat waktu & baterai, dan alasan invalid di CrashLogger jadi lebih
        // cepat didapat untuk debugging berikutnya).
        val builder = StringBuilder()
        var sawOpenBrace = false
        var braceDepth = 0
        var repeatStreak = 0
        var lastToken: String? = null

        activeEngine.sendUserPrompt(prompt, predictLength = maxNewTokens)
            .transformWhile { token ->
                builder.append(token)
                emit(token)

                for (c in token) {
                    when (c) {
                        '{' -> { sawOpenBrace = true; braceDepth++ }
                        '}' -> if (sawOpenBrace) braceDepth--
                    }
                }
                if (sawOpenBrace && braceDepth <= 0) return@transformWhile false

                if (token.isNotBlank() && token == lastToken) {
                    repeatStreak++
                    if (repeatStreak >= REPEAT_LOOP_THRESHOLD) return@transformWhile false
                } else {
                    repeatStreak = 0
                }
                lastToken = token

                true
            }
            .collect()

        builder.toString()
      }
    }

    override suspend fun unload() = mutex.withLock {
        withContext(Dispatchers.IO) {
            engine?.destroy()
            engine = null
            modelLoaded = false
        }
    }

    private companion object {
        /** Berapa kali token identik berturut-turut sebelum dianggap loop/degenerasi & generate dihentikan. */
        const val REPEAT_LOOP_THRESHOLD = 4
    }
}
