package com.axel.nara.ui.activity

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.axel.nara.R
import com.axel.nara.databinding.ActivityQuickInputBinding
import com.axel.nara.service.AssistantService

/**
 * Dibuka saat area "ketik" di widget pill ditekan (lihat AssistantWidgetRemoteViews).
 * Tampil sebagai bar input mengambang di atas homescreen (tema Theme.Nara.QuickInput),
 * meniru interaksi search bar Google saat diketuk — user ketik, kirim, activity ini
 * langsung menutup diri; hasilnya diproses AssistantService lewat ACTION_PROCESS_TEXT
 * (skip STT, langsung ke ModelEngine) sehingga alur sama persis dengan perintah suara,
 * cuma sumber teksnya beda.
 */
class QuickInputActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuickInputBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuickInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etQuickInput.requestFocus()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        binding.etQuickInput.setOnEditorActionListener { _, actionId, event ->
            val isSendAction = actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isSendAction) {
                submit()
                true
            } else {
                false
            }
        }

        binding.btnSend.setOnClickListener { submit() }
    }

    private fun submit() {
        val text = binding.etQuickInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            finish()
            return
        }

        val serviceIntent = android.content.Intent(this, AssistantService::class.java).apply {
            action = AssistantService.ACTION_PROCESS_TEXT
            putExtra(AssistantService.EXTRA_TEXT, text)
        }
        startForegroundService(serviceIntent)
        hideKeyboard()
        finish()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etQuickInput.windowToken, 0)
    }
}
