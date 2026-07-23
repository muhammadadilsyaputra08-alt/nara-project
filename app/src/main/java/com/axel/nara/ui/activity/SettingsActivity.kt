package com.axel.nara.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.axel.nara.databinding.ActivitySettingsBinding
import com.axel.nara.state.UserPrefs
import com.axel.nara.state.WidgetPrefs
import com.axel.nara.ui.widget.AssistantWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity pengaturan: toggle mode widget (compact/expanded), preferensi prioritas
 * offline, dan import model GGUF dari penyimpanan HP (tanpa perlu ADB).
 * Dibuka lewat tombol gear di widget expanded mode.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var userPrefs: UserPrefs

    private val pickModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importModelFile(uri)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true
        binding.tvPermissionStatus.text = if (micGranted) {
            "Izin mikrofon: diberikan ✓"
        } else {
            "Izin mikrofon: DITOLAK — tombol mic widget tidak akan berfungsi"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userPrefs = UserPrefs(applicationContext)
        checkAndRequestPermissions()

        // Mode widget expanded dibaca/ditulis lewat WidgetPrefs (SharedPreferences sinkron),
        // supaya konsisten dengan yang dibaca AssistantWidgetProvider (bukan DataStore lagi).
        binding.switchExpandedMode.isChecked = WidgetPrefs.isExpandedMode(applicationContext)
        binding.switchExpandedMode.setOnCheckedChangeListener { _, isChecked ->
            WidgetPrefs.setExpandedMode(applicationContext, isChecked)
            AssistantWidgetProvider.refreshAllWidgets(applicationContext)
        }

        lifecycleScope.launch {
            userPrefs.preferOffline.collect { value ->
                if (binding.switchPreferOffline.isChecked != value) {
                    binding.switchPreferOffline.isChecked = value
                }
            }
        }
        binding.switchPreferOffline.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { userPrefs.setPreferOffline(isChecked) }
        }

        binding.btnPickModel.setOnClickListener {
            pickModelLauncher.launch(arrayOf("*/*"))
        }

        refreshModelStatus()
    }

    private fun checkAndRequestPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        binding.tvPermissionStatus.text = if (micGranted) {
            "Izin mikrofon: diberikan ✓"
        } else {
            "Izin mikrofon: belum diberikan"
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun refreshModelStatus() {
        val modelFile = com.axel.nara.model.ModelStorage.modelFile(applicationContext)
        binding.tvModelStatus.text = if (modelFile.exists()) {
            val sizeMb = modelFile.length() / (1024 * 1024)
            "Status model: tersedia (${sizeMb} MB) — ${modelFile.absolutePath}"
        } else {
            "Status model: belum dimuat — pilih file .gguf di bawah"
        }
    }

    private fun importModelFile(uri: android.net.Uri) {
        binding.btnPickModel.isEnabled = false
        binding.progressModelCopy.visibility = android.view.View.VISIBLE
        binding.progressModelCopy.progress = 0
        binding.tvModelStatus.text = "Status model: menyalin..."

        lifecycleScope.launch {
            try {
                val destFile = com.axel.nara.model.ModelStorage.modelFile(applicationContext)
                val tempFile = com.axel.nara.model.ModelStorage.tempModelFile(applicationContext)

                withContext(Dispatchers.IO) {
                    val totalBytes = contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
                    contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(1 * 1024 * 1024)
                            var copied = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                copied += read
                                if (totalBytes > 0) {
                                    val percent = (copied * 100 / totalBytes).toInt()
                                    withContext(Dispatchers.Main) {
                                        binding.progressModelCopy.progress = percent
                                    }
                                }
                            }
                        }
                    } ?: throw IllegalStateException("Tidak bisa membuka file yang dipilih")

                    val header = ByteArray(4)
                    val isGguf = tempFile.length() >= 8 && java.io.RandomAccessFile(tempFile, "r").use {
                        it.readFully(header)
                        header.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46)) // "GGUF"
                    }
                    if (!isGguf) {
                        tempFile.delete()
                        throw IllegalArgumentException("File yang dipilih bukan GGUF valid (header tidak cocok)")
                    }

                    if (destFile.exists()) destFile.delete()
                    tempFile.renameTo(destFile)
                }

                binding.progressModelCopy.visibility = android.view.View.GONE
                binding.btnPickModel.isEnabled = true
                refreshModelStatus()
                Toast.makeText(this@SettingsActivity, "Model berhasil disalin", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                com.axel.nara.utils.CrashLogger.log(applicationContext, "ModelImport", e)
                binding.progressModelCopy.visibility = android.view.View.GONE
                binding.btnPickModel.isEnabled = true
                binding.tvModelStatus.text = "Status model: gagal menyalin (${e.message ?: "error"})"
                Toast.makeText(this@SettingsActivity, "Gagal menyalin model", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
