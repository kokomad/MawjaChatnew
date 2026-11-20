package com.khader.mawjachat.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.khader.mawjachat.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val REQ_PERMISSIONS = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAndRequestPermissions()

        val prefs = getSharedPreferences("MawjaPrefs", MODE_PRIVATE)
        binding.etSenderName.setText(prefs.getString("last_sender", ""))
        binding.etReceiverName.setText(prefs.getString("last_receiver", ""))
        binding.etSharedCode.setText(prefs.getString("last_code", ""))

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnConnectRoom.setOnClickListener {
            val sender = binding.etSenderName.text.toString().trim()
            val receiver = binding.etReceiverName.text.toString().trim()
            val code = binding.etSharedCode.text.toString().trim()

            if (sender.isEmpty() || receiver.isEmpty() || code.isEmpty()) {
                Toast.makeText(this, "يرجى إدخال اسم المرسل واسم المستقبل والكود المشترك", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                checkAndRequestPermissions()
                Toast.makeText(this, "يرجى منح صلاحية الميكروفون أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("last_sender", sender)
                .putString("last_receiver", receiver)
                .putString("last_code", code)
                .apply()

            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("SENDER_NAME", sender)
                putExtra("RECEIVER_NAME", receiver)
                putExtra("SHARED_CODE", code)
            }
            startActivity(intent)
        }
    }

    private fun checkAndRequestPermissions() {
        val needed = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMISSIONS)
        }
    }
}