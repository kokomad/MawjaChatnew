package com.khader.mawjachat.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.khader.mawjachat.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("MawjaPrefs", MODE_PRIVATE)
        binding.etRelayUrl.setText(prefs.getString("relay_url", "wss://echo.websocket.events"))

        binding.tvDeveloperName.text = "khader abo salha"
        binding.tvDeveloperEmail.text = "mank.hader@gmail.com"

        binding.tvDeveloperEmail.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:mank.hader@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "تطبيق موجة شات - MawjaChat")
            }
            try {
                startActivity(emailIntent)
            } catch (_: Exception) {
                Toast.makeText(this, "mank.hader@gmail.com", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSaveSettings.setOnClickListener {
            val url = binding.etRelayUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                prefs.edit().putString("relay_url", url).apply()
                Toast.makeText(this, "تم حفظ الإعدادات بنجاح", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        binding.btnBack.setOnClickListener { finish() }
    }
}